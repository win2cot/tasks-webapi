CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    oidc_sub        VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    full_name_kana  VARCHAR(255) NOT NULL,
    department_name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_oidc_sub (oidc_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenants (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    plan       VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenants_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_tenants (
    user_id   BIGINT      NOT NULL,
    tenant_id BIGINT      NOT NULL,
    role      VARCHAR(20) NOT NULL,
    status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME    NOT NULL,
    PRIMARY KEY (user_id, tenant_id),
    KEY idx_ut_tenant (tenant_id, user_id),
    CONSTRAINT fk_ut_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_ut_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT       NOT NULL,
    title        VARCHAR(100) NOT NULL,
    description  TEXT         NULL,
    status       VARCHAR(20)  NOT NULL,
    priority     VARCHAR(10)  NOT NULL,
    visibility   VARCHAR(20)  NOT NULL DEFAULT 'TENANT',
    owner_id     BIGINT       NOT NULL,
    assignee_id  BIGINT       NULL,
    due_date     DATE         NOT NULL,
    completed_at DATETIME     NULL,
    deleted_at   DATETIME     NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tasks_tenant_due        (tenant_id, due_date),
    KEY idx_tasks_tenant_owner_due  (tenant_id, owner_id, due_date),
    KEY idx_tasks_tenant_assignee   (tenant_id, assignee_id, due_date),
    KEY idx_tasks_tenant_status_due (tenant_id, status, due_date),
    KEY idx_tasks_tenant_visibility (tenant_id, visibility),
    KEY idx_tasks_tenant_deleted    (tenant_id, deleted_at),
    CONSTRAINT fk_tasks_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_owner  FOREIGN KEY (owner_id)  REFERENCES users(id)   ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_stakeholders (
    task_id   BIGINT   NOT NULL,
    user_id   BIGINT   NOT NULL,
    tenant_id BIGINT   NOT NULL,
    added_by  BIGINT   NOT NULL,
    added_at  DATETIME NOT NULL,
    PRIMARY KEY (task_id, user_id),
    KEY idx_ts_user_tenant (user_id, tenant_id),
    CONSTRAINT fk_ts_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_ts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT      NULL,
    user_id     BIGINT      NULL,
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NULL,
    entity_id   BIGINT      NULL,
    detail      JSON        NULL,
    ip_address  VARCHAR(45) NULL,
    hash_chain  CHAR(64)    NOT NULL,
    created_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_al_tenant_user_created (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_notification_settings (
    user_id           BIGINT   NOT NULL,
    tenant_id         BIGINT   NOT NULL,
    email_due_today   BOOLEAN  NOT NULL DEFAULT TRUE,
    email_overdue     BOOLEAN  NOT NULL DEFAULT TRUE,
    email_stakeholder BOOLEAN  NOT NULL DEFAULT TRUE,
    updated_at        DATETIME NOT NULL,
    PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT fk_uns_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_uns_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
