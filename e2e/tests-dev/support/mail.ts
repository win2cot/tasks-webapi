import { randomUUID } from 'node:crypto';
import { GetObjectCommand, ListObjectsV2Command, S3Client } from '@aws-sdk/client-s3';
import { simpleParser } from 'mailparser';

/**
 * dev E2E のメール受信ヘルパ(ADR-0041 / #843)。
 *
 * `e2e.dgz48.xyz` 宛メールは SES email receiving で S3(`platform-dev-e2e-mail` の `inbound/`)へ
 * 生 MIME として格納される。テストは一意アドレスでフローを起動し、本ヘルパで S3 をポーリングして
 * 該当メールを取得・パースし、リンク/トークンを抽出する。サインアップ確認 / 招待 / パスワードリセット /
 * 通知など全メールフローで共用する。認証情報は既定の AWS 資格情報チェーン(ローカル SSO / CI は OIDC ロール)。
 */
const REGION = process.env.AWS_REGION ?? 'ap-northeast-1';
const BUCKET = process.env.DEV_SMOKE_MAIL_BUCKET ?? 'platform-dev-e2e-mail';
const PREFIX = process.env.DEV_SMOKE_MAIL_PREFIX ?? 'inbound/';
const MAIL_DOMAIN = process.env.DEV_SMOKE_MAIL_DOMAIN ?? 'e2e.dgz48.xyz';

const s3 = new S3Client({ region: REGION });

export interface ReceivedMail {
  key: string;
  subject: string;
  to: string;
  text: string;
  html: string;
  /** 本文(text + html)から抽出した http(s) URL 一覧。 */
  links: string[];
}

/** テストごとに一意な受信アドレスを作る(例: `signup-<uuid>@e2e.dgz48.xyz`)。 */
export function uniqueRecipient(localPrefix: string): string {
  return `${localPrefix}-${randomUUID()}@${MAIL_DOMAIN}`;
}

async function streamToString(body: unknown): Promise<string> {
  // @aws-sdk の Body は Node の Readable。transformToString があれば使う。
  const b = body as { transformToString?: () => Promise<string> };
  if (b?.transformToString) {
    return b.transformToString();
  }
  const chunks: Buffer[] = [];
  for await (const chunk of body as AsyncIterable<Buffer>) {
    chunks.push(Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString('utf-8');
}

function extractLinks(text: string, html: string): string[] {
  const found = new Set<string>();
  const urlRe = /https?:\/\/[^\s"'<>)\]]+/g;
  for (const src of [text, html]) {
    for (const m of src.matchAll(urlRe)) {
      found.add(m[0]);
    }
  }
  return [...found];
}

/**
 * 指定した受信アドレス宛のメールが S3 に届くまでポーリングして取得する。届いたメールの To を照合するため、
 * 一意アドレス(`uniqueRecipient`)の利用を前提とする。
 */
export async function waitForEmailTo(
  recipient: string,
  opts: { timeoutMs?: number; pollMs?: number } = {},
): Promise<ReceivedMail> {
  const timeoutMs = opts.timeoutMs ?? 90_000;
  const pollMs = opts.pollMs ?? 5_000;
  const target = recipient.toLowerCase();
  const deadline = Date.now() + timeoutMs;
  const seen = new Set<string>();

  while (Date.now() < deadline) {
    const listed = await s3.send(new ListObjectsV2Command({ Bucket: BUCKET, Prefix: PREFIX }));
    const objects = (listed.Contents ?? [])
      .filter((o) => o.Key && !seen.has(o.Key))
      .sort((a, b) => (b.LastModified?.getTime() ?? 0) - (a.LastModified?.getTime() ?? 0));

    for (const obj of objects) {
      const key = obj.Key as string;
      seen.add(key);
      const got = await s3.send(new GetObjectCommand({ Bucket: BUCKET, Key: key }));
      const raw = await streamToString(got.Body);
      const parsed = await simpleParser(raw);
      const toText = (parsed.to && !Array.isArray(parsed.to) ? parsed.to.text : '') ?? '';
      if (!toText.toLowerCase().includes(target)) {
        continue;
      }
      const text = parsed.text ?? '';
      const html = typeof parsed.html === 'string' ? parsed.html : '';
      return {
        key,
        subject: parsed.subject ?? '',
        to: toText,
        text,
        html,
        links: extractLinks(text, html),
      };
    }
    await new Promise((r) => setTimeout(r, pollMs));
  }
  throw new Error(`メールが ${timeoutMs}ms 以内に届きませんでした: to=${recipient}`);
}
