-- SPI 検証用の users テーブル(本番 webapi の V1.0.0_01__create_tables.sql の users 定義と一致)。
-- SPI は users のみを read/write するため、FK 元の業務テーブルは作らない。
-- (ADR-0006 §3.2 / §3.4。version / deleted_at を含む)
CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '変更不可',
    oidc_sub        VARCHAR(255) NOT NULL COMMENT 'Keycloak Subject。匿名化時は __deleted__{id} に置換',
    email           VARCHAR(255) NOT NULL COMMENT 'メールアドレス。匿名化時は __deleted__{id}@deleted.invalid に置換',
    full_name       VARCHAR(255) NOT NULL COMMENT '氏名。匿名化時は __deleted__ に置換',
    full_name_kana  VARCHAR(255) NOT NULL COMMENT '氏名カナ。匿名化時は __deleted__ に置換',
    department_name VARCHAR(255) NULL     COMMENT '部署名。匿名化時は NULL に置換',
    status          ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' COMMENT 'アカウント状態(ACTIVE=有効, INACTIVE=無効化)',
    version         BIGINT       NOT NULL DEFAULT 0 COMMENT 'JPA @Version 楽観排他用(ADR-0006 §3.4)',
    deleted_at      DATETIME     NULL     COMMENT '論理削除日時。NULL=有効、NOT NULL=匿名化済み(ADR-0006 §3.4)',
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_oidc_sub (oidc_sub),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
