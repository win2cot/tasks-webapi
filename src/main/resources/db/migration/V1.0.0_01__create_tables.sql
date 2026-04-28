CREATE TABLE users (
    id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    oidc_sub       VARCHAR(255)     NOT NULL,
    email          VARCHAR(255)     NOT NULL,
    full_name      VARCHAR(255)     NOT NULL,
    full_name_kana VARCHAR(255)     NOT NULL,
    department_name VARCHAR(255)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_oidc_sub (oidc_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id       BIGINT UNSIGNED               NOT NULL AUTO_INCREMENT,
    status   ENUM('INCOMPLETE','COMPLETE') NOT NULL DEFAULT 'INCOMPLETE',
    owner_id BIGINT UNSIGNED               NOT NULL,
    title    VARCHAR(255)                  NOT NULL,
    body     LONGTEXT                      NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
