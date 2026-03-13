-- Migration 041: Rename stats columns from _30d to _7d
-- Data has always been collected in 7-day intervals (INTERVAL 7 DAY in getstats.ps1).
-- Column names said _30d which was misleading. This aligns names with reality.

ALTER TABLE customer_stats_daily
    CHANGE COLUMN publiseringar_30d         publiseringar_7d         INT NOT NULL DEFAULT 0,
    CHANGE COLUMN publiseringar_30_60d      publiseringar_7_14d      INT NOT NULL DEFAULT 0,
    CHANGE COLUMN skapade_guider_30d        skapade_guider_7d        INT NOT NULL DEFAULT 0,
    CHANGE COLUMN skapade_guider_30_60d     skapade_guider_7_14d     INT NOT NULL DEFAULT 0,
    CHANGE COLUMN visningar_30d             visningar_7d             INT NOT NULL DEFAULT 0,
    CHANGE COLUMN visningar_30_60d          visningar_7_14d          INT NOT NULL DEFAULT 0,
    CHANGE COLUMN processvisningar_30d      processvisningar_7d      INT NOT NULL DEFAULT 0,
    CHANGE COLUMN processvisningar_30_60d   processvisningar_7_14d   INT NOT NULL DEFAULT 0,
    CHANGE COLUMN processer_skapade_30d     processer_skapade_7d     INT NOT NULL DEFAULT 0,
    CHANGE COLUMN processer_skapade_30_60d  processer_skapade_7_14d  INT NOT NULL DEFAULT 0;
