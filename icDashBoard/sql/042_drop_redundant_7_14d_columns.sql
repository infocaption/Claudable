-- Migration 042: Drop redundant _7_14d columns
-- With weekly snapshots, the "previous 7 days" data is simply the prior
-- week's _7d row. No need to store it as a separate column.
-- The servlet computes prev-period comparisons via a date-range subquery.

ALTER TABLE customer_stats_daily
    DROP COLUMN publiseringar_7_14d,
    DROP COLUMN skapade_guider_7_14d,
    DROP COLUMN visningar_7_14d,
    DROP COLUMN processvisningar_7_14d,
    DROP COLUMN processer_skapade_7_14d;
