-- Remove manage-clients from bookkeeper — they can view but not create/edit/delete clients.
DELETE FROM role_has_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'bookkeeper')
  AND permission_id = (SELECT id FROM permissions WHERE name = 'manage-clients');
