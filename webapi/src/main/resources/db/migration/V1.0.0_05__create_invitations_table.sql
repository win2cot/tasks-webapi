-- A-09 招待フロー(ADR-0017 選択肢 B: 自前招待トークン)。
-- Tenant Admin が email を指定して招待 → invitations 行を作成し SES で送信。
-- トークンは平文をメールにのみ載せ、DB には SHA-256 ハッシュ(64 hex)のみ保存する(ADR-0017 §3.1)。
-- 受諾(token 消費)/ user_tenants 紐付けは受諾画面フロー(別 Issue)で実装する。

CREATE TABLE invitations (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL COMMENT '招待先テナント(業務テーブル: Hibernate tenantFilter 対象)',
    email       VARCHAR(255) NOT NULL COMMENT '招待先メールアドレス',
    token_hash  CHAR(64)     NOT NULL COMMENT '招待トークンの SHA-256 ハッシュ(16進64文字)。平文は非保存',
    status      ENUM('PENDING','USED','REVOKED') NOT NULL DEFAULT 'PENDING'
                COMMENT '招待状態。受諾成功で USED、再送/取消で REVOKED(ADR-0017 §3.1)',
    role        ENUM('TENANT_ADMIN','MEMBER')    NOT NULL COMMENT '受諾時に付与するロール',
    expires_at  DATETIME     NOT NULL COMMENT '有効期限(発行から 7 日。ADR-0017 §3.1)',
    invited_by  BIGINT       NOT NULL COMMENT '招待した Tenant Admin の users.id',
    created_at  DATETIME     NOT NULL COMMENT '発行日時',
    consumed_at DATETIME     NULL     COMMENT '受諾(消費)日時。未消費は NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_invitations_token_hash (token_hash),
    KEY idx_invitations_tenant_email (tenant_id, email),
    KEY idx_invitations_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
