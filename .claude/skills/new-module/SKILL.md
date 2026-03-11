---
name: new-module
description: Create a new dashboard module with ic-styles, ic-utils, postMessage setup, and DB registration
user-invocable: true
argument-hint: [module-name e.g. reports]
---

# Create a New Dashboard Module

Create a new module named `$ARGUMENTS`.

## Steps

1. **Create directory**: `icDashBoard/src/main/webapp/modules/{module-name}/`

2. **Create entry HTML file** (e.g., `index.html`):
   ```html
   <!DOCTYPE html>
   <html lang="sv">
   <head>
       <meta charset="UTF-8">
       <meta name="viewport" content="width=device-width, initial-scale=1.0">
       <title>{Module Name}</title>
       <link rel="stylesheet" href="../../shared/ic-styles.css">
       <link rel="stylesheet" href="../../shared/ic-icons.css">
       <style>
           /* Module-specific styles */
       </style>
   </head>
   <body>
       <div class="ic-container">
           <!-- Module content -->
       </div>
       <script src="../../shared/ic-utils.js"></script>
       <script>
       (async () => {
           ICUtils.notifyReady('{module-name}');
           // Module logic
       })();
       </script>
   </body>
   </html>
   ```

3. **Key patterns**:
   - Use `ic-styles.css` classes: `.ic-container`, `.ic-card`, `.ic-button`, `.ic-header`
   - Use `ic-icons` font for toolbar buttons (no emojis): `icon-search`, `icon-refresh`, etc.
   - API calls: `fetch('../../api/{endpoint}')` with relative paths
   - Toast notifications: `ICUtils.showToast('message', 'success|error')`
   - Open doc popup: `ICUtils.showDocPopup()`
   - postMessage to parent for navigation: `window.parent.postMessage({type:'LOAD_MODULE', moduleId: N}, '*')`

4. **Register in database** via SQL:
   ```sql
   INSERT INTO modules (module_type, name, icon, description, entry_file, directory_name, is_active)
   VALUES ('system', '{Name}', '{emoji}', '{description}', '{entry-file}', '{directory-name}', 1);
   ```

5. **Group assignment** (optional): Insert into `module_groups` to restrict visibility:
   ```sql
   INSERT INTO module_groups (module_id, group_id) VALUES (LAST_INSERT_ID(), {group_id});
   ```
   No `module_groups` rows = visible to everyone.

6. **If backend needed**: Use `/new-servlet` skill to create the API servlet.
