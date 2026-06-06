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

CREATE TABLE tenants (
    id         BIGINT                                     NOT NULL AUTO_INCREMENT,
    code       VARCHAR(50)                                NOT NULL COMMENT 'テナント識別コード',
    name       VARCHAR(255)                               NOT NULL COMMENT 'テナント名',
    plan       ENUM('FREE','STANDARD','ENTERPRISE')       NOT NULL DEFAULT 'STANDARD',
    status     ENUM('ACTIVE','SUSPENDED','DELETED')       NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME                                   NOT NULL COMMENT '作成日時',
    updated_at DATETIME                                   NOT NULL COMMENT '更新日時',
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenants_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_tenants (
    user_id   BIGINT                                 NOT NULL,
    tenant_id BIGINT                                 NOT NULL,
    role      ENUM('TENANT_ADMIN','MEMBER')          NOT NULL COMMENT '参照のみのVIEWERは設けない(要件定義書 §2.3)',
    status    ENUM('ACTIVE','INVITED','DISABLED')    NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME                               NOT NULL COMMENT '参加日時',
    PRIMARY KEY (user_id, tenant_id),
    KEY idx_ut_tenant (tenant_id, user_id),
    CONSTRAINT fk_ut_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_ut_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id           BIGINT                                              NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT                                              NOT NULL COMMENT 'テナント分離キー',
    title        VARCHAR(100)                                        NOT NULL COMMENT 'タイトル',
    description  TEXT                                                NULL     COMMENT '説明(2000文字以内)',
    status       ENUM('NOT_STARTED','IN_PROGRESS','DONE','ON_HOLD')  NOT NULL COMMENT '未着手/進行中/完了/保留',
    priority     ENUM('HIGH','MEDIUM','LOW')                         NOT NULL,
    visibility   ENUM('TENANT','STAKEHOLDERS','PRIVATE')             NOT NULL DEFAULT 'TENANT',
    owner_id     BIGINT                                              NOT NULL COMMENT '所有者。編集・削除権限の根拠',
    assignee_id  BIGINT                                              NULL     COMMENT '担当者(任意)',
    due_date     DATE                                                NOT NULL COMMENT '期限日',
    completed_at DATETIME                                            NULL     COMMENT '完了日時',
    deleted_at   DATETIME                                            NULL     COMMENT '論理削除日時(NULL=有効)',
    created_at   DATETIME                                            NOT NULL COMMENT '作成日時',
    updated_at   DATETIME                                            NOT NULL COMMENT '更新日時',
    created_by   BIGINT                                              NOT NULL COMMENT '作成ユーザーID(users.id)',
    updated_by   BIGINT                                              NOT NULL COMMENT '更新ユーザーID(users.id)',
    PRIMARY KEY (id),
    KEY idx_tasks_tenant_due        (tenant_id, due_date),
    KEY idx_tasks_tenant_owner_due  (tenant_id, owner_id, due_date),
    KEY idx_tasks_tenant_assignee   (tenant_id, assignee_id, due_date),
    KEY idx_tasks_tenant_status_due   (tenant_id, status, due_date),
    KEY idx_tasks_tenant_visibility   (tenant_id, visibility),
    KEY idx_tasks_tenant_deleted      (tenant_id, deleted_at),
    KEY idx_tasks_tenant_completed    (tenant_id, completed_at),
    CONSTRAINT fk_tasks_tenant      FOREIGN KEY (tenant_id)   REFERENCES tenants(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_owner       FOREIGN KEY (owner_id)    REFERENCES users(id)    ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_assignee    FOREIGN KEY (assignee_id) REFERENCES users(id)    ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_created_by  FOREIGN KEY (created_by)  REFERENCES users(id)    ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_updated_by  FOREIGN KEY (updated_by)  REFERENCES users(id)    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_stakeholders (
    task_id   BIGINT   NOT NULL,
    user_id   BIGINT   NOT NULL,
    tenant_id BIGINT   NOT NULL COMMENT 'テナント分離(検索高速化)',
    added_by  BIGINT   NOT NULL COMMENT '追加した所有者(監査用)',
    added_at  DATETIME NOT NULL COMMENT '追加日時',
    PRIMARY KEY (task_id, user_id),
    KEY idx_ts_user_tenant (user_id, tenant_id),
    CONSTRAINT fk_ts_task     FOREIGN KEY (task_id)   REFERENCES tasks(id)   ON DELETE CASCADE,
    CONSTRAINT fk_ts_user     FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_ts_tenant   FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ts_added_by FOREIGN KEY (added_by)  REFERENCES users(id)   ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- tenant_id / user_id は FK 制約を意図的に省略している。
-- テナント・ユーザー削除後も監査ログを完全に保持するためである(監査ログの不変性確保)。
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

CREATE TABLE user_notification_settings (
    user_id           BIGINT   NOT NULL,
    tenant_id         BIGINT   NOT NULL,
    email_due_today   BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '期限当日メール通知(F-18)',
    email_overdue     BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '期限超過メール通知',
    email_stakeholder BOOLEAN  NOT NULL DEFAULT TRUE COMMENT '関係者として登録された時の通知',
    updated_at        DATETIME NOT NULL COMMENT '更新日時',
    PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT fk_uns_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_uns_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL COMMENT 'ロック名(バッチごとに一意)',
    lock_until TIMESTAMP(3) NOT NULL COMMENT 'ロック解放予定時刻',
    locked_at  TIMESTAMP(3) NOT NULL COMMENT 'ロック取得時刻',
    locked_by  VARCHAR(255) NOT NULL COMMENT 'ロック取得インスタンス識別子',
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
