/**
 * web/serve.mjs — 静的配信サーバ(local / CI 用)
 *
 * #529 CloudFront Response Headers Policy 相当のセキュリティヘッダを
 * security-headers.json から読み込み全レスポンスに付与する。
 * connect-src の origin は環境変数で注入する。HSTS は http localhost では付けない。
 *
 * 使い方: npm run serve
 * 環境変数:
 *   PORT             — 待受ポート(デフォルト: 5500)
 *   LOCAL_API_ORIGIN — webapi の origin(デフォルト: http://localhost:8080)
 *   LOCAL_AUTH_ORIGIN — Keycloak の origin(デフォルト: http://localhost:18080)
 */

import { readFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('.', import.meta.url));
const PORT = parseInt(process.env.PORT ?? '5500', 10);
const api = process.env.LOCAL_API_ORIGIN ?? 'http://localhost:8080';
const auth = process.env.LOCAL_AUTH_ORIGIN ?? 'http://localhost:18080';

const config = JSON.parse(
  await readFile(new URL('./security-headers.json', import.meta.url), 'utf8'),
);

const cspValue = Object.entries(config.csp.directives)
  .map(
    ([k, vals]) =>
      `${k} ${vals
        .map((v) => (v === '__API_ORIGIN__' ? api : v === '__AUTH_ORIGIN__' ? auth : v))
        .join(' ')}`,
  )
  .join('; ');

const cspHeader = config.csp.reportOnly
  ? 'Content-Security-Policy-Report-Only'
  : 'Content-Security-Policy';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
};

function applySecurityHeaders(res) {
  res.setHeader(cspHeader, cspValue);
  for (const [name, value] of Object.entries(config.headers)) {
    res.setHeader(name, value);
  }
  // HSTS は http://localhost では付けない(http では無視され localhost を pin したくない)
}

function notFound(res) {
  applySecurityHeaders(res);
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('Not Found');
}

http
  .createServer(async (req, res) => {
    if (req.url?.startsWith('/node_modules/')) {
      return notFound(res);
    }

    let urlPath = (req.url ?? '/').split('?')[0];
    if (urlPath === '' || urlPath === '/') urlPath = '/index.html';

    const filePath = resolve(join(ROOT, urlPath));
    if (!filePath.startsWith(ROOT)) {
      return notFound(res);
    }

    try {
      const info = await stat(filePath);
      if (info.isDirectory()) {
        return notFound(res);
      }
      const body = await readFile(filePath);
      const mime = MIME[extname(filePath)] ?? 'application/octet-stream';
      applySecurityHeaders(res);
      res.writeHead(200, { 'Content-Type': mime });
      res.end(body);
    } catch {
      notFound(res);
    }
  })
  .listen(PORT, () => {
    console.log(`web  ready → http://localhost:${PORT}`);
    console.log(`  ${cspHeader}`);
    console.log(`    connect-src … ${api} ${auth}`);
  });
