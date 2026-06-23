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

-- ANONYMIZE 監査記録(ADR-0006 §3.4 step8 / #734)の検証用。本番では webapi の
-- V1.0.0_01__create_tables.sql が作成するが、SPI テスト DB は users のみのためここで定義を複製する。
CREATE TABLE audit_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT      NULL     COMMENT 'テナント分離(NULL=システム横断)。FK省略: テナント削除後も監査ログを保持',
    user_id     BIGINT      NULL     COMMENT '操作ユーザー(認証失敗時はNULL)。FK省略: ユーザー削除後も監査ログを保持',
    action      VARCHAR(50) NOT NULL COMMENT 'LOGIN/CREATE/UPDATE/DELETE 等',
    entity_type VARCHAR(50) NULL     COMMENT '対象エンティティ種別',
    entity_id   BIGINT      NULL     COMMENT '対象ID',
    detail      JSON        NULL     COMMENT '変更内容(差分)',
    ip_address  VARCHAR(45) NULL     COMMENT 'IPv4/IPv6',
    hash_chain  CHAR(64)    NOT NULL COMMENT '前レコードのSHA-256(改ざん検知用)',
    created_at  DATETIME    NOT NULL COMMENT '発生日時',
    PRIMARY KEY (id),
    KEY idx_al_tenant_user_created (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
