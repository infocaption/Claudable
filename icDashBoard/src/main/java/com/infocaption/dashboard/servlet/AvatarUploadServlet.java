package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

/**
 * Avatar upload servlet.
 *
 * POST /api/user/avatar  — upload profile picture (multipart, max 2MB)
 * DELETE /api/user/avatar — remove profile picture
 */
@MultipartConfig(maxFileSize = 2 * 1024 * 1024, maxRequestSize = 2 * 1024 * 1024)
public class AvatarUploadServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final String UPLOAD_DIR = "uploads/avatars";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        Part filePart;
        try {
            filePart = req.getPart("avatar");
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Ingen fil bifogad\"}");
            return;
        }

        if (filePart == null || filePart.getSize() == 0) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Ingen fil bifogad\"}");
            return;
        }

        // Validate content type
        String contentType = filePart.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Otill\\u00e5ten filtyp. Anv\\u00e4nd JPG, PNG, GIF eller WebP.\"}");
            return;
        }

        // Validate file size (already enforced by @MultipartConfig but double-check)
        if (filePart.getSize() > 2 * 1024 * 1024) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Bilden f\\u00e5r vara max 2 MB\"}");
            return;
        }

        // Build file name: avatar_{userId}.{ext}
        String ext = "jpg";
        if ("image/png".equals(contentType)) ext = "png";
        else if ("image/gif".equals(contentType)) ext = "gif";
        else if ("image/webp".equals(contentType)) ext = "webp";
        String fileName = "avatar_" + user.getId() + "." + ext;

        // Resolve upload directory
        String appPath = getServletContext().getRealPath("/");
        Path uploadPath = Path.of(appPath, UPLOAD_DIR);
        Files.createDirectories(uploadPath);

        // Delete old avatars for this user (different extension maybe)
        deleteOldAvatars(uploadPath, user.getId());

        // Save file
        Path filePath = uploadPath.resolve(fileName);
        try (InputStream in = filePart.getInputStream()) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Build URL relative to context
        String relativeUrl = UPLOAD_DIR + "/" + fileName + "?t=" + System.currentTimeMillis();

        // Update DB
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET profile_picture_url = ? WHERE id = ?")) {
            ps.setString(1, relativeUrl);
            ps.setInt(2, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Databasfel\"}");
            return;
        }

        // Update session
        user.setProfilePictureUrl(relativeUrl);
        req.getSession().setAttribute("user", user);

        resp.getWriter().write("{\"success\":true,\"url\":" + JsonUtil.quote(relativeUrl) + "}");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");

        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        // Delete file
        String appPath = getServletContext().getRealPath("/");
        Path uploadPath = Path.of(appPath, UPLOAD_DIR);
        deleteOldAvatars(uploadPath, user.getId());

        // Clear DB
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET profile_picture_url = NULL WHERE id = ?")) {
            ps.setInt(1, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Databasfel\"}");
            return;
        }

        // Update session
        user.setProfilePictureUrl(null);
        req.getSession().setAttribute("user", user);

        resp.getWriter().write("{\"success\":true}");
    }

    private void deleteOldAvatars(Path uploadDir, int userId) {
        String prefix = "avatar_" + userId + ".";
        File dir = uploadDir.toFile();
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(prefix)) {
                f.delete();
            }
        }
    }
}
