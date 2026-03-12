-- 034: Add renewal date (avtalsdatum) column to customers
ALTER TABLE customers ADD COLUMN renewal_date DATE NULL COMMENT 'Avtalsdatum / renewal date';
