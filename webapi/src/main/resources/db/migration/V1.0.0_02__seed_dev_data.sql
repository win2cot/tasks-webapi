-- Dev seed data: test tenant and users corresponding to tasks-realm.json accounts.
-- oidc_sub values match the fixed UUIDs declared in keycloak/realm-export/tasks-realm.json.
-- Remove before first production deployment.

INSERT INTO tenants (id, code, name, plan, status, created_at, updated_at) VALUES
  (1, 'tenant1', 'テナント1', 'STANDARD', 'ACTIVE', NOW(), NOW());

INSERT INTO users (id, oidc_sub, email, full_name, full_name_kana, status, version) VALUES
  (1, '69649e2f-63a3-4b75-ae2e-2916ee22e1b5', 'admin@example.com',          'Admin User',     'アドミン ユーザー',        'ACTIVE', 0),
  (2, '0090cd3a-7ac2-4409-b086-23a285c196ea', 'tenant1-admin@example.com',   'Tenant1 Admin',  'テナントイチ アドミン',    'ACTIVE', 0),
  (3, 'c490c707-819c-4ac7-860f-6e05426a6708', 'tenant1-member1@example.com', 'Tenant1 Member1','テナントイチ メンバーイチ', 'ACTIVE', 0),
  (4, '11873fb0-285d-4188-bd3b-cf223c257ecf', 'tenant1-member2@example.com', 'Tenant1 Member2','テナントイチ メンバーニ',   'ACTIVE', 0);

INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at) VALUES
  (2, 1, 'TENANT_ADMIN', 'ACTIVE', NOW()),
  (3, 1, 'MEMBER',       'ACTIVE', NOW()),
  (4, 1, 'MEMBER',       'ACTIVE', NOW());

INSERT INTO app_admin_users (oidc_sub, created_at) VALUES
  ('69649e2f-63a3-4b75-ae2e-2916ee22e1b5', NOW());
