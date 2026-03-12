-- Migration 033: Add is_onboarding flag to customers table
-- Boolean flag to mark customers currently in onboarding phase
ALTER TABLE customers ADD COLUMN is_onboarding TINYINT(1) NOT NULL DEFAULT 0;
