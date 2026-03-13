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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guide Planner API Servlet — shared project planning for guide updates.
 *
 * Endpoints:
 *   GET    /api/guide-planner/projects              — List all projects with task counts
 *   POST   /api/guide-planner/projects              — Create project
 *   PUT    /api/guide-planner/projects              — Update project name
 *   DELETE /api/guide-planner/projects?id=X         — Delete project
 *   GET    /api/guide-planner/tasks?projectId=X     — List tasks for project
 *   POST   /api/guide-planner/tasks                 — Create single task
 *   PUT    /api/guide-planner/tasks                 — Update task (partial)
 *   DELETE /api/guide-planner/tasks                 — Delete tasks {ids:[]}
 *   POST   /api/guide-planner/tasks/bulk-update     — Bulk status/assignee
 *   POST   /api/guide-planner/tasks/reorder         — Reorder task within group
 *   POST   /api/guide-planner/tasks/import          — Import tasks (merge/replace)
 *   GET    /api/guide-planner/assignees?projectId=X — List assignees
 *   POST   /api/guide-planner/assignees             — Add assignee
 *   DELETE /api/guide-planner/assignees?projectId=X&name=Y — Remove assignee
 */
public class GuidePlannerApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(GuidePlannerApiServlet.class);

    private static final String[] VALID_STATUSES = {
        "not_started", "in_progress", "bumped", "skipped", "completed"
    };
    private static final String[] VALID_TASK_TYPES = { "new", "update" };

    // ==================== Init ====================

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS guide_projects (" +
                "  id          INT AUTO_INCREMENT PRIMARY KEY," +
                "  name        VARCHAR(255) NOT NULL," +
                "  created_by  INT NULL," +
                "  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  CONSTRAINT fk_gp_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS guide_tasks (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  project_id      INT NOT NULL," +
                "  guide_id        VARCHAR(255) NOT NULL DEFAULT ''," +
                "  sv_id           VARCHAR(255) NOT NULL DEFAULT ''," +
                "  en_id           VARCHAR(255) NOT NULL DEFAULT ''," +
                "  no_id           VARCHAR(255) NOT NULL DEFAULT ''," +
                "  namn            VARCHAR(500) NOT NULL DEFAULT ''," +
                "  description     TEXT NULL," +
                "  task_type       ENUM('new','update') NOT NULL DEFAULT 'update'," +
                "  status          ENUM('not_started','in_progress','bumped','skipped','completed') NOT NULL DEFAULT 'not_started'," +
                "  assignee        VARCHAR(255) NOT NULL DEFAULT ''," +
                "  sort_order      INT NOT NULL DEFAULT 0," +
                "  started_at      TIMESTAMP NULL," +
                "  completed_at    TIMESTAMP NULL," +
                "  completion_time VARCHAR(50) NULL," +
                "  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  CONSTRAINT fk_gt_project FOREIGN KEY (project_id) REFERENCES guide_projects(id) ON DELETE CASCADE," +
                "  INDEX idx_gt_project (project_id)," +
                "  INDEX idx_gt_guide_id (guide_id)," +
                "  INDEX idx_gt_status (status)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS guide_project_assignees (" +
                "  id              INT AUTO_INCREMENT PRIMARY KEY," +
                "  project_id      INT NOT NULL," +
                "  assignee_name   VARCHAR(255) NOT NULL," +
                "  CONSTRAINT fk_gpa_project FOREIGN KEY (project_id) REFERENCES guide_projects(id) ON DELETE CASCADE," +
                "  UNIQUE KEY uq_gpa_project_assignee (project_id, assignee_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            // Seed default project if none exist
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM guide_projects");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO guide_projects (name) VALUES ('Projekt 1')");
            }
            rs.close();

            log.info("Guide planner tables verified/created successfully");
        } catch (SQLException e) {
            log.warn("Could not auto-create guide planner tables: {}", e.getMessage());
        }
    }

    // ==================== HTTP Method Handlers ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/projects") || pathInfo.equals("/projects/")) {
            handleListProjects(resp);
        } else if (pathInfo.equals("/tasks") || pathInfo.equals("/tasks/")) {
            handleListTasks(req, resp);
        } else if (pathInfo.equals("/assignees") || pathInfo.equals("/assignees/")) {
            handleListAssignees(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/projects") || pathInfo.equals("/projects/")) {
            handleCreateProject(req, resp, user);
        } else if (pathInfo.equals("/tasks") || pathInfo.equals("/tasks/")) {
            handleCreateTask(req, resp);
        } else if (pathInfo.equals("/tasks/bulk-update")) {
            handleBulkUpdate(req, resp);
        } else if (pathInfo.equals("/tasks/reorder")) {
            handleReorder(req, resp);
        } else if (pathInfo.equals("/tasks/import")) {
            handleImportTasks(req, resp);
        } else if (pathInfo.equals("/assignees") || pathInfo.equals("/assignees/")) {
            handleAddAssignee(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/projects") || pathInfo.equals("/projects/")) {
            handleUpdateProject(req, resp);
        } else if (pathInfo.equals("/tasks") || pathInfo.equals("/tasks/")) {
            handleUpdateTask(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = authenticate(req, resp);
        if (user == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        if (pathInfo.equals("/projects") || pathInfo.equals("/projects/")) {
            handleDeleteProject(req, resp);
        } else if (pathInfo.equals("/tasks") || pathInfo.equals("/tasks/")) {
            handleDeleteTasks(req, resp);
        } else if (pathInfo.equals("/assignees") || pathInfo.equals("/assignees/")) {
            handleDeleteAssignee(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== Project Handlers ====================

    private void handleListProjects(HttpServletResponse resp) throws IOException {
        String sql = "SELECT p.id, p.name, p.created_at, p.updated_at, " +
                     "COUNT(t.id) AS task_count, " +
                     "SUM(CASE WHEN t.status = 'completed' THEN 1 ELSE 0 END) AS completed_count " +
                     "FROM guide_projects p " +
                     "LEFT JOIN guide_tasks t ON t.project_id = p.id " +
                     "GROUP BY p.id ORDER BY p.id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append("{");
                json.append("\"id\":").append(rs.getInt("id"));
                json.append(",\"name\":").append(JsonUtil.quote(rs.getString("name")));
                json.append(",\"taskCount\":").append(rs.getInt("task_count"));
                json.append(",\"completedCount\":").append(rs.getInt("completed_count"));
                json.append(",\"createdAt\":").append(JsonUtil.quote(rs.getString("created_at")));
                json.append(",\"updatedAt\":").append(JsonUtil.quote(rs.getString("updated_at")));
                json.append("}");
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to list projects", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleCreateProject(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        String body = readRequestBody(req);
        String name = JsonUtil.extractJsonString(body, "name");
        if (name == null || name.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Name is required\"}");
            return;
        }

        String sql = "INSERT INTO guide_projects (name, created_by) VALUES (?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setInt(2, user.getId());
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            int id = 0;
            if (rs.next()) id = rs.getInt(1);

            resp.getWriter().write("{\"id\":" + id + ",\"name\":" + JsonUtil.quote(name.trim()) + "}");
        } catch (SQLException e) {
            log.error("Failed to create project", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleUpdateProject(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        int id = JsonUtil.extractJsonInt(body, "id");
        String name = JsonUtil.extractJsonString(body, "name");

        if (id <= 0 || name == null || name.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"id and name are required\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE guide_projects SET name = ? WHERE id = ?")) {
            ps.setString(1, name.trim());
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            resp.getWriter().write("{\"updated\":" + rows + "}");
        } catch (SQLException e) {
            log.error("Failed to update project", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleDeleteProject(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");
        if (idStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"id parameter required\"}");
            return;
        }

        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return;
        }

        // Prevent deleting the last project
        try (Connection conn = DBUtil.getConnection()) {
            try (PreparedStatement countPs = conn.prepareStatement("SELECT COUNT(*) FROM guide_projects")) {
                ResultSet rs = countPs.executeQuery();
                if (rs.next() && rs.getInt(1) <= 1) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Cannot delete the last project\"}");
                    return;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guide_projects WHERE id = ?")) {
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                resp.getWriter().write("{\"deleted\":" + rows + "}");
            }
        } catch (SQLException e) {
            log.error("Failed to delete project", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Task Handlers ====================

    private void handleListTasks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String projectIdStr = req.getParameter("projectId");
        if (projectIdStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"projectId parameter required\"}");
            return;
        }

        int projectId;
        try { projectId = Integer.parseInt(projectIdStr); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid projectId\"}");
            return;
        }

        String sql = "SELECT id, project_id, guide_id, sv_id, en_id, no_id, namn, description, " +
                     "task_type, status, assignee, sort_order, started_at, completed_at, " +
                     "completion_time, created_at, updated_at " +
                     "FROM guide_tasks WHERE project_id = ? ORDER BY sort_order, id";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                appendTaskJson(json, rs);
            }
            json.append("]");
            resp.getWriter().write(json.toString());

        } catch (SQLException e) {
            log.error("Failed to list tasks for project {}", projectId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleCreateTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        int projectId = JsonUtil.extractJsonInt(body, "projectId");
        if (projectId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"projectId is required\"}");
            return;
        }

        String svId = JsonUtil.extractJsonString(body, "svId");
        if (svId == null) svId = "";
        String guideId = JsonUtil.extractJsonString(body, "guideId");
        if (guideId == null) guideId = deriveGuideId(svId);
        String namn = JsonUtil.extractJsonString(body, "namn");
        if (namn == null) namn = "";
        String description = JsonUtil.extractJsonString(body, "description");
        String enId = JsonUtil.extractJsonString(body, "enId");
        if (enId == null) enId = "";
        String noId = JsonUtil.extractJsonString(body, "noId");
        if (noId == null) noId = "";
        String taskType = JsonUtil.extractJsonString(body, "taskType");
        if (!isValidTaskType(taskType)) taskType = "update";
        String status = JsonUtil.extractJsonString(body, "status");
        if (!isValidStatus(status)) status = "not_started";
        String assignee = JsonUtil.extractJsonString(body, "assignee");
        if (assignee == null) assignee = "";
        int sortOrder = JsonUtil.extractJsonInt(body, "sortOrder");

        String sql = "INSERT INTO guide_tasks (project_id, guide_id, sv_id, en_id, no_id, namn, " +
                     "description, task_type, status, assignee, sort_order) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, guideId);
            ps.setString(3, svId);
            ps.setString(4, enId);
            ps.setString(5, noId);
            ps.setString(6, namn);
            ps.setString(7, description);
            ps.setString(8, taskType);
            ps.setString(9, status);
            ps.setString(10, assignee);
            ps.setInt(11, sortOrder);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            int id = 0;
            if (rs.next()) id = rs.getInt(1);

            // Auto-add assignee to project assignees if not empty
            if (!assignee.isEmpty()) {
                addAssigneeIfMissing(conn, projectId, assignee);
            }

            resp.getWriter().write("{\"id\":" + id + "}");
        } catch (SQLException e) {
            log.error("Failed to create task", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleUpdateTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        int id = JsonUtil.extractJsonInt(body, "id");
        if (id <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"id is required\"}");
            return;
        }

        // Parameterized SET clauses (use ?)
        List<String> paramClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        // Literal SET clauses (no params, e.g. "started_at = NOW()")
        List<String> literalClauses = new ArrayList<>();

        String svId = JsonUtil.extractJsonString(body, "svId");
        if (svId != null) {
            paramClauses.add("sv_id = ?");
            params.add(svId);
            paramClauses.add("guide_id = ?");
            params.add(deriveGuideId(svId));
        }
        addStringUpdate(body, "enId", "en_id", paramClauses, params);
        addStringUpdate(body, "noId", "no_id", paramClauses, params);
        addStringUpdate(body, "namn", "namn", paramClauses, params);
        addStringUpdate(body, "description", "description", paramClauses, params);
        addStringUpdate(body, "assignee", "assignee", paramClauses, params);

        String taskType = JsonUtil.extractJsonString(body, "taskType");
        if (taskType != null && isValidTaskType(taskType)) {
            paramClauses.add("task_type = ?");
            params.add(taskType);
        }

        String status = JsonUtil.extractJsonString(body, "status");
        if (status != null && isValidStatus(status)) {
            paramClauses.add("status = ?");
            params.add(status);
            // Side effects are literal SQL (no params)
            applyStatusSideEffects(status, literalClauses, null);
        }

        int sortOrder = JsonUtil.extractJsonInt(body, "sortOrder");
        if (body.contains("\"sortOrder\"")) {
            paramClauses.add("sort_order = ?");
            params.add(sortOrder);
        }

        if (paramClauses.isEmpty() && literalClauses.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"No updates provided\"}");
            return;
        }

        // Combine: param clauses first, then literal clauses, then WHERE id = ?
        List<String> allClauses = new ArrayList<>(paramClauses);
        allClauses.addAll(literalClauses);
        String sql = "UPDATE guide_tasks SET " + String.join(", ", allClauses) + " WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : params) {
                if (p instanceof String) ps.setString(idx++, (String) p);
                else if (p instanceof Integer) ps.setInt(idx++, (Integer) p);
            }
            ps.setInt(idx, id); // WHERE id = ?
            int rows = ps.executeUpdate();

            // Auto-add assignee to project assignees
            String assignee = JsonUtil.extractJsonString(body, "assignee");
            if (assignee != null && !assignee.isEmpty()) {
                try (PreparedStatement lookup = conn.prepareStatement(
                        "SELECT project_id FROM guide_tasks WHERE id = ?")) {
                    lookup.setInt(1, id);
                    ResultSet rs = lookup.executeQuery();
                    if (rs.next()) {
                        addAssigneeIfMissing(conn, rs.getInt("project_id"), assignee);
                    }
                }
            }

            resp.getWriter().write("{\"updated\":" + rows + "}");
        } catch (SQLException e) {
            log.error("Failed to update task {}", id, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleDeleteTasks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        List<Integer> ids = extractJsonIntArray(body, "ids");
        if (ids == null || ids.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"ids array required\"}");
            return;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }

        String sql = "DELETE FROM guide_tasks WHERE id IN (" + placeholders + ")";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            int rows = ps.executeUpdate();
            resp.getWriter().write("{\"deleted\":" + rows + "}");
        } catch (SQLException e) {
            log.error("Failed to delete tasks", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleBulkUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        List<Integer> ids = extractJsonIntArray(body, "ids");
        if (ids == null || ids.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"ids array required\"}");
            return;
        }

        String status = JsonUtil.extractJsonString(body, "status");
        String assignee = JsonUtil.extractJsonString(body, "assignee");

        if (status == null && assignee == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"status or assignee required\"}");
            return;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }

        try (Connection conn = DBUtil.getConnection()) {
            int totalRows = 0;

            if (status != null && isValidStatus(status)) {
                List<String> paramClauses = new ArrayList<>();
                List<String> literalClauses = new ArrayList<>();
                List<Object> statusParams = new ArrayList<>();
                paramClauses.add("status = ?");
                statusParams.add(status);
                applyStatusSideEffects(status, literalClauses, null);

                List<String> allClauses = new ArrayList<>(paramClauses);
                allClauses.addAll(literalClauses);
                String sql = "UPDATE guide_tasks SET " + String.join(", ", allClauses) +
                             " WHERE id IN (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int idx = 1;
                    for (Object p : statusParams) {
                        if (p instanceof String) ps.setString(idx++, (String) p);
                        else if (p instanceof Integer) ps.setInt(idx++, (Integer) p);
                    }
                    for (int taskId : ids) {
                        ps.setInt(idx++, taskId);
                    }
                    totalRows = ps.executeUpdate();
                }
            }

            if (assignee != null) {
                String sql = "UPDATE guide_tasks SET assignee = ? WHERE id IN (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, assignee);
                    for (int i = 0; i < ids.size(); i++) {
                        ps.setInt(i + 2, ids.get(i));
                    }
                    totalRows += ps.executeUpdate();
                }

                // Auto-add assignee to project
                if (!assignee.isEmpty() && !ids.isEmpty()) {
                    try (PreparedStatement lookup = conn.prepareStatement(
                            "SELECT DISTINCT project_id FROM guide_tasks WHERE id = ?")) {
                        lookup.setInt(1, ids.get(0));
                        ResultSet rs = lookup.executeQuery();
                        if (rs.next()) {
                            addAssigneeIfMissing(conn, rs.getInt("project_id"), assignee);
                        }
                    }
                }
            }

            resp.getWriter().write("{\"updated\":" + totalRows + "}");
        } catch (SQLException e) {
            log.error("Failed to bulk update tasks", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleReorder(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        int projectId = JsonUtil.extractJsonInt(body, "projectId");
        String guideId = JsonUtil.extractJsonString(body, "guideId");
        int taskId = JsonUtil.extractJsonInt(body, "taskId");
        int newIndex = JsonUtil.extractJsonInt(body, "newIndex");

        if (projectId <= 0 || guideId == null || taskId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"projectId, guideId, taskId required\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Get all tasks in this guide group, ordered
                String sql = "SELECT id FROM guide_tasks WHERE project_id = ? AND guide_id = ? ORDER BY sort_order, id";
                List<Integer> orderedIds = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, projectId);
                    ps.setString(2, guideId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) orderedIds.add(rs.getInt("id"));
                }

                // Remove and re-insert at new position
                orderedIds.remove(Integer.valueOf(taskId));
                if (newIndex < 0) newIndex = 0;
                if (newIndex > orderedIds.size()) newIndex = orderedIds.size();
                orderedIds.add(newIndex, taskId);

                // Update sort_order for all tasks in the group
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE guide_tasks SET sort_order = ? WHERE id = ?")) {
                    for (int i = 0; i < orderedIds.size(); i++) {
                        ps.setInt(1, i);
                        ps.setInt(2, orderedIds.get(i));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                resp.getWriter().write("{\"reordered\":true}");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to reorder task", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleImportTasks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        int projectId = JsonUtil.extractJsonInt(body, "projectId");
        String mode = JsonUtil.extractJsonString(body, "mode");
        if (mode == null) mode = "merge";

        if (projectId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"projectId is required\"}");
            return;
        }

        // Parse the tasks array
        List<String> taskObjects = extractJsonObjectArray(body, "tasks");
        if (taskObjects == null || taskObjects.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"tasks array is required\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if ("replace".equals(mode)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM guide_tasks WHERE project_id = ?")) {
                        ps.setInt(1, projectId);
                        ps.executeUpdate();
                    }
                }

                int imported = 0;
                int maxSort = 0;

                // Get current max sort_order
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(MAX(sort_order), -1) FROM guide_tasks WHERE project_id = ?")) {
                    ps.setInt(1, projectId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) maxSort = rs.getInt(1);
                }

                for (String taskJson : taskObjects) {
                    String svId = JsonUtil.extractJsonString(taskJson, "svId");
                    if (svId == null) svId = "";
                    String guideId = deriveGuideId(svId);
                    String namn = JsonUtil.extractJsonString(taskJson, "namn");
                    if (namn == null) namn = "";
                    String description = JsonUtil.extractJsonString(taskJson, "description");
                    String enId = JsonUtil.extractJsonString(taskJson, "enId");
                    if (enId == null) enId = "";
                    String noId = JsonUtil.extractJsonString(taskJson, "noId");
                    if (noId == null) noId = "";
                    String taskType = JsonUtil.extractJsonString(taskJson, "taskType");
                    if (!isValidTaskType(taskType)) taskType = "update";
                    String assignee = JsonUtil.extractJsonString(taskJson, "assignee");
                    if (assignee == null) assignee = "";

                    if ("merge".equals(mode) && !svId.isEmpty()) {
                        // Check if task with this sv_id exists in project
                        try (PreparedStatement check = conn.prepareStatement(
                                "SELECT id FROM guide_tasks WHERE project_id = ? AND sv_id = ?")) {
                            check.setInt(1, projectId);
                            check.setString(2, svId);
                            ResultSet rs = check.executeQuery();
                            if (rs.next()) {
                                // Update existing
                                int existingId = rs.getInt("id");
                                try (PreparedStatement upd = conn.prepareStatement(
                                        "UPDATE guide_tasks SET namn = ?, description = ?, en_id = ?, " +
                                        "no_id = ?, task_type = ?, assignee = ? WHERE id = ?")) {
                                    upd.setString(1, namn.isEmpty() ? null : namn);
                                    upd.setString(2, description);
                                    upd.setString(3, enId);
                                    upd.setString(4, noId);
                                    upd.setString(5, taskType);
                                    upd.setString(6, assignee);
                                    upd.setInt(7, existingId);
                                    upd.executeUpdate();
                                }
                                imported++;
                                continue;
                            }
                        }
                    }

                    // Insert new task
                    maxSort++;
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO guide_tasks (project_id, guide_id, sv_id, en_id, no_id, " +
                            "namn, description, task_type, status, assignee, sort_order) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'not_started', ?, ?)")) {
                        ins.setInt(1, projectId);
                        ins.setString(2, guideId);
                        ins.setString(3, svId);
                        ins.setString(4, enId);
                        ins.setString(5, noId);
                        ins.setString(6, namn);
                        ins.setString(7, description);
                        ins.setString(8, taskType);
                        ins.setString(9, assignee);
                        ins.setInt(10, maxSort);
                        ins.executeUpdate();
                    }
                    imported++;
                }

                conn.commit();
                resp.getWriter().write("{\"imported\":" + imported + "}");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to import tasks", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Assignee Handlers ====================

    private void handleListAssignees(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String projectIdStr = req.getParameter("projectId");
        if (projectIdStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"projectId parameter required\"}");
            return;
        }

        int projectId;
        try { projectId = Integer.parseInt(projectIdStr); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid projectId\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT assignee_name FROM guide_project_assignees WHERE project_id = ? ORDER BY assignee_name")) {
            ps.setInt(1, projectId);
            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                json.append(JsonUtil.quote(rs.getString("assignee_name")));
            }
            json.append("]");
            resp.getWriter().write(json.toString());
        } catch (SQLException e) {
            log.error("Failed to list assignees", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleAddAssignee(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        int projectId = JsonUtil.extractJsonInt(body, "projectId");
        String name = JsonUtil.extractJsonString(body, "name");

        if (projectId <= 0 || name == null || name.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"projectId and name required\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            addAssigneeIfMissing(conn, projectId, name.trim());
            resp.getWriter().write("{\"added\":true}");
        } catch (SQLException e) {
            log.error("Failed to add assignee", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleDeleteAssignee(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String projectIdStr = req.getParameter("projectId");
        String name = req.getParameter("name");

        if (projectIdStr == null || name == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"projectId and name parameters required\"}");
            return;
        }

        int projectId;
        try { projectId = Integer.parseInt(projectIdStr); } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid projectId\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM guide_project_assignees WHERE project_id = ? AND assignee_name = ?")) {
            ps.setInt(1, projectId);
            ps.setString(2, name);
            int rows = ps.executeUpdate();
            resp.getWriter().write("{\"deleted\":" + rows + "}");
        } catch (SQLException e) {
            log.error("Failed to delete assignee", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Helpers ====================

    private void appendTaskJson(StringBuilder json, ResultSet rs) throws SQLException {
        json.append("{");
        json.append("\"id\":").append(rs.getInt("id"));
        json.append(",\"projectId\":").append(rs.getInt("project_id"));
        json.append(",\"guideId\":").append(JsonUtil.quote(rs.getString("guide_id")));
        json.append(",\"svId\":").append(JsonUtil.quote(rs.getString("sv_id")));
        json.append(",\"enId\":").append(JsonUtil.quote(rs.getString("en_id")));
        json.append(",\"noId\":").append(JsonUtil.quote(rs.getString("no_id")));
        json.append(",\"namn\":").append(JsonUtil.quote(rs.getString("namn")));
        json.append(",\"description\":").append(JsonUtil.quote(rs.getString("description")));
        json.append(",\"taskType\":").append(JsonUtil.quote(rs.getString("task_type")));
        json.append(",\"status\":").append(JsonUtil.quote(rs.getString("status")));
        json.append(",\"assignee\":").append(JsonUtil.quote(rs.getString("assignee")));
        json.append(",\"sortOrder\":").append(rs.getInt("sort_order"));

        String startedAt = rs.getString("started_at");
        json.append(",\"startedAt\":").append(startedAt != null ? JsonUtil.quote(startedAt) : "null");

        String completedAt = rs.getString("completed_at");
        json.append(",\"completedAt\":").append(completedAt != null ? JsonUtil.quote(completedAt) : "null");

        json.append(",\"completionTime\":").append(JsonUtil.quote(rs.getString("completion_time")));
        json.append(",\"createdAt\":").append(JsonUtil.quote(rs.getString("created_at")));
        json.append(",\"updatedAt\":").append(JsonUtil.quote(rs.getString("updated_at")));
        json.append("}");
    }

    private String deriveGuideId(String svId) {
        if (svId == null || svId.isEmpty()) return "";
        String[] parts = svId.split("-");
        if (parts.length > 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) sb.append("-");
                sb.append(parts[i]);
            }
            return sb.toString();
        }
        return svId;
    }

    /**
     * Add status side-effect SQL clauses. Uses literal SQL (no params) for
     * timestamp expressions like NOW(), COALESCE, and NULL.
     * The params parameter is ignored (kept for signature compatibility).
     */
    @SuppressWarnings("unused")
    private void applyStatusSideEffects(String status, List<String> setClauses, List<Object> params) {
        if ("in_progress".equals(status)) {
            setClauses.add("started_at = COALESCE(started_at, NOW())");
            setClauses.add("completed_at = NULL");
            setClauses.add("completion_time = NULL");
        } else if ("completed".equals(status)) {
            setClauses.add("started_at = COALESCE(started_at, NOW())");
            setClauses.add("completed_at = NOW()");
        } else {
            setClauses.add("started_at = NULL");
            setClauses.add("completed_at = NULL");
            setClauses.add("completion_time = NULL");
        }
        // Note: these clauses are literal SQL, they do NOT add params
    }

    private void addAssigneeIfMissing(Connection conn, int projectId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO guide_project_assignees (project_id, assignee_name) VALUES (?, ?)")) {
            ps.setInt(1, projectId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private void addStringUpdate(String body, String jsonKey, String dbColumn,
                                 List<String> setClauses, List<Object> params) {
        String value = JsonUtil.extractJsonString(body, jsonKey);
        if (value != null) {
            setClauses.add(dbColumn + " = ?");
            params.add(value);
        }
    }

    private boolean isValidStatus(String status) {
        if (status == null) return false;
        for (String s : VALID_STATUSES) {
            if (s.equals(status)) return true;
        }
        return false;
    }

    private boolean isValidTaskType(String type) {
        if (type == null) return false;
        for (String t : VALID_TASK_TYPES) {
            if (t.equals(type)) return true;
        }
        return false;
    }

    // ==================== Auth & JSON Utilities ====================

    private User authenticate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return null;
        }
        return (User) session.getAttribute("user");
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private List<Integer> extractJsonIntArray(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (!m.find()) return null;

        String content = m.group(1);
        List<Integer> result = new ArrayList<>();
        Matcher numMatcher = Pattern.compile("-?\\d+").matcher(content);
        while (numMatcher.find()) {
            try { result.add(Integer.parseInt(numMatcher.group())); } catch (NumberFormatException e) { /* skip */ }
        }
        return result;
    }

    /**
     * Extract an array of JSON objects by key.
     * Returns a list of individual JSON object strings.
     * Uses brace-depth tracking to correctly handle nested objects.
     */
    private List<String> extractJsonObjectArray(String json, String key) {
        if (json == null || key == null) return null;
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;

        // Find opening bracket
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;
        int bracketStart = json.indexOf('[', colonIdx);
        if (bracketStart < 0) return null;

        // Find matching closing bracket
        int depth = 0;
        int bracketEnd = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = bracketStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) { bracketEnd = i; break; }
                }
            }
        }
        if (bracketEnd < 0) return null;

        // Extract individual objects
        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        List<String> objects = new ArrayList<>();
        depth = 0;
        int objStart = -1;
        inString = false;
        escaped = false;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        objects.add(arrayContent.substring(objStart, i + 1));
                        objStart = -1;
                    }
                }
            }
        }

        return objects;
    }

}
