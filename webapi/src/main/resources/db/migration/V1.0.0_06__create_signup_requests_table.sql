-- Flow B セルフサインアップ(double opt-in、ADR-0040 §3.3 / §3.4)。
-- 公開エンドポイントで email を受け取り、自前 one-time トークン(招待と同型)を発行して SES で確認リンクを送る。
-- 確認リンクの受信そのものが email 到達証明。complete でトークンを消費し会員登録(users 行 + Keycloak credential)を行う。
-- テナント未所属の登録のみを担うため tenant_id / role / invited_by は持たない(招待 invitations との差分)。
-- 平文トークンはメールにのみ載せ、DB には SHA-256 ハッシュ(64 hex)のみ保存する。

CREATE TABLE signup_requests (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL COMMENT 'サインアップ先メールアドレス',
    token_hash  CHAR(64)     NOT NULL COMMENT '確認トークンの SHA-256 ハッシュ(16進64文字)。平文は非保存',
    status      ENUM('PENDING','USED','REVOKED') NOT NULL DEFAULT 'PENDING'
                COMMENT 'サインアップ要求状態。complete 成功で USED、再要求で旧 PENDING は REVOKED',
    expires_at  DATETIME     NOT NULL COMMENT '有効期限(発行から 24 時間)',
    created_at  DATETIME     NOT NULL COMMENT '発行日時',
    consumed_at DATETIME     NULL     COMMENT 'complete(消費)日時。未消費は NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uq_signup_requests_token_hash (token_hash),
    KEY idx_signup_requests_email (email),
    KEY idx_signup_requests_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
