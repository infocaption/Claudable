package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.Group;
import com.infocaption.dashboard.model.Module;
import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.GroupUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.http.Part;

/**
 * GET /module/manage          -> Show manage-modules.jsp (user's modules + system modules + groups)
 * POST /module/manage         -> Handle actions (toggle-visibility, update, update-groups, delete)
 */
public class ModuleManageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ModuleManageServlet.class);

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".html", ".css", ".js", ".json", ".png", ".jpg", ".jpeg", ".gif", ".svg",
        ".ico", ".woff", ".woff2", ".ttf", ".eot", ".map", ".txt", ".md"
    ));
    private static final int MAX_ZIP_FILES = 500;
    private static final long MAX_ZIP_TOTAL_SIZE = 100 * 1024 * 1024; // 100 MB uncompressed

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (User) session.getAttribute("user");
        int userId = user.getId();

        List<Module> userModules = new ArrayList<>();
        List<Module> systemModules = new ArrayList<>();

        String sql = "SELECT id, owner_user_id, module_type, name, icon, description, " +
                     "category, entry_file, directory_name, badge, version, ai_spec_text, is_active, " +
                     "created_at, updated_at " +
                     "FROM modules ORDER BY module_type, name";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // Load module-group mappings
            Map<Integer, List<Integer>> moduleGroupIds = loadModuleGroupIds(conn);
            Map<Integer, List<String>> moduleGroupNames = loadModuleGroupNames(conn);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Module m = mapModule(rs);
                    // Attach group info as request-scoped data
                    if ("system".equals(m.getModuleType())) {
                        systemModules.add(m);
                    } else if (m.getOwnerUserId() != null && m.getOwnerUserId() == userId) {
                        userModules.add(m);
                    }
                }
            }

            // Load all groups for the group selector
            List<Group> allGroups = loadAllGroups(conn);
            req.setAttribute("allGroups", allGroups);
            req.setAttribute("moduleGroupIds", moduleGroupIds);
            req.setAttribute("moduleGroupNames", moduleGroupNames);

        } catch (SQLException e) {
            log.error("Failed to load modules for management view", e);
            req.setAttribute("error", "Kunde inte h\u00e4mta moduler.");
        }

        req.setAttribute("userModules", userModules);
        req.setAttribute("systemModules", systemModules);
        req.getRequestDispatcher("/manage-modules.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (User) session.getAttribute("user");
        int userId = user.getId();

        String action = req.getParameter("action");
        String moduleIdStr = req.getParameter("moduleId");

        if (action == null || moduleIdStr == null) {
            resp.sendRedirect(req.getContextPath() + "/module/manage?error=missing_params");
            return;
        }

        int moduleId;
        try {
            moduleId = Integer.parseInt(moduleIdStr);
        } catch (NumberFormatException e) {
            resp.sendRedirect(req.getContextPath() + "/module/manage?error=invalid_id");
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            // Verify ownership
            Module module = getModuleById(conn, moduleId);
            if (module == null) {
                resp.sendRedirect(req.getContextPath() + "/module/manage?error=not_found");
                return;
            }

            if ("system".equals(module.getModuleType())) {
                resp.sendRedirect(req.getContextPath() + "/module/manage?error=system_readonly");
                return;
            }

            if (module.getOwnerUserId() == null || module.getOwnerUserId() != userId) {
                resp.sendRedirect(req.getContextPath() + "/module/manage?error=not_owner");
                return;
            }

            switch (action) {
                case "toggle-visibility":
                    toggleVisibility(conn, module);
                    resp.sendRedirect(req.getContextPath() + "/module/manage?success=visibility");
                    break;

                case "update":
                    updateModule(conn, req, module);
                    resp.sendRedirect(req.getContextPath() + "/module/manage?success=updated");
                    break;

                case "update-file":
                    updateModuleFile(conn, req, module);
                    resp.sendRedirect(req.getContextPath() + "/module/manage?success=file_updated");
                    break;

                case "update-groups":
                    updateModuleGroups(conn, req, module);
                    resp.sendRedirect(req.getContextPath() + "/module/manage?success=groups_updated");
                    break;

                case "delete":
                    deleteModule(conn, module);
                    resp.sendRedirect(req.getContextPath() + "/module/manage?success=deleted");
                    break;

                default:
                    resp.sendRedirect(req.getContextPath() + "/module/manage?error=unknown_action");
            }
        } catch (SQLException e) {
            log.error("Failed to process module management action '{}' for moduleId={}", action, moduleIdStr, e);
            resp.sendRedirect(req.getContextPath() + "/module/manage?error=db_error");
        }
    }

    private void toggleVisibility(Connection conn, Module module) throws SQLException {
        String newType = "shared".equals(module.getModuleType()) ? "private" : "shared";
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE modules SET module_type = ? WHERE id = ?")) {
            ps.setString(1, newType);
            ps.setInt(2, module.getId());
            ps.executeUpdate();
        }

        // When switching to shared, auto-assign "Alla" group if no groups exist
        if ("shared".equals(newType)) {
            String checkSql = "SELECT COUNT(*) FROM module_groups WHERE module_id = ?";
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setInt(1, module.getId());
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        int allaId = GroupUtil.getAllaGroupId(conn);
                        if (allaId > 0) {
                            try (PreparedStatement insertPs = conn.prepareStatement(
                                    "INSERT INTO module_groups (module_id, group_id) VALUES (?, ?)")) {
                                insertPs.setInt(1, module.getId());
                                insertPs.setInt(2, allaId);
                                insertPs.executeUpdate();
                            }
                        }
                    }
                }
            }
        }

        // When switching to private, remove all group assignments
        if ("private".equals(newType)) {
            try (PreparedStatement delPs = conn.prepareStatement(
                    "DELETE FROM module_groups WHERE module_id = ?")) {
                delPs.setInt(1, module.getId());
                delPs.executeUpdate();
            }
        }
    }

    private void updateModule(Connection conn, HttpServletRequest req, Module module) throws SQLException {
        String name = req.getParameter("name");
        String icon = req.getParameter("icon");
        String description = req.getParameter("description");
        String category = req.getParameter("category");
        String aiSpecText = req.getParameter("aiSpecText");

        if (name == null || name.trim().isEmpty()) name = module.getName();
        if (icon == null || icon.trim().isEmpty()) icon = module.getIcon();
        if (category == null || category.trim().isEmpty()) category = module.getCategory();

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE modules SET name = ?, icon = ?, description = ?, category = ?, ai_spec_text = ? WHERE id = ?")) {
            ps.setString(1, name.trim());
            ps.setString(2, icon.trim());
            ps.setString(3, description != null ? description.trim() : null);
            ps.setString(4, category);
            ps.setString(5, aiSpecText != null && !aiSpecText.trim().isEmpty() ? aiSpecText.trim() : null);
            ps.setInt(6, module.getId());
            ps.executeUpdate();
        }
    }

    private void updateModuleGroups(Connection conn, HttpServletRequest req, Module module) throws SQLException {
        String[] groupIdStrs = req.getParameterValues("groupIds");

        // Clear existing group assignments
        try (PreparedStatement delPs = conn.prepareStatement(
                "DELETE FROM module_groups WHERE module_id = ?")) {
            delPs.setInt(1, module.getId());
            delPs.executeUpdate();
        }

        // Insert new group assignments
        if (groupIdStrs != null && groupIdStrs.length > 0) {
            String insertSql = "INSERT INTO module_groups (module_id, group_id) VALUES (?, ?)";
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                for (String idStr : groupIdStrs) {
                    try {
                        int groupId = Integer.parseInt(idStr.trim());
                        insertPs.setInt(1, module.getId());
                        insertPs.setInt(2, groupId);
                        insertPs.addBatch();
                    } catch (NumberFormatException ignored) {}
                }
                insertPs.executeBatch();
            }
        } else if ("shared".equals(module.getModuleType())) {
            // If shared module with no groups selected, default to "Alla"
            int allaId = GroupUtil.getAllaGroupId(conn);
            if (allaId > 0) {
                try (PreparedStatement insertPs = conn.prepareStatement(
                        "INSERT INTO module_groups (module_id, group_id) VALUES (?, ?)")) {
                    insertPs.setInt(1, module.getId());
                    insertPs.setInt(2, allaId);
                    insertPs.executeUpdate();
                }
            }
        }
    }

    /**
     * Replace the module's files with a newly uploaded HTML or ZIP file.
     * Deletes old files, extracts new ones, and updates entry_file in DB.
     */
    private void updateModuleFile(Connection conn, HttpServletRequest req, Module module)
            throws SQLException, IOException, ServletException {

        Part filePart = req.getPart("moduleFile");
        if (filePart == null || filePart.getSize() == 0) {
            throw new IOException("Ingen fil bifogad.");
        }

        String fileName = getFileName(filePart);
        if (fileName == null) {
            throw new IOException("Ogiltig fil.");
        }

        boolean isZip = fileName.toLowerCase().endsWith(".zip");
        boolean isHtml = fileName.toLowerCase().endsWith(".html") || fileName.toLowerCase().endsWith(".htm");

        if (!isZip && !isHtml) {
            throw new IOException("Endast .html eller .zip-filer accepteras.");
        }

        // Get existing module directory
        String modulesRoot = getServletContext().getRealPath("/modules");
        Path moduleDir = Paths.get(modulesRoot, module.getDirectoryName());

        // Delete old files but keep the directory
        if (moduleDir.toFile().exists()) {
            File[] oldFiles = moduleDir.toFile().listFiles();
            if (oldFiles != null) {
                for (File f : oldFiles) {
                    deleteDirectory(f);
                }
            }
        } else {
            Files.createDirectories(moduleDir);
        }

        String entryFile;

        if (isHtml) {
            entryFile = fileName;
            Path targetFile = moduleDir.resolve(fileName);
            try (InputStream is = filePart.getInputStream()) {
                Files.copy(is, targetFile);
            }
        } else {
            entryFile = extractZip(filePart.getInputStream(), moduleDir);
            if (entryFile == null) {
                throw new IOException("ZIP-filen m\u00e5ste inneh\u00e5lla minst en HTML-fil.");
            }
        }

        // Update entry_file in database
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE modules SET entry_file = ?, updated_at = NOW() WHERE id = ?")) {
            ps.setString(1, entryFile);
            ps.setInt(2, module.getId());
            ps.executeUpdate();
        }

        log.info("Module '{}' (id={}) files updated by user, new entry_file='{}'",
                module.getName(), module.getId(), entryFile);
    }

    /**
     * Extract filename from Content-Disposition header.
     */
    private String getFileName(Part part) {
        String disposition = part.getHeader("content-disposition");
        if (disposition != null) {
            for (String token : disposition.split(";")) {
                if (token.trim().startsWith("filename")) {
                    String name = token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                    int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                    if (lastSep >= 0) name = name.substring(lastSep + 1);
                    return name.isEmpty() ? null : name;
                }
            }
        }
        return null;
    }

    /**
     * Get file extension including the dot.
     */
    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    /**
     * Extract a ZIP file to the target directory.
     * Returns the entry file name (first .html found), or null if no HTML found.
     */
    private String extractZip(InputStream zipStream, Path targetDir) throws IOException {
        String entryFile = null;
        int fileCount = 0;
        long totalSize = 0;

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    continue;
                }
                if (name.startsWith("__MACOSX") || name.startsWith(".")) {
                    continue;
                }

                String ext = getExtension(name);
                if (!entry.isDirectory() && !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
                    continue;
                }

                Path targetPath = targetDir.resolve(name).normalize();
                if (!targetPath.startsWith(targetDir)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    fileCount++;
                    if (fileCount > MAX_ZIP_FILES) {
                        throw new IOException("ZIP contains too many files (max " + MAX_ZIP_FILES + ")");
                    }
                    Files.createDirectories(targetPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            totalSize += len;
                            if (totalSize > MAX_ZIP_TOTAL_SIZE) {
                                throw new IOException("ZIP uncompressed size exceeds limit (max 100 MB)");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }

                    if (entryFile == null && (name.endsWith(".html") || name.endsWith(".htm"))) {
                        entryFile = name;
                    }
                    if (name.equalsIgnoreCase("index.html") || name.endsWith("/index.html")) {
                        entryFile = name;
                    }
                }
                zis.closeEntry();
            }
        }
        return entryFile;
    }

    private void deleteModule(Connection conn, Module module) throws SQLException {
        // module_groups entries are auto-deleted via ON DELETE CASCADE
        // Delete DB record
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM modules WHERE id = ?")) {
            ps.setInt(1, module.getId());
            ps.executeUpdate();
        }

        // Delete files
        String modulesRoot = getServletContext().getRealPath("/modules");
        File moduleDir = new File(modulesRoot, module.getDirectoryName());
        if (moduleDir.exists()) {
            deleteDirectory(moduleDir);
        }
    }

    private Module getModuleById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, owner_user_id, module_type, name, icon, description, " +
                "category, entry_file, directory_name, badge, version, ai_spec_text, is_active, " +
                "created_at, updated_at FROM modules WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapModule(rs);
                }
                return null;
            }
        }
    }

    /**
     * Load all groups.
     */
    private List<Group> loadAllGroups(Connection conn) throws SQLException {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name, g.icon, g.description, g.is_hidden, " +
                     "(SELECT COUNT(*) FROM user_groups ug WHERE ug.group_id = g.id) as member_count " +
                     "FROM `groups` g ORDER BY g.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = new Group();
                    g.setId(rs.getInt("id"));
                    g.setName(rs.getString("name"));
                    g.setIcon(rs.getString("icon"));
                    g.setDescription(rs.getString("description"));
                    g.setHidden(rs.getBoolean("is_hidden"));
                    g.setMemberCount(rs.getInt("member_count"));
                    groups.add(g);
                }
            }
        }
        return groups;
    }

    /**
     * Load module_id -> list of group IDs mapping.
     */
    private Map<Integer, List<Integer>> loadModuleGroupIds(Connection conn) throws SQLException {
        Map<Integer, List<Integer>> map = new HashMap<>();
        String sql = "SELECT module_id, group_id FROM module_groups";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int moduleId = rs.getInt("module_id");
                    int groupId = rs.getInt("group_id");
                    map.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(groupId);
                }
            }
        }
        return map;
    }

    /**
     * Load module_id -> list of group names mapping.
     */
    private Map<Integer, List<String>> loadModuleGroupNames(Connection conn) throws SQLException {
        Map<Integer, List<String>> map = new HashMap<>();
        String sql = "SELECT mg.module_id, g.name FROM module_groups mg " +
                     "JOIN `groups` g ON mg.group_id = g.id ORDER BY g.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int moduleId = rs.getInt("module_id");
                    String name = rs.getString("name");
                    map.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(name);
                }
            }
        }
        return map;
    }

    private Module mapModule(ResultSet rs) throws SQLException {
        Module m = new Module();
        m.setId(rs.getInt("id"));
        Integer ownerId = rs.getObject("owner_user_id") != null ? rs.getInt("owner_user_id") : null;
        m.setOwnerUserId(ownerId);
        m.setModuleType(rs.getString("module_type"));
        m.setName(rs.getString("name"));
        m.setIcon(rs.getString("icon"));
        m.setDescription(rs.getString("description"));
        m.setCategory(rs.getString("category"));
        m.setEntryFile(rs.getString("entry_file"));
        m.setDirectoryName(rs.getString("directory_name"));
        m.setBadge(rs.getString("badge"));
        m.setVersion(rs.getString("version"));
        m.setAiSpecText(rs.getString("ai_spec_text"));
        m.setActive(rs.getBoolean("is_active"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        m.setUpdatedAt(rs.getTimestamp("updated_at"));
        return m;
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        dir.delete();
    }
}
