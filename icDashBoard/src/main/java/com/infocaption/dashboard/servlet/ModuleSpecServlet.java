package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;

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

/**
 * GET /api/module/spec?id=X         -> Returns AI spec text as text/markdown
 * GET /api/module/spec?id=X&download=true -> Returns as downloadable .md file
 *
 * Access: system modules (anyone), shared modules (anyone), private modules (owner only)
 */
public class ModuleSpecServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ModuleSpecServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write("Not authenticated");
            return;
        }

        User user = (User) session.getAttribute("user");
        String idStr = req.getParameter("id");
        boolean download = "true".equals(req.getParameter("download"));

        if (idStr == null || idStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write("Missing parameter: id");
            return;
        }

        int moduleId;
        try {
            moduleId = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write("Invalid module ID");
            return;
        }

        String sql = "SELECT name, directory_name, module_type, owner_user_id, ai_spec_text " +
                     "FROM modules WHERE id = ? AND is_active = 1";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, moduleId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setContentType("text/plain; charset=UTF-8");
                resp.getWriter().write("Module not found");
                return;
            }

            String moduleType = rs.getString("module_type");
            Integer ownerId = rs.getObject("owner_user_id") != null ? rs.getInt("owner_user_id") : null;
            String aiSpecText = rs.getString("ai_spec_text");
            String name = rs.getString("name");
            String dirName = rs.getString("directory_name");

            // Access check: system and shared are open, private only to owner
            if ("private".equals(moduleType)) {
                if (ownerId == null || ownerId != user.getId()) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.setContentType("text/plain; charset=UTF-8");
                    resp.getWriter().write("Access denied");
                    return;
                }
            }

            if (aiSpecText == null || aiSpecText.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setContentType("text/plain; charset=UTF-8");
                resp.getWriter().write("No AI spec available for this module");
                return;
            }

            // Build markdown output
            StringBuilder md = new StringBuilder();
            md.append("# AI-specifikation: ").append(name).append("\n\n");
            md.append("> Modul-ID: `").append(dirName).append("`\n\n");
            md.append("---\n\n");
            md.append(aiSpecText.trim()).append("\n");

            resp.setContentType("text/markdown; charset=UTF-8");

            if (download) {
                String filename = dirName + "-spec.md";
                resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            }

            PrintWriter out = resp.getWriter();
            out.write(md.toString());
            out.flush();

        } catch (SQLException e) {
            log.error("Failed to load module spec for moduleId={}", moduleId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write("Database error");
        }
    }
}
