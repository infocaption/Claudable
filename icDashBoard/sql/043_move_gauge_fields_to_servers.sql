-- Migration 043: Move gauge fields from customer_stats_daily to servers
-- antal_producenter, antal_administratorer, totalt_antal_anvandare are point-in-time
-- measurements, not weekly activity. They belong on the servers table.
-- antal_aktiva_producenter_6m is NOT moved (it's a rolling window metric).

-- Step 1: Add columns to servers
ALTER TABLE servers
  ADD COLUMN antal_producenter INT NOT NULL DEFAULT 0,
  ADD COLUMN antal_administratorer INT NOT NULL DEFAULT 0,
  ADD COLUMN totalt_antal_anvandare INT NOT NULL DEFAULT 0;

-- Step 2: Copy latest snapshot values to servers
UPDATE servers s
  JOIN (
    SELECT csd.server_id, csd.antal_producenter, csd.antal_administratorer, csd.totalt_antal_anvandare
    FROM customer_stats_daily csd
    INNER JOIN (
      SELECT server_id, MAX(snapshot_date) AS max_date
      FROM customer_stats_daily GROUP BY server_id
    ) latest ON csd.server_id = latest.server_id AND csd.snapshot_date = latest.max_date
  ) src ON s.id = src.server_id
SET s.antal_producenter = src.antal_producenter,
    s.antal_administratorer = src.antal_administratorer,
    s.totalt_antal_anvandare = src.totalt_antal_anvandare;

-- Step 3: Drop columns from customer_stats_daily
ALTER TABLE customer_stats_daily
  DROP COLUMN antal_producenter,
  DROP COLUMN antal_administratorer,
  DROP COLUMN totalt_antal_anvandare;
