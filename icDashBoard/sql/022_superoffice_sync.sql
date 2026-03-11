-- 022_superoffice_sync.sql
-- Creates two Data Sync configurations for SuperOffice company data.
-- Config 1: Upserts company data into customers table (name + coach email)
-- Config 2: Links servers to their company by matching URL (update-only)

-- Config 1: SuperOffice → Kunder
INSERT INTO sync_configs (name, source_url, auth_type, json_root_path, target_table,
    id_field_source, id_field_target, field_mappings, schedule_minutes, update_only, created_by)
VALUES (
    'SuperOffice → Kunder',
    'https://online2.superoffice.com/Cust16404/CS/scripts/customer.fcgi?action=safeParse&includeId=getCompanyServer&key=9Xl6DQJ3ttZ9hBNK',
    'none',
    'serverCompanyLink',
    'customers',
    'companyId',
    'company_id',
    '[{"source":"companyName","target":"company_name"},{"source":"coachEmail","target":"coach_email"}]',
    60,
    0,
    (SELECT id FROM users WHERE is_admin = 1 ORDER BY id LIMIT 1)
);

-- Config 2: SuperOffice → Serverkoppling (update-only, uses url_normalize transform)
INSERT INTO sync_configs (name, source_url, auth_type, json_root_path, target_table,
    id_field_source, id_field_target, field_mappings, schedule_minutes, update_only, created_by)
VALUES (
    'SuperOffice → Serverkoppling',
    'https://online2.superoffice.com/Cust16404/CS/scripts/customer.fcgi?action=safeParse&includeId=getCompanyServer&key=9Xl6DQJ3ttZ9hBNK',
    'none',
    'serverCompanyLink',
    'servers',
    'serverUrl|url_normalize',
    'url_normalized',
    '[{"source":"companyId","target":"customer_id","lookup":"customers.company_id"}]',
    60,
    1,
    (SELECT id FROM users WHERE is_admin = 1 ORDER BY id LIMIT 1)
);
