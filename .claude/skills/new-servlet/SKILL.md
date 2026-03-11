---
name: new-servlet
description: Scaffold a new Java servlet with standard project patterns (auth, CSRF, JSON response, web.xml registration)
user-invocable: true
argument-hint: [ServletName e.g. ReportApi]
---

# Create a New Servlet

Create a new servlet named `$ARGUMENTS` following project conventions.

## Steps

1. **Read existing patterns** — Read `ModuleApiServlet.java` or `AdminApiServlet.java` for reference patterns (JSON construction, auth, error handling).

2. **Create Java file** at `icDashBoard/src/main/java/com/infocaption/dashboard/servlet/{Name}Servlet.java`:
   - Package: `com.infocaption.dashboard.servlet`
   - Extend `HttpServlet`
   - Use `@WebServlet` annotation OR register in web.xml (prefer web.xml for consistency)
   - Set `resp.setContentType("application/json; charset=UTF-8")`
   - JSON via `StringBuilder` (no Jackson/Gson)
   - Use `AppConfig.get("key", FALLBACK)` for configurable values
   - Try-with-resources for DB connections
   - Parameterized SQL queries
   - For admin-only: `AdminUtil.requireAdmin(req, resp)` guard

3. **Register in web.xml** at `icDashBoard/src/main/webapp/WEB-INF/web.xml`:
   ```xml
   <servlet>
       <servlet-name>{Name}Servlet</servlet-name>
       <servlet-class>com.infocaption.dashboard.servlet.{Name}Servlet</servlet-class>
   </servlet>
   <servlet-mapping>
       <servlet-name>{Name}Servlet</servlet-name>
       <url-pattern>/api/{url-path}</url-pattern>
   </servlet-mapping>
   ```

4. **If tables needed**: Add `CREATE TABLE IF NOT EXISTS` in `init()` method with `loadOnStartup` in web.xml.

5. **Update CLAUDE.md**: Add the new route to the Key URL Routes section if it's a significant endpoint.

6. **Create SQL migration** if new DB tables are needed (use `/new-migration` skill).
