package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * GET /api/widgets
 * Returns a JSON array of all widgets visible to the current user,
 * filtered by group membership (same pattern as ModuleApiServlet).
 *
 * Widgets with no group assignment → visible to everyone.
 * Widgets assigned to "Alla" group → visible to everyone.
 * Widgets assigned to specific groups → visible only to group members.
 */
public class WidgetApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(WidgetApiServlet.class);

    @Override
    public void init() throws ServletException {
        // Auto-create tables if they don't exist (idempotent startup)
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS widgets (" +
                "  id          INT AUTO_INCREMENT PRIMARY KEY," +
                "  name        VARCHAR(100)  NOT NULL," +
                "  icon        VARCHAR(10)   NOT NULL DEFAULT '📦'," +
                "  description VARCHAR(500)  NULL," +
                "  render_key  VARCHAR(50)   NOT NULL UNIQUE," +
                "  custom_html TEXT          NULL," +
                "  custom_js   TEXT          NULL," +
                "  refresh_seconds INT       NOT NULL DEFAULT 0," +
                "  is_active   TINYINT       NOT NULL DEFAULT 1," +
                "  created_by  INT           NULL," +
                "  created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS widget_groups (" +
                "  widget_id   INT NOT NULL," +
                "  group_id    INT NOT NULL," +
                "  PRIMARY KEY (widget_id, group_id)," +
                "  CONSTRAINT fk_wg_widget FOREIGN KEY (widget_id) REFERENCES widgets(id) ON DELETE CASCADE," +
                "  CONSTRAINT fk_wg_group  FOREIGN KEY (group_id)  REFERENCES `groups`(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            log.info("Widget tables verified/created successfully");
        } catch (SQLException e) {
            log.warn("Could not auto-create widget tables: {}", e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        User user = (User) session.getAttribute("user");
        int userId = user.getId();

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        // Group-filtered widget query (same LEFT JOIN pattern as ModuleApiServlet)
        String sql = "SELECT DISTINCT w.id, w.name, w.icon, w.description, w.render_key, " +
                     "w.custom_html, w.custom_js, w.refresh_seconds " +
                     "FROM widgets w " +
                     "LEFT JOIN widget_groups wg ON w.id = wg.widget_id " +
                     "LEFT JOIN `groups` g ON wg.group_id = g.id " +
                     "WHERE w.is_active = 1 AND (" +
                     "  wg.group_id IS NULL " +
                     "  OR g.name = 'Alla' " +
                     "  OR wg.group_id IN (SELECT ug.group_id FROM user_groups ug WHERE ug.user_id = ?)" +
                     ") " +
                     "ORDER BY w.name";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id")).append(",");
                    json.append("\"name\":").append(JsonUtil.quote(rs.getString("name"))).append(",");
                    json.append("\"icon\":").append(JsonUtil.quote(rs.getString("icon"))).append(",");
                    json.append("\"description\":").append(JsonUtil.quote(rs.getString("description"))).append(",");
                    json.append("\"renderKey\":").append(JsonUtil.quote(rs.getString("render_key"))).append(",");
                    json.append("\"customHtml\":").append(JsonUtil.quote(rs.getString("custom_html"))).append(",");
                    json.append("\"customJs\":").append(JsonUtil.quote(rs.getString("custom_js"))).append(",");
                    json.append("\"refreshSeconds\":").append(rs.getInt("refresh_seconds"));
                    json.append("}");
                }

                json.append("]");

                PrintWriter out = resp.getWriter();
                out.write(json.toString());
                out.flush();
            }

        } catch (SQLException e) {
            log.error("Failed to load widgets for user", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}
