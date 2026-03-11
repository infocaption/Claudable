---
name: new-migration
description: Create a numbered SQL migration file following project conventions (utf8mb4, IF NOT EXISTS, proper collation)
user-invocable: true
argument-hint: [description e.g. add-reports-table]
---

# Create a New SQL Migration

Create a migration for: `$ARGUMENTS`

## Steps

1. **Find next number**: Check existing files in `icDashBoard/sql/` to determine the next migration number. Current highest is 024. Use 3-digit zero-padded format.

2. **Create file**: `icDashBoard/sql/{NNN}_{description}.sql`

3. **Conventions**:
   - Use `CREATE TABLE IF NOT EXISTS` for idempotent execution
   - Charset: `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
   - Foreign keys with `ON DELETE CASCADE` or `ON DELETE SET NULL` as appropriate
   - TIMESTAMP columns: `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`
   - TINYINT for booleans (0/1)
   - VARCHAR with explicit length, TEXT for large content
   - Include INSERT statements for seed data if needed

4. **Example structure**:
   ```sql
   -- {NNN}: {Description}
   -- {Date}

   CREATE TABLE IF NOT EXISTS `new_table` (
       `id` INT AUTO_INCREMENT PRIMARY KEY,
       `name` VARCHAR(200) NOT NULL,
       `is_active` TINYINT(1) NOT NULL DEFAULT 1,
       `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
   ```

5. **Run migration**:
   ```bash
   "C:/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < icDashBoard/sql/{NNN}_{description}.sql
   ```

6. **Update install.sql**: Add the new table/data to `install-package/install.sql` for fresh installs.

7. **If config keys added**: Add INSERT to `app_config` table. Key auto-appears in Admin panel.
