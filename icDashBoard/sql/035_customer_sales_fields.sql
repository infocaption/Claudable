-- Migration 035: Add leadscore, engagement, and upsell columns to customers
-- These fields can be populated manually or via scheduled sync jobs (Admin → Datasynk)
ALTER TABLE customers ADD COLUMN leadscore INT NULL COMMENT 'Leadscore (0-100)';
ALTER TABLE customers ADD COLUMN engagement VARCHAR(50) NULL COMMENT 'Engagemangsnivå (e.g. Hög, Medel, Låg)';
ALTER TABLE customers ADD COLUMN upsell VARCHAR(50) NULL COMMENT 'Merförsäljningspotential (e.g. Hög, Medel, Låg)';
