package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.Group;
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
import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GET /module/create  -> Forward to create-module.jsp (with groups list)
 * POST /module/create -> Process multipart upload, create module + group assignments
 */
public class ModuleCreateServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ModuleCreateServlet.class);

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".html", ".css", ".js", ".json", ".png", ".jpg", ".jpeg", ".gif", ".svg",
        ".ico", ".woff", ".woff2", ".ttf", ".eot", ".map", ".txt", ".md"
    ));

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Load all groups for the group selector in the form
        List<Group> allGroups = loadAllGroups();
        req.setAttribute("allGroups", allGroups);

        req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (User) session.getAttribute("user");

        // Read form fields
        String name = getPartValue(req, "name");
        String icon = getPartValue(req, "icon");
        String description = getPartValue(req, "description");
        String category = getPartValue(req, "category");
        String visibility = getPartValue(req, "visibility");
        String aiSpecText = getPartValue(req, "aiSpecText");
        String groupIdsStr = getPartValue(req, "groupIds");

        // Validate required fields
        if (name == null || name.trim().isEmpty()) {
            req.setAttribute("error", "Modulnamn kr\u00e4vs.");
            req.setAttribute("allGroups", loadAllGroups());
            req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
            return;
        }

        // Default values
        if (icon == null || icon.trim().isEmpty()) icon = "\uD83D\uDCE6"; // 📦
        if (category == null || category.trim().isEmpty()) category = "tools";
        if (visibility == null) visibility = "private";

        String moduleType = "shared".equals(visibility) ? "shared" : "private";

        // Parse group IDs
        List<Integer> groupIds = new ArrayList<>();
        if (groupIdsStr != null && !groupIdsStr.trim().isEmpty()) {
            for (String idStr : groupIdsStr.split(",")) {
                try {
                    groupIds.add(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Get uploaded file
        Part filePart = req.getPart("moduleFile");
        if (filePart == null || filePart.getSize() == 0) {
            req.setAttribute("error", "En HTML-fil eller ZIP-fil kr\u00e4vs.");
            req.setAttribute("name", name);
            req.setAttribute("description", description);
            req.setAttribute("aiSpecText", aiSpecText);
            req.setAttribute("allGroups", loadAllGroups());
            req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
            return;
        }

        String fileName = getFileName(filePart);
        if (fileName == null) {
            req.setAttribute("error", "Ogiltig fil.");
            req.setAttribute("allGroups", loadAllGroups());
            req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
            return;
        }

        boolean isZip = fileName.toLowerCase().endsWith(".zip");
        boolean isHtml = fileName.toLowerCase().endsWith(".html") || fileName.toLowerCase().endsWith(".htm");

        if (!isZip && !isHtml) {
            req.setAttribute("error", "Endast .html eller .zip-filer accepteras.");
            req.setAttribute("name", name);
            req.setAttribute("description", description);
            req.setAttribute("aiSpecText", aiSpecText);
            req.setAttribute("allGroups", loadAllGroups());
            req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
            return;
        }

        // Generate directory name
        String dirName = toKebabCase(name.trim()) + "-" + System.currentTimeMillis();

        // Create module directory
        String modulesRoot = getServletContext().getRealPath("/modules");
        Path moduleDir = Paths.get(modulesRoot, dirName);
        Files.createDirectories(moduleDir);

        String entryFile;

        try {
            if (isHtml) {
                // Single HTML file
                entryFile = fileName;
                Path targetFile = moduleDir.resolve(fileName);
                try (InputStream is = filePart.getInputStream()) {
                    Files.copy(is, targetFile);
                }
            } else {
                // ZIP file - extract with security checks
                entryFile = extractZip(filePart.getInputStream(), moduleDir);
                if (entryFile == null) {
                    // Clean up
                    deleteDirectory(moduleDir.toFile());
                    req.setAttribute("error", "ZIP-filen m\u00e5ste inneh\u00e5lla minst en HTML-fil.");
                    req.setAttribute("name", name);
                    req.setAttribute("description", description);
                    req.setAttribute("aiSpecText", aiSpecText);
                    req.setAttribute("allGroups", loadAllGroups());
                    req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
                    return;
                }
            }

            // Insert into database and get generated ID
            String sql = "INSERT INTO modules (owner_user_id, module_type, name, icon, description, " +
                         "category, entry_file, directory_name, version, ai_spec_text) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, '1.0', ?)";

            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, user.getId());
                ps.setString(2, moduleType);
                ps.setString(3, name.trim());
                ps.setString(4, icon.trim());
                ps.setString(5, description != null ? description.trim() : null);
                ps.setString(6, category);
                ps.setString(7, entryFile);
                ps.setString(8, dirName);
                ps.setString(9, aiSpecText != null && !aiSpecText.trim().isEmpty() ? aiSpecText.trim() : null);

                ps.executeUpdate();

                // Get the generated module ID
                int newModuleId = -1;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        newModuleId = keys.getInt(1);
                    }
                }

                // Assign groups if module is shared
                if ("shared".equals(moduleType) && newModuleId > 0) {
                    if (groupIds.isEmpty()) {
                        // Default: assign to "Alla" group
                        int allaId = GroupUtil.getAllaGroupId(conn);
                        if (allaId > 0) {
                            groupIds.add(allaId);
                        }
                    }
                    assignModuleGroups(conn, newModuleId, groupIds);
                }
            }

            resp.sendRedirect(req.getContextPath() + "/module/manage?success=created");

        } catch (SQLException e) {
            log.error("Failed to create module '{}' in database", name, e);
            // Clean up on DB error
            deleteDirectory(moduleDir.toFile());
            req.setAttribute("error", "Databasfel vid skapande av modul.");
            req.setAttribute("allGroups", loadAllGroups());
            req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
        } catch (Exception e) {
            log.error("Failed to upload module files for '{}'", name, e);
            deleteDirectory(moduleDir.toFile());
            req.setAttribute("error", "Fel vid uppladdning.");
            req.setAttribute("allGroups", loadAllGroups());
            req.getRequestDispatcher("/create-module.jsp").forward(req, resp);
        }
    }

    /**
     * Load all groups for the group selector.
     */
    private List<Group> loadAllGroups() {
        List<Group> groups = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, icon, description, is_hidden FROM `groups` ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = new Group();
                    g.setId(rs.getInt("id"));
                    g.setName(rs.getString("name"));
                    g.setIcon(rs.getString("icon"));
                    g.setDescription(rs.getString("description"));
                    g.setHidden(rs.getBoolean("is_hidden"));
                    groups.add(g);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load groups for module creation form", e);
        }
        return groups;
    }

    /**
     * Insert module-group assignments.
     */
    private void assignModuleGroups(Connection conn, int moduleId, List<Integer> groupIds) throws SQLException {
        String sql = "INSERT INTO module_groups (module_id, group_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE module_id = module_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int groupId : groupIds) {
                ps.setInt(1, moduleId);
                ps.setInt(2, groupId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static final int MAX_ZIP_FILES = 500;
    private static final long MAX_ZIP_TOTAL_SIZE = 100 * 1024 * 1024; // 100 MB uncompressed

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

                // Security: reject path traversal
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    continue;
                }

                // Skip macOS resource fork and hidden files
                if (name.startsWith("__MACOSX") || name.startsWith(".")) {
                    continue;
                }

                // Check extension whitelist
                String ext = getExtension(name);
                if (!entry.isDirectory() && !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
                    continue; // Skip disallowed file types
                }

                Path targetPath = targetDir.resolve(name).normalize();

                // Additional security: ensure target is within module dir
                if (!targetPath.startsWith(targetDir)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    // Zipbomb protection: limit file count
                    fileCount++;
                    if (fileCount > MAX_ZIP_FILES) {
                        throw new IOException("ZIP contains too many files (max " + MAX_ZIP_FILES + ")");
                    }

                    // Ensure parent dirs exist
                    Files.createDirectories(targetPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            totalSize += len;
                            // Zipbomb protection: limit total extracted size
                            if (totalSize > MAX_ZIP_TOTAL_SIZE) {
                                throw new IOException("ZIP uncompressed size exceeds limit (max 100 MB)");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }

                    // Track first HTML file as entry point
                    if (entryFile == null && (name.endsWith(".html") || name.endsWith(".htm"))) {
                        entryFile = name;
                    }

                    // Prefer index.html as entry point
                    if (name.equalsIgnoreCase("index.html") || name.endsWith("/index.html")) {
                        entryFile = name;
                    }
                }

                zis.closeEntry();
            }
        }

        return entryFile;
    }

    /**
     * Get a form field value from a multipart request.
     */
    private String getPartValue(HttpServletRequest req, String name) throws IOException, ServletException {
        Part part = req.getPart(name);
        if (part == null || part.getSize() == 0) return null;
        try (InputStream is = part.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        }
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
                    // Handle full path (IE/Edge legacy)
                    int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                    if (lastSep >= 0) name = name.substring(lastSep + 1);
                    return name.isEmpty() ? null : name;
                }
            }
        }
        return null;
    }

    /**
     * Convert a name to kebab-case.
     */
    private String toKebabCase(String input) {
        return input.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    /**
     * Get file extension including the dot.
     */
    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    /**
     * Recursively delete a directory.
     */
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
