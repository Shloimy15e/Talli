-- Department addresses identify the role; the display name identifies the business.
-- Only replace seeded values so edited profile names and signatures are preserved.
WITH departments(address, role) AS (
    VALUES ('billing@dynamiq.dev', 'Billing'),
           ('finance@dynamiq.dev', 'Finance'),
           ('support@dynamiq.dev', 'Support'),
           ('sales@dynamiq.dev', 'Sales')
)
UPDATE email_sender_profiles AS profile
SET name = CASE WHEN profile.name = 'Dynamiq ' || department.role
                THEN 'Dynamiq Solutions' ELSE profile.name END,
    signature_html = CASE
        WHEN profile.signature_html = '<strong>Dynamiq ' || department.role || '</strong><br><a href="mailto:' || department.address || '">' || department.address || '</a>'
        THEN '<strong>Dynamiq Solutions</strong><br>' || department.role || '<br><a href="mailto:' || department.address || '">' || department.address || '</a>'
        ELSE profile.signature_html END,
    version = profile.version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM departments AS department
WHERE profile.address = department.address
  AND (profile.name = 'Dynamiq ' || department.role
       OR profile.signature_html = '<strong>Dynamiq ' || department.role || '</strong><br><a href="mailto:' || department.address || '">' || department.address || '</a>');
