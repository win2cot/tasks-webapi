# ADR-0038: 監査ログ ハッシュチェーンによる改ざん検知(テナント単位連鎖・自レコード HMAC・検証バッチ)

- **Status**: Accepted
- **Date**: 2026-06-25
- **Deciders**: win2cot
- **Tags**: audit, security, multi-tenancy, persistence

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [論点A: 連鎖単位](#論点a-連鎖単位)
  - [論点B: ハッシュ構造](#論点b-ハッシュ構造)
  - [論点C: ハッシュ関数](#論点c-ハッシュ関数)
  - [論点D: 並行 INSERT の直列化](#論点d-並行-insert-の直列化)
  - [論点E: 保管削除(B-03)との両立](#論点e-保管削除b-03との両立)
  - [論点F: 検証経路の置き場所と ADR 要否](#論点f-検証経路の置き場所と-adr-要否)
- [3. 決定(Decision)](#3-決定decision)
  - [3.1 連鎖単位](#31-連鎖単位)
  - [3.2 ハッシュ構造](#32-ハッシュ構造)
  - [3.3 ハッシュ関数](#33-ハッシュ関数)
  - [3.4 直列化(chain_heads)](#34-直列化chain_heads)
  - [3.5 スキーマ変更](#35-スキーマ変更)
  - [3.6 保管削除との両立(audit_anchors)](#36-保管削除との両立audit_anchors)
  - [3.7 検証(B-05 バッチ)](#37-検証b-05-バッチ)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

Issue #751(開発計画書 §4.3.1 Sprint 4 App)で、`audit_logs` の改ざん検知(tamper-evidence)をハッシュチェーンとして完成させる。監査ログの記録基盤(差分計算 = ADR-0013、テナント帰属 = ADR-0020、語彙追加・記録配線 = #734 / #736)は Sprint 3 までに landing 済であり、本 ADR は **完全性(改ざん検知)の方式** を確定する。

Sprint 3 で landing した既存実装(`AuditLogPersistenceAdapter.computeChainHash`)を精査した結果、これは **真のハッシュチェーンになっていない**ことが判明した。新規行の `hash_chain` 列に `SHA256(前行.id | 前行.detail | 前行.createdAt)` を格納しているだけで、以下の欠陥がある。

1. **再帰(前ハッシュを入力に含めること)がない。** 入力に `前行.hash_chain` が含まれないため、連鎖が伝播しない。任意の行を改ざんしても、攻撃者は隣接する次行の `hash_chain` を 1 個だけ再計算すれば検証を通せる。1 か所の改変が末尾まで波及するという連鎖の本質が成立していない。
2. **保護対象の列が 3 列のみ。** `tenant_id` / `user_id` / `action` / `entity_type` / `entity_id` / `ip_address` がハッシュ入力外で、「誰が」「何を」を書き換えても検出できない。
3. **格納セマンティクスが「前行のハッシュ」**であり、末尾(最新)行の内容はどのハッシュにも保護されない。整合性検証バッチ(基本設計書 §8.1 B-05)のコードも未実装である。

この弱い定義は基本設計書 §6.8・設計規約 §4.3 に `audit_logs.hash_chain は前レコードの id + detail + created_at の SHA-256` として明文化されている。したがって #751 は実装コードと仕様ドキュメントの双方を改訂する必要がある。

加えて、本 ADR では連鎖を真に機能させるために以下の未決事項を統一的に決定する。`audit_logs` は Hibernate Filter 非適用(ADR-0010 §6.1)・`tenant_id` nullable(`NULL` = システム横断 / SaaS Admin 操作)であり、連鎖単位の選択がマルチテナント整合・保管削除・並行性に直結する。

関連: ADR-0004(`tenants` 監査列例外・`actor_sub` 方針)/ ADR-0009(JST 全層統一)/ ADR-0010(Hibernate Filter・§6.1 Filter 除外)/ ADR-0013(監査ログ差分粒度)/ ADR-0020(SaaS Admin 操作の `tenant_id` 帰属・read 監査)/ ADR-0037(スケジュールバッチ ShedLock + SES)/ 基本設計書 §4.2.6 / §6.8 / §8.1 / NIST SP 800-53 AU-9(監査情報の保護)/ AU-10(否認防止)。

## 2. 検討した選択肢(Options Considered)

### 論点A: 連鎖単位

- **A-1: グローバル単一連鎖**
  - 概要: 全 `audit_logs` 行を 1 本の連鎖に並べる。
  - 利点: 検証経路が 1 本で単純。実装が最小。
  - 欠点: テナント解約の物理削除(#167)と 1 年保管削除(B-03)が連鎖の途中行を削除し、ジェネシス(連鎖の起点ハッシュ、固定値)からの検証を恒久的に破壊する。全 INSERT が単一末尾で直列化し、書込スループットのボトルネックになる。`tenant_id IS NULL`(プラットフォーム横断)行とテナント行が混線する。
  - リスク・未知数: なし(欠点が決定的)。
- **A-2: テナント単位連鎖 + プラットフォーム連鎖(採用)**
  - 概要: `chain_key` 単位で独立した連鎖を持つ。テナント行は `chain_key = tenant_id`、`tenant_id IS NULL` 行は予約センチネル(後述)で 1 本のプラットフォーム連鎖にまとめる。
  - 利点: テナント解約・保管削除を `chain_key` 単位で完結でき、他テナントの連鎖を破壊しない(cascade 削除は `tenant_id` 単位の単一 DELETE、ADR-0013 / #167 と整合)。ADR-0020 の「`tenant_id` = 操作対象テナント」帰属とも整合し、Tenant Admin が自テナントの連鎖を自己完結的に検証できる。書込の直列化が `chain_key` 単位に分散する。
  - 欠点: `chain_key` ごとにジェネシス・末尾管理が要り、検証はチェーン本数分のループになる。`tenant_id IS NULL` 用のセンチネル運用が要る。
  - リスク・未知数: なし。

### 論点B: ハッシュ構造

- **B-1: 現行「前レコードの 3 列ハッシュ」を踏襲し列を拡張**
  - 概要: 「前行のハッシュを格納」する現行セマンティクスを維持しつつ、ハッシュ入力に欠けている列(`action` / `user_id` / `tenant_id` 等)を追加する。
  - 利点: 既存コードからの変更が小さい。
  - 欠点: コンテキスト §1 の欠陥 1(再帰なし)が残る。前ハッシュを入力に含めないため伝播せず、隣接 1 行の再計算で偽造でき、tamper-evidence として成立しない。末尾行が無保護のままになる。
  - リスク・未知数: なし(欠点が決定的)。
- **B-2: 自レコードハッシュに作り直す(採用)**
  - 概要: 各行に「自行の全不変列 + 直前行ハッシュ」のハッシュ(= 自行のハッシュ)を格納する。`hash_n = H( 正準化(自行の不変列) ‖ hash_{n-1} )`。
  - 利点: 標準的なハッシュチェーンとなり、任意の行の改変・削除・並べ替えが末尾まで波及して検出できる。全不変列を入力に含めるため「誰が何を」の改変も検出できる。末尾行も自身のハッシュで保護される。
  - 欠点: 列セマンティクスを「前行ハッシュ」→「自行ハッシュ」に変更するため、仕様ドキュメントとコードの改訂が要る。
  - リスク・未知数: `detail` JSON の正準化(キー順・空白)が決定的でないと検証が崩れる(ADR-0013 の `ORDER_MAP_ENTRIES_BY_KEYS` 規定で対応済)。

### 論点C: ハッシュ関数

- **C-1: 素の SHA-256**
  - 概要: 鍵なしの SHA-256 で連鎖を計算する。
  - 利点: 鍵管理が不要で実装が最小。
  - 欠点: tamper-evidence が成立するのは「監査ログを読めるが書けない」脅威モデルに限られる。DB に書き込める攻撃者は全行と後述アンカーを再計算して連鎖を偽造でき、改ざんが検出されない。
  - リスク・未知数: なし。
- **C-2: HMAC-SHA256(アプリ保持鍵)(採用)**
  - 概要: アプリが保持する鍵(Parameter Store / KMS、DB 外)で HMAC-SHA256 を計算する。
  - 利点: DB 単独で侵害された攻撃者は鍵を持たないため連鎖を偽造できない。NIST AU-9 / AU-10 の監査情報保護・否認防止の観点で上位。秘匿情報を Parameter Store に一本化する既存方針(設計規約 §5.4)とも整合。
  - 欠点: 鍵のローテーション運用と、どの鍵で計算されたかを示す key-id の管理が要る。
  - リスク・未知数: Native Image(ADR-0008)下での起動時鍵ロードに問題がないことの確認が要る(`javax.crypto.Mac` は標準で reflection 不要の見込み)。

### 論点D: 並行 INSERT の直列化

真の連鎖では、同時 INSERT 2 件が同じ末尾を読むと連鎖が分岐するため、`chain_key` 単位で直列化が必須となる。

- **D-1: `chain_heads` テーブル + 行ロック(採用)**
  - 概要: `chain_key` ごとに末尾状態を保持する小テーブルを持ち、audit INSERT トランザクション内で当該行を `SELECT ... FOR UPDATE` する。
  - 利点: 直列化と末尾 O(1) 取得を同時に満たし、現行の全表スキャン `findFirstByOrderByIdDesc` を廃止できる。アンカー(チェックポイント)の格納先も兼ねられる。
  - 欠点: 監査を伴う write ごとに `chain_key` 行のロック競合が発生する(ただし `chain_key` 単位なので競合範囲は限定的)。
  - リスク・未知数: なし。
- **D-2: MySQL named lock(`GET_LOCK`)**
  - 概要: `chain_key` ごとにアプリ側で名前付きロックを取得する。
  - 利点: テーブル追加が不要。
  - 欠点: 末尾取得は別途必要で、ロックのコネクション結合・タイムアウト運用の癖がある。トランザクション境界とロック解放の整合に注意が要る。
- **D-3: 単一ライタ**
  - 概要: 監査 INSERT を単一経路に集約して直列化する。
  - 利点: DB 変更が不要。
  - 欠点: スループット制約。ECS 複数インスタンス構成では成立させにくい。

### 論点E: 保管削除(B-03)との両立

- **E-1: 定期チェックポイント(アンカー)方式(採用)**
  - 概要: 連鎖頭ハッシュを追記専用のアンカー表へ日次で固定し、アンカー表自体は prune しない。検証は保管窓内の最新チェックポイントを起点に行う。
  - 利点: B-03 が保管期間超の行を削除しても、最新チェックポイント以降の連鎖が検証可能なまま保たれる。削除済み行は元々参照できないので実害がない。
  - 欠点: アンカー表の運用と、検証起点の選択ロジックが要る。
- **E-2: 保管窓内のみ検証**
  - 概要: 削除で起点が切れることを許容し、検証範囲を保管期間内に限定する。
  - 利点: アンカー表が不要で実装最小。
  - 欠点: チェックポイントの固定がないため、保管窓の境界付近で「削除されたのか改ざんされたのか」を区別する根拠が弱い。
- **E-3: prune 時に再ジェネシス**
  - 概要: 削除後、最古の生存行を新たな起点として記録する。
  - 利点: アンカー表を持たない。
  - 欠点: B-03 バッチに連鎖再起点処理を組み込む結合が生じ、削除と完全性管理が密結合になる。

### 論点F: 検証経路の置き場所と ADR 要否

検証経路(B-05)は基本設計書 §8.1 に「監査ログ整合性検証 / 毎日 02:00 / ハッシュチェーンの整合性を検証」として既載であり、ShedLock による排他(ADR-0037)も方針確定済。したがって **置き場所そのものに新規 ADR は不要**で、本 ADR は連鎖方式(論点 A〜E)を正式決定する。連鎖方式の確定は設計規約 §4.3 の改訂を伴う新規アーキパターンの導入であり、repo の ADR 運用(新規パターン / 規約改訂は ADR を立てる)に従って本 ADR を起こす。なお hash_chain の方式は ADR-0013 の決定事項ではなく Context での既存仕様引用にとどまるため、本 ADR は ADR-0013 を Supersede せず相互参照に留める。

## 3. 決定(Decision)

論点A=**A-2**、論点B=**B-2**、論点C=**C-2**、論点D=**D-1**、論点E=**E-1** を採用する。検証経路は B-05 日次バッチ(基本設計書 §8.1)を踏襲する。

### 3.1 連鎖単位

`chain_key` 単位の独立連鎖とする。

- テナントに帰属する行: `chain_key = tenant_id`
- `tenant_id IS NULL`(プラットフォーム横断 / 単一対象を持たない SaaS Admin 操作。ADR-0020 §3.1): 予約センチネル `chain_key = 0` で 1 本のプラットフォーム連鎖にまとめる。`tenants.id` は正の AUTO_INCREMENT のため `0` と衝突しない。
- 各 `chain_key` の最初の行は直前ハッシュをジェネシスハッシュ `GENESIS = "0" × 64` として計算する。

ADR-0020 により「特定テナントを対象とする SaaS Admin 操作」は `tenant_id = 対象テナント` で記録されるため、それらは当該テナントの連鎖に自然に載る。

### 3.2 ハッシュ構造

各行に自行のハッシュを格納する。

```text
hash_n = HMAC_SHA256( key , canonical(row_n) ‖ hash_{n-1} )
canonical(row) = chain_key | chain_seq | user_id | action | entity_type | entity_id | detail | ip_address | created_at | hash_key_id
hash_0(per chain) = GENESIS = "0" × 64
```

- `canonical(row)` は上記の全不変列を決定的に直列化したもの。`NULL` 列は固定のセンチネル文字列(例: 空文字ではなく ` ` 区切り + 明示 `null` トークン)で表現し、値の混同を避ける。
- `detail` は ADR-0013 の規定(Jackson `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS`)で正準化済の JSON 文字列を用いる。
- `created_at` は ADR-0009 の JST 形式で直列化する。
- グローバル AUTO_INCREMENT の `id` は連鎖の順序にも入力にも使わない(テナント横断で採番が交錯するため)。順序の正本は `chain_seq`(3.5)とする。

### 3.3 ハッシュ関数

HMAC-SHA256 を用いる。

- 鍵はアプリが保持し、DB 外(Parameter Store / KMS)で管理する(設計規約 §5.4 の秘匿情報一本化方針に従う)。
- 鍵のローテーションに備え、各行に `hash_key_id`(どの鍵で計算したかの識別子)を保持し、`canonical(row)` の入力にも含める。検証時は `hash_key_id` で鍵を解決する。ローテーション後の新規行は新 key-id を用い、既存行は元の key-id で検証する。連鎖そのものは鍵に依存せず `hash_{n-1}` でリンクするため、ローテーションは連鎖を切らない。

### 3.4 直列化(chain_heads)

`chain_heads` テーブルで `chain_key` 単位の末尾を管理し、行ロックで直列化する。

```sql
CREATE TABLE chain_heads (
    chain_key  BIGINT     NOT NULL COMMENT 'tenant_id、横断は予約値 0',
    last_hash  CHAR(64)   NOT NULL COMMENT 'この連鎖の末尾行ハッシュ',
    last_seq   BIGINT     NOT NULL COMMENT 'この連鎖の最大 chain_seq',
    updated_at DATETIME   NOT NULL,
    PRIMARY KEY (chain_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

監査 INSERT は同一トランザクション内で次の順に行う:

1. 当該 `chain_key` の `chain_heads` 行を `SELECT ... FOR UPDATE`(無ければジェネシス相当として新規作成)
2. `chain_seq = last_seq + 1` を採番
3. `hash_n` を計算(3.2)
4. `audit_logs` に INSERT
5. `chain_heads` の `last_hash` / `last_seq` / `updated_at` を更新

現行の全表スキャン `findFirstByOrderByIdDesc` は廃止する。

### 3.5 スキーマ変更

`audit_logs` に Flyway マイグレーション(命名規約準拠)で次を行う:

- `chain_seq BIGINT NOT NULL` を追加(連鎖内の順序の正本)。グローバル `id` 昇順では `chain_key` 横断で順序が崩れ、`created_at` は `DATETIME`(秒精度)で高負荷時に同秒衝突するため、決定的順序を `chain_seq` で持つ。
- `hash_key_id`(HMAC 鍵識別子、短い VARCHAR)を追加。
- 既存 `hash_chain CHAR(64)` の意味を「前レコードの SHA-256」→「自レコードの HMAC(前ハッシュ込み)」に変更する。列名は維持し、列コメントと仕様を改訂する。
- `chain_heads` テーブルおよび `audit_anchors` テーブル(3.6)を新設する。

### 3.6 保管削除との両立(audit_anchors)

追記専用のアンカー表で連鎖頭を定期固定する。

```sql
CREATE TABLE audit_anchors (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    chain_key          BIGINT   NOT NULL,
    seq_at_checkpoint  BIGINT   NOT NULL COMMENT 'この時点の chain_seq',
    head_hash          CHAR(64) NOT NULL COMMENT 'この時点の連鎖頭ハッシュ',
    hash_key_id        VARCHAR(32) NOT NULL,
    created_at         DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_anchor_chain (chain_key, seq_at_checkpoint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`audit_anchors` は B-03(1 年保管削除)の対象外とし、prune しない。B-05 検証成功後に各 `chain_key` の頭を新たなチェックポイントとして追記する。検証は「保管窓内の最古チェックポイント」を起点に末尾まで再計算する。これにより B-03 が保管期間超の行を削除しても連鎖検証は切れない。

### 3.7 検証(B-05 バッチ)

- 毎日 02:00 にバッチ(ShedLock 排他、ADR-0037 / 基本設計書 §8.1 B-05)を実行する。
- `chain_key` ごとに最新チェックポイントを起点として末尾まで HMAC を再計算し、各行の `hash_chain` および `chain_heads.last_hash` と突合する。
- 不整合は **検出専用(fail-open)** とし、監査ログ書込はブロックしない(改ざん検知は事後検出であり、可用性を犠牲にしない)。不整合検知時は通知バッチ経由でアラートを発し、構造化ログ(ADR-0019)に記録する。
- 検証成功後に新チェックポイントを `audit_anchors` へ追記する。
- オンデマンドの検証 API(Tenant Admin / SaaS Admin 向け)は MVP スコープ外とし、Phase 2 で別途検討する。

## 4. 理由(Rationale)

- 既存実装は再帰なし・3 列のみ・前行ハッシュ格納で tamper-evidence が成立していない。自レコードハッシュ + 全不変列 + 前ハッシュ込み(B-2)が、改ざん・削除・並べ替えを末尾まで波及検出する唯一の素直な形である。
- `audit_logs` は `tenant_id` nullable で Filter 非適用(ADR-0010 §6.1)であり、テナント解約・保管削除が物理削除を伴う。テナント単位連鎖(A-2)はこれらを `chain_key` 単位に閉じ込め、グローバル連鎖が抱える「中間削除で全検証が破壊される」問題と「単一末尾の直列化ボトルネック」を回避する。ADR-0020 のテナント帰属とも一貫する。
- HMAC(C-2)は「DB を書ける攻撃者」を脅威モデルに含められる唯一の選択で、コスト差はほぼない。監査ログという性質上、内部不正・DB 侵害を想定するのが妥当(NIST AU-9)。
- `chain_heads` 行ロック(D-1)は直列化・末尾 O(1) 取得・アンカー格納を 1 つの仕組みで満たし、現行の全表スキャンも解消する。
- アンカー方式(E-1)は B-03 削除と B-05 検証を疎結合に保ち、削除バッチに連鎖ロジックを混ぜ込まない(E-3 の密結合を回避)。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 監査ログが NIST AU-9 / AU-10 を満たす実効的な改ざん検知を備える。DB 侵害単独では連鎖を偽造できない。
- テナント単位連鎖により、テナント解約・GDPR cascade 削除(#167)が連鎖を破壊しない。Tenant Admin 向け監査参照(A-22)の検証も自テナント内で完結する。
- 末尾取得が `chain_heads` で O(1) になり、現行の全表スキャンが解消される。
- 改ざん検知が fail-open のため、検証バッチの障害が業務 write を止めない。

### 悪い影響・制約(Negative)

- 監査を伴う write ごとに `chain_key` 行ロックが入り、同一テナントの高頻度更新で軽微な競合が生じうる(ADR-0029 の性能測定で観測する)。
- HMAC 鍵のローテーション運用・key-id 管理という運用負荷が増える。
- `chain_seq` / `hash_key_id` 列と `chain_heads` / `audit_anchors` テーブルの追加で、スキーマと検証ロジックの複雑度が上がる。
- `canonical(row)` の直列化規約が決定的でないと検証が崩れるため、規約の明文化とレビュー運用が要る。

### 既存ドキュメント・規約への波及

- `docs/specs/設計規約.md` §4.3: `hash_chain` の記述「前レコードの id + detail + created_at の SHA-256」を、自レコード HMAC-SHA256(`canonical(row) ‖ 前ハッシュ`)・テナント単位連鎖・`chain_seq` 順序・アンカー方式に改訂。
- `docs/specs/基本設計書.md` §6.8: 同上の改ざん検知記述を改訂。§4.2.6 `audit_logs` テーブル定義に `chain_seq` / `hash_key_id` を追記し `chain_heads` / `audit_anchors` を追加。§8.1 B-05 の説明をチェックポイント起点検証に更新。
- 実装コード: `AuditLogJpaEntity`(列追加)/ `AuditLogPersistenceAdapter.computeChainHash`(自レコード HMAC へ全面改修)/ `AuditLogJpaRepository`(`findFirstByOrderByIdDesc` 廃止、`chain_heads` リポジトリ追加)。
- ADR-0013: 影響は相互参照のみ(diff 戦略の決定は不変)。`detail` 正準化(`ORDER_MAP_ENTRIES_BY_KEYS`)が本 ADR の前提として継続。
- ADR-0010 §6.1: `audit_logs` が Filter 非適用である前提を本 ADR が利用する旨を補足参照(変更なし)。

## 6. 実装メモ(Implementation Notes)

着手順序(派生 Issue / PR、本 ADR PR とは別):

1. 本 ADR(Accepted)+ 設計規約 §4.3 / 基本設計書 §6.8・§4.2.6・§8.1 改訂(同一 PR)。
2. Flyway マイグレーション: `audit_logs` への `chain_seq` / `hash_key_id` 追加、`chain_heads` / `audit_anchors` 新設(命名規約準拠)。
3. 連鎖計算の改修: `AuditLogPersistenceAdapter` を自レコード HMAC + `chain_heads` 行ロックへ。`computeChainHash` を全面置換、`findFirstByOrderByIdDesc` 廃止。HMAC 鍵を Parameter Store からロードする infra 配線。
4. B-05 検証バッチ + アンカー追記(ShedLock、ADR-0037 / `NotificationBatchScheduler` 同様の構成)。不整合アラートは通知経路 + 構造化ログ。
5. テスト: `chain_key` 別連鎖の連結 IT、改ざん注入(行改変 / 削除 / 並べ替え / 末尾改変)で検出する IT、保管削除後にチェックポイント起点検証が通る IT、並行 INSERT で連鎖が分岐しない IT。カバレッジ 80% 以上(CI ゲート)。

開発環境の既存行: 本プロジェクトは実装着手前(プレ本番)で本番データが無いため、旧セマンティクスで記録された dev シードは再シードまたはマイグレーション内での再計算で扱い、後方互換の移行は設けない。

鍵運用: 初期は単一 key-id(例 `v1`)で開始し、ローテーション手順は runbook(Sprint 4 サブ Issue)で別途整備する。Native Image(ADR-0008)下での `javax.crypto.Mac` 初期化を実ビルドで検証する。

## 7. 参考リンク(References)

- Issue #751(本 ADR の起票元 / 実装 Issue)
- Issue #734 / #736(Sprint 3 監査ログ語彙追加・記録配線)
- Issue #167(テナント解約 Phase 2、cascade 削除)
- ADR-0004(`tenants` 監査列例外・`actor_sub`)
- ADR-0008(GraalVM Native Image)
- ADR-0009(JST 全層統一)
- ADR-0010(Hibernate Filter・§6.1 Filter 除外テーブル)
- ADR-0013(監査ログ差分粒度・`detail` 正準化)
- ADR-0019(構造化ログ)
- ADR-0020(SaaS Admin 操作の `tenant_id` 帰属・read 監査)
- ADR-0029(性能測定・診断)
- ADR-0037(スケジュールバッチ ShedLock + SES)
- 基本設計書 §4.2.6 / §6.8 / §8.1
- 設計規約 §4.3 / §5.4
- NIST SP 800-53 AU-9(監査情報の保護)/ AU-10(否認防止)/ AU-11(監査記録の保持)
