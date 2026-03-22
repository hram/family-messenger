INSERT INTO families (id, name, created_at)
VALUES (1, 'Demo Family', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO invites (family_id, code, role, display_name, is_active, max_uses, uses_count, created_at, expires_at)
VALUES
    (1, 'PARENT-DEMO', 'PARENT', 'Женя', TRUE, 1, 0, NOW(), NULL),
    (1, 'CHILD-DEMO', 'CHILD', 'Катя', TRUE, 1, 0, NOW(), NULL)
ON CONFLICT (code) DO NOTHING;
