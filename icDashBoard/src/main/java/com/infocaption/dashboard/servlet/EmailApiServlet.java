package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AcsEmailUtil;
import com.infocaption.dashboard.util.AppConfig;
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
 * Email API Servlet — handles email templates, sending, and history.
 *
 * Endpoints:
 *   GET    /api/email/templates         — List user's templates
 *   POST   /api/email/templates         — Create new template
 *   PUT    /api/email/templates/{id}    — Update template
 *   DELETE /api/email/templates/{id}    — Delete template
 *   POST   /api/email/send              — Send email broadcast
 *   GET    /api/email/history           — List send history
 *   GET    /api/email/history/{id}      — Get send details with recipients
 */
public class EmailApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(EmailApiServlet.class);

    @Override
    public void init() throws ServletException {
        // Auto-create tables on startup (idempotent)
        try (Connection conn = DBUtil.getConnection(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS email_templates (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  owner_user_id INT NOT NULL," +
                "  name VARCHAR(255) NOT NULL," +
                "  subject VARCHAR(500) NOT NULL," +
                "  body_html LONGTEXT NOT NULL," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS email_sends (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  sender_user_id INT NOT NULL," +
                "  template_id INT NULL," +
                "  subject VARCHAR(500) NOT NULL," +
                "  body_html LONGTEXT NOT NULL," +
                "  recipient_count INT DEFAULT 0," +
                "  sent_count INT DEFAULT 0," +
                "  failed_count INT DEFAULT 0," +
                "  status ENUM('pending','sending','completed','failed') DEFAULT 'pending'," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  completed_at TIMESTAMP NULL," +
                "  FOREIGN KEY (sender_user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (template_id) REFERENCES email_templates(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS email_recipients (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  send_id INT NOT NULL," +
                "  email VARCHAR(255) NOT NULL," +
                "  status ENUM('pending','sent','failed') DEFAULT 'pending'," +
                "  error_message TEXT NULL," +
                "  operation_id VARCHAR(255) NULL," +
                "  sent_at TIMESTAMP NULL," +
                "  FOREIGN KEY (send_id) REFERENCES email_sends(id) ON DELETE CASCADE," +
                "  INDEX idx_send_id (send_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            log("Email tables verified/created successfully");
        } catch (SQLException e) {
            log("Warning: Could not auto-create email tables: " + e.getMessage());
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

        if (pathInfo.equals("/templates") || pathInfo.equals("/templates/")) {
            handleListTemplates(req, resp, user);
        } else if (pathInfo.equals("/history") || pathInfo.equals("/history/")) {
            handleListHistory(req, resp, user);
        } else if (pathInfo.startsWith("/history/")) {
            String idStr = pathInfo.substring("/history/".length());
            handleHistoryDetail(req, resp, user, idStr);
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

        if (pathInfo.equals("/templates") || pathInfo.equals("/templates/")) {
            handleCreateTemplate(req, resp, user);
        } else if (pathInfo.equals("/send") || pathInfo.equals("/send/")) {
            handleSend(req, resp, user);
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
        if (pathInfo != null && pathInfo.startsWith("/templates/")) {
            String idStr = pathInfo.substring("/templates/".length());
            handleUpdateTemplate(req, resp, user, idStr);
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
        if (pathInfo != null && pathInfo.startsWith("/templates/")) {
            String idStr = pathInfo.substring("/templates/".length());
            handleDeleteTemplate(req, resp, user, idStr);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    // ==================== Template Handlers ====================

    private void handleListTemplates(HttpServletRequest req, HttpServletResponse resp, User user)
            throws IOException {
        String sql = "SELECT id, name, subject, body_html, created_at, updated_at " +
                     "FROM email_templates WHERE owner_user_id = ? ORDER BY updated_at DESC";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id")).append(",");
                    json.append("\"name\":").append(JsonUtil.quote(rs.getString("name"))).append(",");
                    json.append("\"subject\":").append(JsonUtil.quote(rs.getString("subject"))).append(",");
                    json.append("\"bodyHtml\":").append(JsonUtil.quote(rs.getString("body_html"))).append(",");
                    json.append("\"createdAt\":").append(JsonUtil.quote(rs.getTimestamp("created_at").toString())).append(",");
                    json.append("\"updatedAt\":").append(JsonUtil.quote(rs.getTimestamp("updated_at").toString()));
                    json.append("}");
                }

                json.append("]");
                resp.getWriter().write(json.toString());
            }

        } catch (SQLException e) {
            log.error("Failed to list email templates", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleCreateTemplate(HttpServletRequest req, HttpServletResponse resp, User user)
            throws IOException {
        String body = readRequestBody(req);
        String name = JsonUtil.extractJsonString(body, "name");
        String subject = JsonUtil.extractJsonString(body, "subject");
        String bodyHtml = JsonUtil.extractJsonString(body, "bodyHtml");

        if (name == null || subject == null || bodyHtml == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing required fields: name, subject, bodyHtml\"}");
            return;
        }

        String sql = "INSERT INTO email_templates (owner_user_id, name, subject, body_html) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, user.getId());
            ps.setString(2, name);
            ps.setString(3, subject);
            ps.setString(4, bodyHtml);
            ps.executeUpdate();

            int templateId = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    templateId = keys.getInt(1);
                }
            }

            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write("{\"success\":true,\"id\":" + templateId + "}");

        } catch (SQLException e) {
            log.error("Failed to create email template", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleUpdateTemplate(HttpServletRequest req, HttpServletResponse resp,
                                       User user, String idStr) throws IOException {
        int templateId;
        try {
            templateId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid template ID\"}");
            return;
        }

        String body = readRequestBody(req);
        String name = JsonUtil.extractJsonString(body, "name");
        String subject = JsonUtil.extractJsonString(body, "subject");
        String bodyHtml = JsonUtil.extractJsonString(body, "bodyHtml");

        if (name == null || subject == null || bodyHtml == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing required fields: name, subject, bodyHtml\"}");
            return;
        }

        String sql = "UPDATE email_templates SET name = ?, subject = ?, body_html = ? " +
                     "WHERE id = ? AND owner_user_id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, subject);
            ps.setString(3, bodyHtml);
            ps.setInt(4, templateId);
            ps.setInt(5, user.getId());
            int rows = ps.executeUpdate();

            if (rows > 0) {
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Template not found or not owned by you\"}");
            }

        } catch (SQLException e) {
            log.error("Failed to update email template id={}", templateId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleDeleteTemplate(HttpServletRequest req, HttpServletResponse resp,
                                       User user, String idStr) throws IOException {
        int templateId;
        try {
            templateId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid template ID\"}");
            return;
        }

        String sql = "DELETE FROM email_templates WHERE id = ? AND owner_user_id = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, templateId);
            ps.setInt(2, user.getId());
            int rows = ps.executeUpdate();

            if (rows > 0) {
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Template not found or not owned by you\"}");
            }

        } catch (SQLException e) {
            log.error("Failed to delete email template id={}", templateId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Send Handler ====================

    private void handleSend(HttpServletRequest req, HttpServletResponse resp, User user)
            throws IOException {
        String body = readRequestBody(req);
        String subject = JsonUtil.extractJsonString(body, "subject");
        String bodyHtml = JsonUtil.extractJsonString(body, "bodyHtml");
        List<String> recipients = JsonUtil.extractJsonStringList(body, "recipients");

        if (subject == null || bodyHtml == null || recipients == null || recipients.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing required fields: subject, bodyHtml, recipients\"}");
            return;
        }

        // Validate email addresses
        List<String> validEmails = new ArrayList<>();
        Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        for (String email : recipients) {
            String trimmed = email.trim();
            if (!trimmed.isEmpty() && emailPattern.matcher(trimmed).matches()) {
                if (!validEmails.contains(trimmed.toLowerCase())) {
                    validEmails.add(trimmed.toLowerCase());
                }
            }
        }

        if (validEmails.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"No valid email addresses provided\"}");
            return;
        }

        // Extract optional template ID
        String templateIdStr = JsonUtil.extractJsonString(body, "templateId");
        Integer templateId = null;
        if (templateIdStr != null) {
            try {
                templateId = Integer.parseInt(templateIdStr);
            } catch (NumberFormatException ignored) {}
        }

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Insert email_sends record
                String insertSend = "INSERT INTO email_sends (sender_user_id, template_id, subject, body_html, recipient_count, status) " +
                                   "VALUES (?, ?, ?, ?, ?, 'sending')";
                int sendId;
                try (PreparedStatement ps = conn.prepareStatement(insertSend, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, user.getId());
                    if (templateId != null) {
                        ps.setInt(2, templateId);
                    } else {
                        ps.setNull(2, Types.INTEGER);
                    }
                    ps.setString(3, subject);
                    ps.setString(4, bodyHtml);
                    ps.setInt(5, validEmails.size());
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        sendId = keys.getInt(1);
                    }
                }

                // 2. Insert all recipients
                String insertRecipient = "INSERT INTO email_recipients (send_id, email) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertRecipient)) {
                    for (String email : validEmails) {
                        ps.setInt(1, sendId);
                        ps.setString(2, email);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();

                // 3. Send emails (outside transaction)
                // Parse optional recipientData for variable substitution
                // Format: "recipientData": { "user@co.com": { "serverUrl": "x", "companyName": "y" }, ... }
                String recipientDataBlock = JsonUtil.extractJsonObject(body, "recipientData");
                // Parse global variables (e.g., updateDate)
                String globalUpdateDate = JsonUtil.extractJsonString(body, "updateDate");

                conn.setAutoCommit(true);
                int sentCount = 0;
                int failedCount = 0;

                for (String email : validEmails) {
                    try {
                        // Apply per-recipient variable substitution
                        String personalizedSubject = subject;
                        String personalizedBody = bodyHtml;

                        // Per-recipient variables from recipientData
                        // HTML-escape all variable values to prevent HTML injection in emails
                        if (recipientDataBlock != null) {
                            String recipData = JsonUtil.extractJsonObject(recipientDataBlock, email);
                            if (recipData != null) {
                                String serverUrl = JsonUtil.extractJsonString(recipData, "serverUrl");
                                String companyName = JsonUtil.extractJsonString(recipData, "companyName");
                                if (serverUrl != null) {
                                    String safeServerUrl = escapeHtml(serverUrl);
                                    personalizedSubject = personalizedSubject.replace("{{serverUrl}}", serverUrl);
                                    personalizedBody = personalizedBody.replace("{{serverUrl}}", safeServerUrl);
                                }
                                if (companyName != null) {
                                    String safeCompanyName = escapeHtml(companyName);
                                    personalizedSubject = personalizedSubject.replace("{{companyName}}", companyName);
                                    personalizedBody = personalizedBody.replace("{{companyName}}", safeCompanyName);
                                }
                            }
                        }

                        // Always replace {{email}} with the recipient's email
                        personalizedSubject = personalizedSubject.replace("{{email}}", email);
                        personalizedBody = personalizedBody.replace("{{email}}", escapeHtml(email));

                        // Global variables
                        if (globalUpdateDate != null) {
                            personalizedSubject = personalizedSubject.replace("{{updateDate}}", globalUpdateDate);
                            personalizedBody = personalizedBody.replace("{{updateDate}}", escapeHtml(globalUpdateDate));
                        }

                        String operationId = AcsEmailUtil.sendEmail(email, personalizedSubject, personalizedBody);

                        // Update recipient status
                        String updateRecip = "UPDATE email_recipients SET status = 'sent', operation_id = ?, sent_at = NOW() " +
                                           "WHERE send_id = ? AND email = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateRecip)) {
                            ps.setString(1, operationId);
                            ps.setInt(2, sendId);
                            ps.setString(3, email);
                            ps.executeUpdate();
                        }
                        sentCount++;

                    } catch (Exception e) {
                        // Update recipient as failed
                        String updateRecip = "UPDATE email_recipients SET status = 'failed', error_message = ? " +
                                           "WHERE send_id = ? AND email = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateRecip)) {
                            ps.setString(1, e.getMessage());
                            ps.setInt(2, sendId);
                            ps.setString(3, email);
                            ps.executeUpdate();
                        }
                        failedCount++;
                        log.error("Failed to send email to {}", email, e);
                    }
                }

                // 4. Update send record with final status
                String status = failedCount == validEmails.size() ? "failed" : "completed";
                String updateSend = "UPDATE email_sends SET sent_count = ?, failed_count = ?, status = ?, completed_at = NOW() " +
                                   "WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSend)) {
                    ps.setInt(1, sentCount);
                    ps.setInt(2, failedCount);
                    ps.setString(3, status);
                    ps.setInt(4, sendId);
                    ps.executeUpdate();
                }

                // 5. Return result
                StringBuilder json = new StringBuilder("{");
                json.append("\"success\":true,");
                json.append("\"sendId\":").append(sendId).append(",");
                json.append("\"recipientCount\":").append(validEmails.size()).append(",");
                json.append("\"sentCount\":").append(sentCount).append(",");
                json.append("\"failedCount\":").append(failedCount).append(",");
                json.append("\"status\":").append(JsonUtil.quote(status));
                json.append("}");

                resp.getWriter().write(json.toString());

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            log.error("Failed to process email send operation", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== History Handlers ====================

    private void handleListHistory(HttpServletRequest req, HttpServletResponse resp, User user)
            throws IOException {
        int historyLimit = AppConfig.getInt("email.historyLimit", 100);
        String sql = "SELECT id, subject, recipient_count, sent_count, failed_count, status, created_at, completed_at " +
                     "FROM email_sends WHERE sender_user_id = ? ORDER BY created_at DESC LIMIT " + historyLimit;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("id")).append(",");
                    json.append("\"subject\":").append(JsonUtil.quote(rs.getString("subject"))).append(",");
                    json.append("\"recipientCount\":").append(rs.getInt("recipient_count")).append(",");
                    json.append("\"sentCount\":").append(rs.getInt("sent_count")).append(",");
                    json.append("\"failedCount\":").append(rs.getInt("failed_count")).append(",");
                    json.append("\"status\":").append(JsonUtil.quote(rs.getString("status"))).append(",");
                    json.append("\"createdAt\":").append(JsonUtil.quote(rs.getTimestamp("created_at").toString()));

                    Timestamp completedAt = rs.getTimestamp("completed_at");
                    if (completedAt != null) {
                        json.append(",\"completedAt\":").append(JsonUtil.quote(completedAt.toString()));
                    } else {
                        json.append(",\"completedAt\":null");
                    }

                    json.append("}");
                }

                json.append("]");
                resp.getWriter().write(json.toString());
            }

        } catch (SQLException e) {
            log.error("Failed to load email send history", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    private void handleHistoryDetail(HttpServletRequest req, HttpServletResponse resp,
                                      User user, String idStr) throws IOException {
        int sendId;
        try {
            sendId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid send ID\"}");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {

            // Get send record
            String sqlSend = "SELECT id, subject, body_html, recipient_count, sent_count, failed_count, status, created_at, completed_at " +
                            "FROM email_sends WHERE id = ? AND sender_user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlSend)) {
                ps.setInt(1, sendId);
                ps.setInt(2, user.getId());
                try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\":\"Send record not found\"}");
                    return;
                }

                StringBuilder json = new StringBuilder("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"subject\":").append(JsonUtil.quote(rs.getString("subject"))).append(",");
                json.append("\"bodyHtml\":").append(JsonUtil.quote(rs.getString("body_html"))).append(",");
                json.append("\"recipientCount\":").append(rs.getInt("recipient_count")).append(",");
                json.append("\"sentCount\":").append(rs.getInt("sent_count")).append(",");
                json.append("\"failedCount\":").append(rs.getInt("failed_count")).append(",");
                json.append("\"status\":").append(JsonUtil.quote(rs.getString("status"))).append(",");
                json.append("\"createdAt\":").append(JsonUtil.quote(rs.getTimestamp("created_at").toString()));

                Timestamp completedAt = rs.getTimestamp("completed_at");
                if (completedAt != null) {
                    json.append(",\"completedAt\":").append(JsonUtil.quote(completedAt.toString()));
                } else {
                    json.append(",\"completedAt\":null");
                }

                // Get recipients
                json.append(",\"recipients\":[");
                String sqlRecip = "SELECT email, status, error_message, sent_at FROM email_recipients WHERE send_id = ? ORDER BY id";
                try (PreparedStatement psR = conn.prepareStatement(sqlRecip)) {
                    psR.setInt(1, sendId);
                    try (ResultSet rsR = psR.executeQuery()) {
                        boolean firstR = true;

                        while (rsR.next()) {
                            if (!firstR) json.append(",");
                            firstR = false;

                            json.append("{");
                            json.append("\"email\":").append(JsonUtil.quote(rsR.getString("email"))).append(",");
                            json.append("\"status\":").append(JsonUtil.quote(rsR.getString("status")));

                            String errorMsg = rsR.getString("error_message");
                            if (errorMsg != null) {
                                json.append(",\"error\":").append(JsonUtil.quote(errorMsg));
                            }

                            Timestamp sentAt = rsR.getTimestamp("sent_at");
                            if (sentAt != null) {
                                json.append(",\"sentAt\":").append(JsonUtil.quote(sentAt.toString()));
                            }

                            json.append("}");
                        }
                    }
                }

                json.append("]}");
                resp.getWriter().write(json.toString());
                }
            }

        } catch (SQLException e) {
            log.error("Failed to load email send history detail for sendId={}", sendId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Authenticate the request and return the User, or send 401 and return null.
     */
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

    /**
     * Read the full request body as a string.
     */
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

    /**
     * Escape HTML special characters to prevent HTML injection in email bodies.
     * Template variables substituted into HTML email content must be escaped.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }
}
