-- 023_customer_tracks.sql
-- Adds track column to customers table for engagement track classification.
-- Values: comma-separated, e.g. 'map,train', 'guide', 'assist', NULL (unassigned)
-- Guide is exclusive (cannot combine with others).

ALTER TABLE customers ADD COLUMN track VARCHAR(100) NULL DEFAULT NULL
  COMMENT 'Engagement track: map,train,assist,guide (comma-separated)' AFTER coach_email;
