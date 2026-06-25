-- ADR-0038: 監査ログ ハッシュチェーン改ざん検知(テナント単位連鎖・自レコード HMAC)。
-- Sprint 3 の弱い hash_chain(前レコード SHA-256 / 再帰なし)を、真の連鎖
-- (hash_n = HMAC_SHA256(key, canonical(自行) ‖ hash_{n-1}))へ作り直す書込経路スキーマ。
--
-- プレ本番(本番データなし)のため後方互換移行は設けず、旧セマンティクスで記録された
-- 既存行は破棄する(ADR-0038 §6 実装メモ)。

-- 旧セマンティクスの行を破棄(新 NOT NULL 列 chain_seq / hash_key_id を満たせないため)。
TRUNCATE TABLE audit_logs;

-- 自レコードハッシュ化に必要な列を追加。
--   chain_seq   : 連鎖内の順序の正本。グローバル id 昇順は chain_key 横断で順序が崩れ、
--                 created_at(DATETIME 秒精度)は高負荷時に同秒衝突するため決定的順序を持つ。
--   hash_key_id : HMAC 鍵識別子(ローテーション対応。canonical(row) 入力にも含める)。
ALTER TABLE audit_logs
    ADD COLUMN chain_seq   BIGINT      NOT NULL COMMENT '連鎖内の順序の正本(chain_key 単位、1 始まり)' AFTER hash_chain,
    ADD COLUMN hash_key_id VARCHAR(32) NOT NULL COMMENT 'この行の HMAC 計算に用いた鍵の識別子' AFTER chain_seq,
    ADD KEY idx_al_chain (tenant_id, chain_seq);

-- hash_chain の意味を「前レコードの SHA-256」→「自レコードの HMAC(前ハッシュ込み)」へ変更。
-- 列名・型は維持し、コメントのみ改訂(ADR-0038 §3.5)。
ALTER TABLE audit_logs
    MODIFY COLUMN hash_chain CHAR(64) NOT NULL COMMENT '自レコードの HMAC-SHA256(canonical(自行) ‖ 前ハッシュ)。改ざん検知用(ADR-0038)';

-- 連鎖末尾の状態を chain_key 単位で保持し、行ロックで並行 INSERT を直列化する(ADR-0038 §3.4)。
-- chain_key = tenant_id、tenant_id IS NULL(プラットフォーム横断)は予約値 0。
-- 行は初回書込時に upsert で生成するため seed しない。
CREATE TABLE chain_heads (
    chain_key  BIGINT     NOT NULL COMMENT 'tenant_id、横断は予約値 0',
    last_hash  CHAR(64)   NOT NULL COMMENT 'この連鎖の末尾行ハッシュ',
    last_seq   BIGINT     NOT NULL COMMENT 'この連鎖の最大 chain_seq',
    updated_at DATETIME   NOT NULL COMMENT '末尾更新日時',
    PRIMARY KEY (chain_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
