-- Migration 013: SSO-based group auto-assignment
-- Adds sso_department column to groups table for SAML department claim mapping
-- Adds app_config entries for SSO group behavior
-- Run with: mysql -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < 013_sso_group_mapping.sql

-- 1. Add sso_department column to groups table
ALTER TABLE `groups`
    ADD COLUMN `sso_department` VARCHAR(200) NULL DEFAULT NULL
    AFTER `is_hidden`;

-- 2. Add index for fast lookup by SSO department value during login
CREATE INDEX idx_groups_sso_department ON `groups` (`sso_department`);

-- 3. Seed app_config entries for SSO claim URIs (all configurable in Admin → Inställningar)
INSERT INTO app_config (config_key, config_value, category, description, is_secret) VALUES
('sso.emailClaimUri', 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress', 'security', 'SAML claim-URI för e-postadress (fallback: NameID)', 0),
('sso.givenNameClaimUri', 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname', 'security', 'SAML claim-URI för förnamn', 0),
('sso.surnameClaimUri', 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname', 'security', 'SAML claim-URI för efternamn', 0),
('sso.nameClaimUri', 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name', 'security', 'SAML claim-URI för visningsnamn (fallback 1)', 0),
('sso.displayNameClaimUri', 'http://schemas.microsoft.com/identity/claims/displayname', 'security', 'SAML claim-URI för visningsnamn (fallback 2, Microsoft-specifik)', 0),
('sso.departmentClaimUri', 'department/department', 'security', 'SAML claim-URI för avdelningsattribut (används för automatisk grupptilldelning vid SSO-inloggning)', 0),
('sso.autoCreateGroups', 'true', 'security', 'Skapa grupper automatiskt när SSO-avdelning saknar matchande grupp (true/false)', 0),
('sso.autoAssignGroups', 'true', 'security', 'Tilldela användare till grupper automatiskt baserat på SSO department-claim vid inloggning (true/false)', 0)
ON DUPLICATE KEY UPDATE config_key = config_key;
