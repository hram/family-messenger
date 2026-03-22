INSERT INTO families (id, name, created_at)
VALUES (1, 'Demo Family', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO system_setup (id, family_id, master_password_hash, initialized_at)
VALUES (1, 1, 'seed-bootstrap-placeholder', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO invites (family_id, code, role, display_name, is_admin, is_active, max_uses, uses_count, created_at, expires_at)
VALUES
    (1, 'PARENT-DEMO', 'PARENT', 'Женя', TRUE, TRUE, 1, 0, NOW(), NULL),
    (1, 'CHILD-DEMO', 'CHILD', 'Катя', FALSE, TRUE, 1, 0, NOW(), NULL)
ON CONFLICT (code) DO NOTHING;
