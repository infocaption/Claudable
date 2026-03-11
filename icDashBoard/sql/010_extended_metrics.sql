-- ============================================================
-- Migration 010: Add extended metrics for risk/success analysis
--
-- New columns in customer_stats_daily:
--   - antal_inloggningar_prod_admin: Non-unique login count for producers/admins (daily)
--   - antal_unika_inloggade_prod_admin: Unique producers/admins who logged in (daily)
--   - antal_unika_publicerande_prod: Unique producers who published at least one guide (daily)
--
-- These support the following feedback criteria:
--   RISK:  "Antal inloggningar (producent/admin) < 1 senaste månaden" (weight 2)
--   RISK:  "Inloggningar ned under 20% av 30-dagars total" (weight 3)
--   SUCCÉ: "Antal inloggningar (producent/admin) > 100 senaste månaden" (weight 2)
--   SUCCÉ: "Ökning av antal unika producenter som publicerar +20% över 3 mån" (weight 4)
--
-- Run: "C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < 010_extended_metrics.sql
-- ============================================================

-- Add login metrics
ALTER TABLE customer_stats_daily
    ADD COLUMN antal_inloggningar_prod_admin INT NOT NULL DEFAULT 0
        COMMENT 'Non-unique login events by producers/admins (daily count)'
        AFTER antal_aktiva_producenter_6m,
    ADD COLUMN antal_unika_inloggade_prod_admin INT NOT NULL DEFAULT 0
        COMMENT 'Unique producers/admins who logged in (daily count)'
        AFTER antal_inloggningar_prod_admin;

-- Add unique publishing producers metric
ALTER TABLE customer_stats_daily
    ADD COLUMN antal_unika_publicerande_prod INT NOT NULL DEFAULT 0
        COMMENT 'Unique producers who published at least one guide (daily count)'
        AFTER antal_unika_inloggade_prod_admin;
