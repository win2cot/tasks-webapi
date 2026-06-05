-- SaaS Admin の正本テーブル。tenant_id を持たない例外テーブル(ADR-0010: Hibernate Filter 適用外)。
-- JWT.sub (oidc_sub) と突合してセッション開始時に ROLE_APP_ADMIN を付与する。
CREATE TABLE app_admin_users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    oidc_sub   VARCHAR(255) NOT NULL COMMENT 'Keycloak Subject (JWT sub claim)',
    created_at DATETIME     NOT NULL COMMENT '登録日時',
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_admin_users_oidc_sub (oidc_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
