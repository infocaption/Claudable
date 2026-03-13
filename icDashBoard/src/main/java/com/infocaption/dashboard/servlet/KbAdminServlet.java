package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AdminUtil;
import com.infocaption.dashboard.util.AppConfig;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Knowledge Base Admin Servlet — CRUD operations for KB documents and collections.
 *
 * GET    /api/admin/kb/documents                 — List all documents (or single with ?id=N)
 * GET    /api/admin/kb/collections               — List all collections with document count
 * GET    /api/admin/kb/collections/documents?id=N — List documents in a collection
 *
 * POST   /api/admin/kb/documents                 — Create document
 * POST   /api/admin/kb/collections               — Create collection
 *
 * PUT    /api/admin/kb/documents                 — Update document
 * PUT    /api/admin/kb/collections               — Update collection
 * PUT    /api/admin/kb/collections/documents     — Set documents for a collection
 *
 * DELETE /api/admin/kb/documents?id=N            — Delete document
 * DELETE /api/admin/kb/collections?id=N          — Delete collection
 */
public class KbAdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(KbAdminServlet.class);

    @Override
    public void init() throws ServletException {
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS kb_documents (" +
                "  id          INT AUTO_INCREMENT PRIMARY KEY," +
                "  slug        VARCHAR(255) NOT NULL," +
                "  title       VARCHAR(500) NOT NULL," +
                "  content     MEDIUMTEXT   NULL," +
                "  file_type   VARCHAR(50)  NOT NULL DEFAULT 'markdown'," +
                "  tags        VARCHAR(1000) NULL," +
                "  created_by  INT          NULL," +
                "  created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_kb_doc_slug (slug)," +
                "  INDEX idx_kb_doc_title (title(100))," +
                "  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS kb_collections (" +
                "  id           INT AUTO_INCREMENT PRIMARY KEY," +
                "  name         VARCHAR(255) NOT NULL," +
                "  description  TEXT         NULL," +
                "  tool_prefix  VARCHAR(100) NOT NULL," +
                "  is_active    TINYINT(1)   NOT NULL DEFAULT 1," +
                "  created_by   INT          NULL," +
                "  created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_kb_coll_prefix (tool_prefix)," +
                "  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS kb_collection_documents (" +
                "  id            INT AUTO_INCREMENT PRIMARY KEY," +
                "  collection_id INT NOT NULL," +
                "  document_id   INT NOT NULL," +
                "  sort_order    INT NOT NULL DEFAULT 0," +
                "  added_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE INDEX idx_kb_cd_unique (collection_id, document_id)," +
                "  FOREIGN KEY (collection_id) REFERENCES kb_collections(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (document_id) REFERENCES kb_documents(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            log.info("kb_documents, kb_collections, kb_collection_documents tables verified/created");
        } catch (SQLException e) {
            log.error("Failed to create KB tables: {}", e.getMessage());
            throw new ServletException("KB table init failed", e);
        }
    }

    // ==================== GET ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        if (pathInfo.equals("/documents")) {
            String idStr = req.getParameter("id");
            if (idStr != null) {
                handleGetDocument(req, resp, idStr);
            } else {
                handleListDocuments(req, resp);
            }
        } else if (pathInfo.equals("/collections/documents")) {
            handleListCollectionDocuments(req, resp);
        } else if (pathInfo.equals("/collections")) {
            handleListCollections(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== POST ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        if (pathInfo.equals("/documents")) {
            handleCreateDocument(req, resp, admin);
        } else if (pathInfo.equals("/collections")) {
            handleCreateCollection(req, resp, admin);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== PUT ====================

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        if (pathInfo.equals("/documents")) {
            handleUpdateDocument(req, resp);
        } else if (pathInfo.equals("/collections/documents")) {
            handleSetCollectionDocuments(req, resp);
        } else if (pathInfo.equals("/collections")) {
            handleUpdateCollection(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== DELETE ====================

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        if (pathInfo.equals("/documents")) {
            handleDeleteDocument(req, resp);
        } else if (pathInfo.equals("/collections")) {
            handleDeleteCollection(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== Document Handlers ====================

    /**
     * GET /api/admin/kb/documents — list all documents.
     */
    private void handleListDocuments(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, slug, title, tags, file_type, created_at, updated_at " +
                 "FROM kb_documents ORDER BY updated_at DESC")) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"slug\":").append(JsonUtil.quote(rs.getString("slug")));
                    json.append(",\"title\":").append(JsonUtil.quote(rs.getString("title")));
                    json.append(",\"tags\":").append(JsonUtil.quote(rs.getString("tags")));
                    json.append(",\"fileType\":").append(JsonUtil.quote(rs.getString("file_type")));
                    json.append(",\"createdAt\":").append(JsonUtil.quote(
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null));
                    json.append(",\"updatedAt\":").append(JsonUtil.quote(
                            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null));
                    json.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error listing KB documents: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    /**
     * GET /api/admin/kb/documents?id=N — get single document with content.
     */
    private void handleGetDocument(HttpServletRequest req, HttpServletResponse resp, String idStr)
            throws IOException {

        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, slug, title, content, tags, file_type, created_by, created_at, updated_at " +
                 "FROM kb_documents WHERE id = ?")) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"Document not found\"}");
                    return;
                }

                StringBuilder json = new StringBuilder("{");
                json.append("\"id\":").append(rs.getInt("id"));
                json.append(",\"slug\":").append(JsonUtil.quote(rs.getString("slug")));
                json.append(",\"title\":").append(JsonUtil.quote(rs.getString("title")));
                json.append(",\"content\":").append(JsonUtil.quote(rs.getString("content")));
                json.append(",\"tags\":").append(JsonUtil.quote(rs.getString("tags")));
                json.append(",\"fileType\":").append(JsonUtil.quote(rs.getString("file_type")));
                json.append(",\"createdBy\":").append(rs.getInt("created_by"));
                json.append(",\"createdAt\":").append(JsonUtil.quote(
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null));
                json.append(",\"updatedAt\":").append(JsonUtil.quote(
                        rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null));
                json.append("}");

                resp.getWriter().write(json.toString());
            }
        } catch (SQLException e) {
            log.error("Error getting KB document {}: {}", id, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * POST /api/admin/kb/documents — create a new document.
     * Body: {title, slug, content, fileType, tags}
     */
    private void handleCreateDocument(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        String bodyStr = readBody(req);

        String title = JsonUtil.extractJsonString(bodyStr, "title");
        String slug = JsonUtil.extractJsonString(bodyStr, "slug");
        String content = JsonUtil.extractJsonString(bodyStr, "content");
        String fileType = JsonUtil.extractJsonString(bodyStr, "fileType");
        String tags = JsonUtil.extractJsonString(bodyStr, "tags");

        // Validate title
        if (title == null || title.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Title is required\"}");
            return;
        }

        // Auto-generate slug from title if not provided
        if (slug == null || slug.trim().isEmpty()) {
            slug = generateSlug(title);
        }

        // Default file type
        if (fileType == null || fileType.trim().isEmpty()) {
            fileType = "markdown";
        }

        // Validate content size
        int maxSize = AppConfig.getInt("kb.maxDocumentSize", 1048576);
        if (content != null && content.length() > maxSize) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Document content exceeds maximum size of " + maxSize + " bytes\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO kb_documents (slug, title, content, file_type, tags, created_by) " +
                 "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, slug.trim());
            ps.setString(2, title.trim());
            ps.setString(3, content);
            ps.setString(4, fileType.trim());
            ps.setString(5, tags);
            ps.setInt(6, admin.getId());
            ps.executeUpdate();

            int newId = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                newId = keys.next() ? keys.getInt(1) : 0;
            }

            resp.getWriter().write("{\"success\":true,\"id\":" + newId + "}");

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"A document with that slug already exists\"}");
            } else {
                log.error("Error creating KB document: {}", e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
            }
        }
    }

    /**
     * PUT /api/admin/kb/documents — update an existing document.
     * Body: {id, title, slug, content, fileType, tags}
     */
    private void handleUpdateDocument(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String bodyStr = readBody(req);

        int id = JsonUtil.extractJsonInt(bodyStr, "id");
        if (id <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Document id is required\"}");
            return;
        }

        String title = JsonUtil.extractJsonString(bodyStr, "title");
        String slug = JsonUtil.extractJsonString(bodyStr, "slug");
        String content = JsonUtil.extractJsonString(bodyStr, "content");
        String fileType = JsonUtil.extractJsonString(bodyStr, "fileType");
        String tags = JsonUtil.extractJsonString(bodyStr, "tags");

        // Validate content size if provided
        int maxSize = AppConfig.getInt("kb.maxDocumentSize", 1048576);
        if (content != null && content.length() > maxSize) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Document content exceeds maximum size of " + maxSize + " bytes\"}");
            return;
        }

        // Build dynamic UPDATE — only update provided fields
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (title != null) {
            setClauses.add("title = ?");
            params.add(title.trim());
        }
        if (slug != null) {
            setClauses.add("slug = ?");
            params.add(slug.trim());
        }
        if (content != null) {
            setClauses.add("content = ?");
            params.add(content);
        }
        if (fileType != null) {
            setClauses.add("file_type = ?");
            params.add(fileType.trim());
        }
        if (tags != null) {
            setClauses.add("tags = ?");
            params.add(tags);
        }

        if (setClauses.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"No fields to update\"}");
            return;
        }

        String sql = "UPDATE kb_documents SET " + String.join(", ", setClauses) + " WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int paramIdx = 1;
            for (Object param : params) {
                ps.setString(paramIdx++, (String) param);
            }
            ps.setInt(paramIdx, id);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Document not found\"}");
                return;
            }

            resp.getWriter().write("{\"success\":true,\"id\":" + id + "}");

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"A document with that slug already exists\"}");
            } else {
                log.error("Error updating KB document {}: {}", id, e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
            }
        }
    }

    /**
     * DELETE /api/admin/kb/documents?id=N — delete a document.
     */
    private void handleDeleteDocument(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int id = parseIdParam(req, resp);
        if (id <= 0) return;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM kb_documents WHERE id = ?")) {

            ps.setInt(1, id);
            int rows = ps.executeUpdate();

            resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");

        } catch (SQLException e) {
            log.error("Error deleting KB document {}: {}", id, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Collection Handlers ====================

    /**
     * GET /api/admin/kb/collections — list all collections with document count.
     */
    private void handleListCollections(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT c.id, c.name, c.description, c.tool_prefix, c.is_active, " +
                 "       c.created_at, c.updated_at, " +
                 "       COUNT(cd.document_id) AS document_count " +
                 "FROM kb_collections c " +
                 "LEFT JOIN kb_collection_documents cd ON c.id = cd.collection_id " +
                 "GROUP BY c.id " +
                 "ORDER BY c.name")) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"name\":").append(JsonUtil.quote(rs.getString("name")));
                    json.append(",\"description\":").append(JsonUtil.quote(rs.getString("description")));
                    json.append(",\"toolPrefix\":").append(JsonUtil.quote(rs.getString("tool_prefix")));
                    json.append(",\"isActive\":").append(rs.getBoolean("is_active"));
                    json.append(",\"documentCount\":").append(rs.getInt("document_count"));
                    json.append(",\"createdAt\":").append(JsonUtil.quote(
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null));
                    json.append(",\"updatedAt\":").append(JsonUtil.quote(
                            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null));
                    json.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error listing KB collections: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    /**
     * GET /api/admin/kb/collections/documents?id=N — list documents in a collection.
     */
    private void handleListCollectionDocuments(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int collectionId = parseIdParam(req, resp);
        if (collectionId <= 0) return;

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT d.id, d.slug, d.title, d.tags, d.file_type, d.created_at, d.updated_at " +
                 "FROM kb_documents d " +
                 "INNER JOIN kb_collection_documents cd ON d.id = cd.document_id " +
                 "WHERE cd.collection_id = ? " +
                 "ORDER BY cd.sort_order, d.title")) {

            ps.setInt(1, collectionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id"));
                    json.append(",\"slug\":").append(JsonUtil.quote(rs.getString("slug")));
                    json.append(",\"title\":").append(JsonUtil.quote(rs.getString("title")));
                    json.append(",\"tags\":").append(JsonUtil.quote(rs.getString("tags")));
                    json.append(",\"fileType\":").append(JsonUtil.quote(rs.getString("file_type")));
                    json.append(",\"createdAt\":").append(JsonUtil.quote(
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null));
                    json.append(",\"updatedAt\":").append(JsonUtil.quote(
                            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null));
                    json.append("}");
                }
            }
        } catch (SQLException e) {
            log.error("Error listing documents for collection {}: {}", collectionId, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        json.append("]");
        resp.getWriter().write(json.toString());
    }

    /**
     * POST /api/admin/kb/collections — create a new collection.
     * Body: {name, description, toolPrefix}
     */
    private void handleCreateCollection(HttpServletRequest req, HttpServletResponse resp, User admin)
            throws IOException {

        String bodyStr = readBody(req);

        String name = JsonUtil.extractJsonString(bodyStr, "name");
        String description = JsonUtil.extractJsonString(bodyStr, "description");
        String toolPrefix = JsonUtil.extractJsonString(bodyStr, "toolPrefix");

        // Validate name
        if (name == null || name.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Name is required\"}");
            return;
        }

        // Auto-generate toolPrefix from name if not provided
        if (toolPrefix == null || toolPrefix.trim().isEmpty()) {
            toolPrefix = generateToolPrefix(name);
        }

        // Validate toolPrefix format
        if (!toolPrefix.matches("^[a-z0-9_]+$")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"toolPrefix must contain only lowercase alphanumeric characters and underscores\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO kb_collections (name, description, tool_prefix, created_by) " +
                 "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name.trim());
            ps.setString(2, description);
            ps.setString(3, toolPrefix.trim());
            ps.setInt(4, admin.getId());
            ps.executeUpdate();

            int newId = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                newId = keys.next() ? keys.getInt(1) : 0;
            }

            resp.getWriter().write("{\"success\":true,\"id\":" + newId + "}");

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"A collection with that tool_prefix already exists\"}");
            } else {
                log.error("Error creating KB collection: {}", e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
            }
        }
    }

    /**
     * PUT /api/admin/kb/collections — update an existing collection.
     * Body: {id, name, description, toolPrefix, isActive}
     */
    private void handleUpdateCollection(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String bodyStr = readBody(req);

        int id = JsonUtil.extractJsonInt(bodyStr, "id");
        if (id <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Collection id is required\"}");
            return;
        }

        String name = JsonUtil.extractJsonString(bodyStr, "name");
        String description = JsonUtil.extractJsonString(bodyStr, "description");
        String toolPrefix = JsonUtil.extractJsonString(bodyStr, "toolPrefix");
        // For isActive, we need to check if it was explicitly provided
        boolean isActiveProvided = bodyStr.contains("\"isActive\"");
        boolean isActive = JsonUtil.extractJsonBoolean(bodyStr, "isActive");

        // Validate toolPrefix format if provided
        if (toolPrefix != null && !toolPrefix.matches("^[a-z0-9_]+$")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"toolPrefix must contain only lowercase alphanumeric characters and underscores\"}");
            return;
        }

        // Build dynamic UPDATE
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<Integer> paramTypes = new ArrayList<>();

        if (name != null) {
            setClauses.add("name = ?");
            params.add(name.trim());
            paramTypes.add(Types.VARCHAR);
        }
        if (description != null) {
            setClauses.add("description = ?");
            params.add(description);
            paramTypes.add(Types.VARCHAR);
        }
        if (toolPrefix != null) {
            setClauses.add("tool_prefix = ?");
            params.add(toolPrefix.trim());
            paramTypes.add(Types.VARCHAR);
        }
        if (isActiveProvided) {
            setClauses.add("is_active = ?");
            params.add(isActive);
            paramTypes.add(Types.BOOLEAN);
        }

        if (setClauses.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"No fields to update\"}");
            return;
        }

        String sql = "UPDATE kb_collections SET " + String.join(", ", setClauses) + " WHERE id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int paramIdx = 1;
            for (int i = 0; i < params.size(); i++) {
                int type = paramTypes.get(i);
                if (type == Types.BOOLEAN) {
                    ps.setBoolean(paramIdx++, (Boolean) params.get(i));
                } else {
                    ps.setString(paramIdx++, (String) params.get(i));
                }
            }
            ps.setInt(paramIdx, id);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Collection not found\"}");
                return;
            }

            resp.getWriter().write("{\"success\":true,\"id\":" + id + "}");

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"A collection with that tool_prefix already exists\"}");
            } else {
                log.error("Error updating KB collection {}: {}", id, e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error\"}");
            }
        }
    }

    /**
     * PUT /api/admin/kb/collections/documents — set documents for a collection.
     * Body: {collectionId, documentIds: [1,2,3]}
     */
    private void handleSetCollectionDocuments(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String bodyStr = readBody(req);

        int collectionId = JsonUtil.extractJsonInt(bodyStr, "collectionId");
        if (collectionId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"collectionId is required\"}");
            return;
        }

        // Extract documentIds array using regex
        String docIdsRaw = JsonUtil.extractJsonArray(bodyStr, "documentIds");
        List<Integer> documentIds = new ArrayList<>();
        if (docIdsRaw != null) {
            Matcher m = Pattern.compile("(\\d+)").matcher(docIdsRaw);
            while (m.find()) {
                documentIds.add(Integer.parseInt(m.group(1)));
            }
        }

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete existing document assignments for this collection
                try (PreparedStatement delPs = conn.prepareStatement(
                         "DELETE FROM kb_collection_documents WHERE collection_id = ?")) {
                    delPs.setInt(1, collectionId);
                    delPs.executeUpdate();
                }

                // Insert new document assignments
                if (!documentIds.isEmpty()) {
                    try (PreparedStatement insPs = conn.prepareStatement(
                             "INSERT INTO kb_collection_documents (collection_id, document_id, sort_order) " +
                             "VALUES (?, ?, ?)")) {
                        for (int i = 0; i < documentIds.size(); i++) {
                            insPs.setInt(1, collectionId);
                            insPs.setInt(2, documentIds.get(i));
                            insPs.setInt(3, i);
                            insPs.addBatch();
                        }
                        insPs.executeBatch();
                    }
                }

                conn.commit();
                resp.getWriter().write("{\"success\":true,\"collectionId\":" + collectionId +
                                       ",\"documentCount\":" + documentIds.size() + "}");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Error setting documents for collection {}: {}", collectionId, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    /**
     * DELETE /api/admin/kb/collections?id=N — delete a collection.
     * CASCADE deletes kb_collection_documents entries.
     */
    private void handleDeleteCollection(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int id = parseIdParam(req, resp);
        if (id <= 0) return;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM kb_collections WHERE id = ?")) {

            ps.setInt(1, id);
            int rows = ps.executeUpdate();

            resp.getWriter().write("{\"success\":true,\"rowsAffected\":" + rows + "}");

        } catch (SQLException e) {
            log.error("Error deleting KB collection {}: {}", id, e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Helpers ====================

    /**
     * Read the full request body as a string.
     */
    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    /**
     * Parse the "id" query parameter. Sends error response and returns 0 if invalid.
     */
    private int parseIdParam(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String idStr = req.getParameter("id");
        if (idStr == null || idStr.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing id parameter\"}");
            return 0;
        }

        try {
            int id = Integer.parseInt(idStr);
            if (id <= 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid id\"}");
                return 0;
            }
            return id;
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid id\"}");
            return 0;
        }
    }

    /**
     * Generate a URL-friendly slug from a title.
     * Lowercase, replace spaces with hyphens, strip non-alphanumeric (except hyphens).
     */
    private String generateSlug(String title) {
        return title.toLowerCase()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .trim()
                    .replaceAll("\\s+", "-");
    }

    /**
     * Generate a tool prefix from a collection name.
     * Lowercase, replace spaces with underscores, strip non-alphanumeric (except underscores).
     */
    private String generateToolPrefix(String name) {
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9\\s_]", "")
                   .trim()
                   .replaceAll("\\s+", "_");
    }
}
