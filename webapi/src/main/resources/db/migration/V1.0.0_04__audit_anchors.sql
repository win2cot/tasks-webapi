-- ADR-0038 §3.6: 監査ハッシュチェーンの検証チェックポイント(アンカー)。
-- B-05 検証バッチが各 chain_key の検証成功後に連鎖頭を追記専用で固定する。
-- audit_anchors は B-03(1 年保管削除)の対象外とし prune しない。保管削除後も
-- retained アンカーを起点に連鎖検証を継続できる。
CREATE TABLE audit_anchors (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    chain_key         BIGINT      NOT NULL COMMENT 'tenant_id、横断は予約値 0',
    seq_at_checkpoint BIGINT      NOT NULL COMMENT 'この時点の chain_seq(連鎖頭)',
    head_hash         CHAR(64)    NOT NULL COMMENT 'この時点の連鎖頭ハッシュ',
    hash_key_id       VARCHAR(32) NOT NULL COMMENT 'チェックポイント作成時の現行鍵 ID',
    created_at        DATETIME    NOT NULL COMMENT 'チェックポイント作成日時',
    PRIMARY KEY (id),
    KEY idx_anchor_chain (chain_key, seq_at_checkpoint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
