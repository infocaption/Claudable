package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.model.User;
import com.infocaption.dashboard.util.AdminUtil;
import com.infocaption.dashboard.util.DBUtil;
import com.infocaption.dashboard.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Admin-only servlet for managing module files.
 * Provides file browsing, editing, upload, download, and metadata management.
 *
 * Endpoints:
 *   GET  /api/admin/modules              — List all modules
 *   GET  /api/admin/modules/files        — List files in a module directory
 *   GET  /api/admin/modules/file         — Read text file or download binary
 *   GET  /api/admin/modules/download     — Download module as ZIP
 *   GET  /api/admin/modules/spec         — Download ai_spec_text as .md
 *   POST /api/admin/modules/file         — Upload file (multipart)
 *   POST /api/admin/modules/create-file  — Create new empty file
 *   PUT  /api/admin/modules/file         — Save text file content
 *   PUT  /api/admin/modules/metadata     — Update module metadata
 *   DELETE /api/admin/modules/file       — Delete a file
 */
public class ModuleFileApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".html", ".htm", ".css", ".js", ".json", ".png", ".jpg", ".jpeg", ".gif", ".svg",
        ".ico", ".woff", ".woff2", ".ttf", ".eot", ".map", ".txt", ".md"
    ));

    /** Extensions that require sanitization or forced download due to XSS/XXE risk */
    private static final Set<String> DANGEROUS_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".svg", ".xml"
    ));

    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".html", ".htm", ".css", ".js", ".json", ".txt", ".md", ".map", ".svg", ".xml"
    ));

    private static final int MAX_DIR_DEPTH = 5;
    private static final long MAX_TEXT_FILE_SIZE = 2 * 1024 * 1024; // 2 MB for text read

    // ─── GET ──────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            if (pathInfo.equals("") || pathInfo.equals("/")) {
                handleListModules(req, resp);
            } else if (pathInfo.equals("/files")) {
                handleListFiles(req, resp);
            } else if (pathInfo.equals("/file")) {
                handleReadFile(req, resp);
            } else if (pathInfo.equals("/download")) {
                handleDownloadZip(req, resp);
            } else if (pathInfo.equals("/spec")) {
                handleDownloadSpec(req, resp);
            } else {
                resp.setStatus(404);
                writeJson(resp, "{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            resp.setStatus(500);
            writeJson(resp, "{\"error\":" + JsonUtil.quote(e.getMessage()) + "}");
        }
    }

    // ─── POST ─────────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            if (pathInfo.equals("/file")) {
                handleUploadFile(req, resp);
            } else if (pathInfo.equals("/create-file")) {
                handleCreateFile(req, resp);
            } else {
                resp.setStatus(404);
                writeJson(resp, "{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            resp.setStatus(500);
            writeJson(resp, "{\"error\":" + JsonUtil.quote(e.getMessage()) + "}");
        }
    }

    // ─── PUT ──────────────────────────────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            if (pathInfo.equals("/file")) {
                handleUpdateFile(req, resp);
            } else if (pathInfo.equals("/metadata")) {
                handleUpdateMetadata(req, resp);
            } else {
                resp.setStatus(404);
                writeJson(resp, "{\"error\":\"Not found\"}");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            resp.setStatus(500);
            writeJson(resp, "{\"error\":" + JsonUtil.quote(e.getClass().getName() + ": " + e.getMessage()) + "}");
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User admin = AdminUtil.requireAdmin(req, resp);
        if (admin == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            if (pathInfo.equals("/file")) {
                handleDeleteFile(req, resp);
            } else {
                resp.setStatus(404);
                writeJson(resp, "{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            resp.setStatus(500);
            writeJson(resp, "{\"error\":" + JsonUtil.quote(e.getMessage()) + "}");
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: List all modules
    // ═════════════════════════════════════════════════════════════════════

    private void handleListModules(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        StringBuilder json = new StringBuilder("[");

        try (Connection conn = DBUtil.getConnection()) {
            // Pre-load module → group names
            Map<Integer, List<String>> groupMap = loadModuleGroupNames(conn);

            String sql = "SELECT m.*, u.full_name AS owner_name FROM modules m "
                       + "LEFT JOIN users u ON m.owner_user_id = u.id "
                       + "ORDER BY FIELD(module_type,'system','shared','private'), m.name";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    int id = rs.getInt("id");
                    json.append("{");
                    json.append("\"id\":").append(id);
                    json.append(",\"name\":").append(JsonUtil.quote(rs.getString("name")));
                    json.append(",\"icon\":").append(JsonUtil.quote(rs.getString("icon")));
                    json.append(",\"description\":").append(JsonUtil.quote(rs.getString("description")));
                    json.append(",\"category\":").append(JsonUtil.quote(rs.getString("category")));
                    json.append(",\"moduleType\":").append(JsonUtil.quote(rs.getString("module_type")));
                    json.append(",\"directoryName\":").append(JsonUtil.quote(rs.getString("directory_name")));
                    json.append(",\"entryFile\":").append(JsonUtil.quote(rs.getString("entry_file")));
                    json.append(",\"badge\":").append(JsonUtil.quote(rs.getString("badge")));
                    json.append(",\"version\":").append(JsonUtil.quote(rs.getString("version")));
                    json.append(",\"isActive\":").append(rs.getBoolean("is_active"));
                    json.append(",\"ownerName\":").append(JsonUtil.quote(rs.getString("owner_name")));
                    String aiSpec = rs.getString("ai_spec_text");
                    json.append(",\"hasAiSpec\":").append(aiSpec != null && !aiSpec.trim().isEmpty());

                    // Groups
                    List<String> groups = groupMap.getOrDefault(id, Collections.emptyList());
                    json.append(",\"groups\":[");
                    for (int i = 0; i < groups.size(); i++) {
                        if (i > 0) json.append(",");
                        json.append(JsonUtil.quote(groups.get(i)));
                    }
                    json.append("]");

                    json.append("}");
                }
            }
        }
        json.append("]");
        writeJson(resp, json.toString());
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: List files in module directory
    // ═════════════════════════════════════════════════════════════════════

    private void handleListFiles(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        int moduleId = parseIntParam(req, "moduleId");
        if (moduleId <= 0) { sendError(resp, 400, "Missing moduleId"); return; }

        String[] moduleInfo = getModuleInfo(moduleId);
        if (moduleInfo == null) { sendError(resp, 404, "Module not found"); return; }

        String dirName = moduleInfo[0];
        String entryFile = moduleInfo[1];

        String modulesRoot = getServletContext().getRealPath("/modules");
        Path moduleDir = Paths.get(modulesRoot, dirName);
        if (!Files.exists(moduleDir) || !Files.isDirectory(moduleDir)) {
            writeJson(resp, "[]");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = listFilesRecursive(moduleDir, moduleDir, entryFile, json, true, 0);
        json.append("]");
        writeJson(resp, json.toString());
    }

    private boolean listFilesRecursive(Path baseDir, Path currentDir, String entryFile,
            StringBuilder json, boolean firstItem, int depth) throws IOException {
        if (depth > MAX_DIR_DEPTH) return firstItem;

        File[] entries = currentDir.toFile().listFiles();
        if (entries == null) return firstItem;

        // Sort: directories first, then files alphabetically
        Arrays.sort(entries, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File entry : entries) {
            // Skip hidden files and __MACOSX
            String name = entry.getName();
            if (name.startsWith(".") || name.equals("__MACOSX")) continue;

            String relativePath = baseDir.relativize(entry.toPath()).toString().replace('\\', '/');
            String ext = getExtension(name).toLowerCase();
            boolean isText = TEXT_EXTENSIONS.contains(ext);

            if (!firstItem) json.append(",");
            firstItem = false;

            json.append("{");
            json.append("\"name\":").append(JsonUtil.quote(name));
            json.append(",\"path\":").append(JsonUtil.quote(relativePath));
            json.append(",\"isDirectory\":").append(entry.isDirectory());
            json.append(",\"size\":").append(entry.isDirectory() ? 0 : entry.length());
            json.append(",\"lastModified\":").append(entry.lastModified());
            json.append(",\"extension\":").append(JsonUtil.quote(ext));
            json.append(",\"isTextFile\":").append(isText);
            json.append(",\"isEntryFile\":").append(relativePath.equals(entryFile));
            json.append("}");

            if (entry.isDirectory()) {
                firstItem = listFilesRecursive(baseDir, entry.toPath(), entryFile, json, firstItem, depth + 1);
            }
        }
        return firstItem;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Read text file / download binary
    // ═════════════════════════════════════════════════════════════════════

    private void handleReadFile(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        int moduleId = parseIntParam(req, "moduleId");
        String filePath = req.getParameter("path");
        boolean download = "true".equals(req.getParameter("download"));

        if (moduleId <= 0) { sendError(resp, 400, "Missing moduleId"); return; }
        if (filePath == null || filePath.isEmpty()) { sendError(resp, 400, "Missing path"); return; }

        String[] moduleInfo = getModuleInfo(moduleId);
        if (moduleInfo == null) { sendError(resp, 404, "Module not found"); return; }

        String modulesRoot = getServletContext().getRealPath("/modules");
        Path resolved = resolveAndValidate(modulesRoot, moduleInfo[0], filePath);
        if (resolved == null) { sendError(resp, 403, "Access denied"); return; }

        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            sendError(resp, 404, "File not found");
            return;
        }

        String ext = getExtension(resolved.getFileName().toString()).toLowerCase();
        boolean isText = TEXT_EXTENSIONS.contains(ext);
        boolean isDangerous = DANGEROUS_EXTENSIONS.contains(ext);

        if (download || !isText || isDangerous) {
            // Binary download (SVG/XML always forced download to prevent XSS/XXE)
            String fileName = sanitizeFileName(resolved.getFileName().toString());
            resp.setContentType(isDangerous ? "application/octet-stream" : getMimeType(ext));
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setContentLengthLong(Files.size(resolved));
            try (InputStream is = Files.newInputStream(resolved);
                 OutputStream os = resp.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            }
        } else {
            // Text file — return as JSON
            long size = Files.size(resolved);
            if (size > MAX_TEXT_FILE_SIZE) {
                sendError(resp, 413, "File too large for editing (" + (size / 1024) + " KB)");
                return;
            }
            String content = new String(Files.readAllBytes(resolved), StandardCharsets.UTF_8);
            StringBuilder json = new StringBuilder("{");
            json.append("\"path\":").append(JsonUtil.quote(filePath));
            json.append(",\"content\":").append(JsonUtil.quote(content));
            json.append(",\"size\":").append(size);
            json.append(",\"lastModified\":").append(resolved.toFile().lastModified());
            json.append("}");
            writeJson(resp, json.toString());
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Download module as ZIP
    // ═════════════════════════════════════════════════════════════════════

    private void handleDownloadZip(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        int moduleId = parseIntParam(req, "moduleId");
        if (moduleId <= 0) { sendError(resp, 400, "Missing moduleId"); return; }

        String[] moduleInfo = getModuleInfo(moduleId);
        if (moduleInfo == null) { sendError(resp, 404, "Module not found"); return; }

        String dirName = moduleInfo[0];
        String modulesRoot = getServletContext().getRealPath("/modules");
        Path moduleDir = Paths.get(modulesRoot, dirName);

        if (!Files.exists(moduleDir) || !Files.isDirectory(moduleDir)) {
            sendError(resp, 404, "Module directory not found");
            return;
        }

        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + sanitizeFileName(dirName) + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(resp.getOutputStream())) {
            Files.walkFileTree(moduleDir, EnumSet.noneOf(FileVisitOption.class), MAX_DIR_DEPTH,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String name = file.getFileName().toString();
                        if (name.startsWith(".") || name.equals("__MACOSX")) return FileVisitResult.CONTINUE;

                        String entryName = moduleDir.relativize(file).toString().replace('\\', '/');
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName().toString();
                        if (name.startsWith(".") || name.equals("__MACOSX")) return FileVisitResult.SKIP_SUBTREE;
                        return FileVisitResult.CONTINUE;
                    }
                });
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Download AI spec as .md
    // ═════════════════════════════════════════════════════════════════════

    private void handleDownloadSpec(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        int moduleId = parseIntParam(req, "moduleId");
        if (moduleId <= 0) { sendError(resp, 400, "Missing moduleId"); return; }

        String aiSpec = null;
        String dirName = null;
        String moduleName = null;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT directory_name, name, ai_spec_text FROM modules WHERE id = ?")) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dirName = rs.getString("directory_name");
                    moduleName = rs.getString("name");
                    aiSpec = rs.getString("ai_spec_text");
                }
            }
        }

        if (dirName == null) { sendError(resp, 404, "Module not found"); return; }
        if (aiSpec == null || aiSpec.trim().isEmpty()) {
            sendError(resp, 404, "No spec available for this module");
            return;
        }

        resp.setContentType("text/markdown; charset=UTF-8");
        resp.setHeader("Content-Disposition",
            "attachment; filename=\"" + sanitizeFileName(dirName) + "-spec.md\"");
        resp.getWriter().write(aiSpec);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Upload file (multipart)
    // ═════════════════════════════════════════════════════════════════════

    private void handleUploadFile(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        String moduleIdStr = getPartValue(req, "moduleId");
        String targetPath = getPartValue(req, "path");
        Part filePart = req.getPart("file");

        if (moduleIdStr == null || moduleIdStr.isEmpty()) {
            sendError(resp, 400, "Missing moduleId"); return;
        }
        int moduleId = Integer.parseInt(moduleIdStr);
        if (targetPath == null) targetPath = "";

        if (filePart == null || filePart.getSize() == 0) {
            sendError(resp, 400, "No file uploaded"); return;
        }

        String fileName = getUploadFileName(filePart);
        if (fileName == null || fileName.isEmpty()) {
            sendError(resp, 400, "Invalid file name"); return;
        }

        String ext = getExtension(fileName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            sendError(resp, 400, "File type not allowed: " + ext);
            return;
        }

        String[] moduleInfo = getModuleInfo(moduleId);
        if (moduleInfo == null) { sendError(resp, 404, "Module not found"); return; }

        String modulesRoot = getServletContext().getRealPath("/modules");
        String combinedPath = targetPath.isEmpty() ? fileName : targetPath + "/" + fileName;
        Path resolved = resolveAndValidate(modulesRoot, moduleInfo[0], combinedPath);
        if (resolved == null) { sendError(resp, 403, "Access denied"); return; }

        // Create parent directories if needed
        Files.createDirectories(resolved.getParent());

        // Write file
        try (InputStream is = filePart.getInputStream();
             OutputStream os = Files.newOutputStream(resolved)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
        }

        StringBuilder json = new StringBuilder("{");
        json.append("\"success\":true");
        json.append(",\"path\":").append(JsonUtil.quote(combinedPath));
        json.append(",\"size\":").append(filePart.getSize());
        json.append("}");
        writeJson(resp, json.toString());
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Create new empty file
    // ═════════════════════════════════════════════════════════════════════

    private void handleCreateFile(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        String body = readBody(req);
        int moduleId = JsonUtil.extractJsonInt(body, "moduleId");
        String filePath = JsonUtil.extractJsonString(body, "path");

        if (moduleId <= 0) { sendError(resp, 400, "Missing moduleId"); return; }
        if (filePath == null || filePath.isEmpty()) {
            sendError(resp, 400, "Missing path"); return;
        }

        String ext = getExtension(filePath).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            sendError(resp, 400, "File type not allowed: " + ext);
            return;
        }

        String[] moduleInfo = getModuleInfo(moduleId);
        if (moduleInfo == null) { sendError(resp, 404, "Module not found"); return; }

        String modulesRoot = getServletContext().getRealPath("/modules");
        Path resolved = resolveAndValidate(modulesRoot, moduleInfo[0], filePath);
        if (resolved == null) { sendError(resp, 403, "Access denied"); return; }

        if (Files.exists(resolved)) {
            sendError(resp, 409, "File already exists");
            return;
        }

        // Create parent directories if needed
        Files.createDirectories(resolved.getParent());
        Files.createFile(resolved);

        writeJson(resp, "{\"success\":true,\"path\":" + JsonUtil.quote(filePath) + "}");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Update text file content
    // ═════════════════════════════════════════════════════════════════════

    private void handleUpdateFile(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        String body = readBody(req);
        int moduleId = JsonUtil.extractJsonInt(body, "moduleId");
        String filePath = JsonUtil.extractJsonString(body, "path");
        // Use streaming parser instead of regex — regex can StackOverflow on large content
        String content = extractLargeJsonString(body, "content");

        if (moduleId <= 0) { sendError(resp, 400, "Missing moduleId"); return; }
        if (filePath == null || filePath.isEmpty()) {
            sendError(resp, 400, "Missing path"); return;
        }
        if (content == null) content = "";

        String ext = getExtension(filePath).toLowerCase();
        if (!TEXT_EXTENSIONS.contains(ext)) {
            sendError(resp, 400, "Not a text file: " + ext);
            return;
        }

        String[] moduleInfo = getModuleInfo(moduleId);
        if (moduleInfo == null) { sendError(resp, 404, "Module not found"); return; }

        String modulesRoot = getServletContext().getRealPath("/modules");
        Path resolved = resolveAndValidate(modulesRoot, moduleInfo[0], filePath);
        if (resolved == null) { sendError(resp, 403, "Access denied"); return; }

        if (!Files.exists(resolved)) {
            sendError(resp, 404, "File not found");
            return;
        }

        Files.write(resolved, content.getBytes(StandardCharsets.UTF_8));
        long size = Files.size(resolved);

        writeJson(resp, "{\"success\":true,\"size\":" + size + "}");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Update module metadata
    // ═════════════════════════════════════════════════════════════════════

    private void handleUpdateMetadata(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        String body = readBody(req);
        int moduleId = JsonUtil.extractJsonInt(body, "id");
        if (moduleId <= 0) { sendError(resp, 400, "Missing id"); return; }

        String name = JsonUtil.extractJsonString(body, "name");
        String icon = JsonUtil.extractJsonString(body, "icon");
        String description = JsonUtil.extractJsonString(body, "description");
        String category = JsonUtil.extractJsonString(body, "category");
        String version = JsonUtil.extractJsonString(body, "version");
        String badge = JsonUtil.extractJsonString(body, "badge");
        String aiSpecText = JsonUtil.extractJsonString(body, "aiSpecText");
        List<Integer> groupIds = extractJsonIntArray(body, "groupIds");

        try (Connection conn = DBUtil.getConnection()) {
            // Build dynamic UPDATE
            List<String> setClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();

            if (name != null) { setClauses.add("name = ?"); params.add(name); }
            if (icon != null) { setClauses.add("icon = ?"); params.add(icon); }
            if (description != null) { setClauses.add("description = ?"); params.add(description); }
            if (category != null) { setClauses.add("category = ?"); params.add(category); }
            if (version != null) { setClauses.add("version = ?"); params.add(version); }
            if (badge != null) { setClauses.add("badge = ?"); params.add(badge); }
            if (aiSpecText != null) { setClauses.add("ai_spec_text = ?"); params.add(aiSpecText); }

            if (!setClauses.isEmpty()) {
                String sql = "UPDATE modules SET " + String.join(", ", setClauses) + " WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setString(i + 1, (String) params.get(i));
                    }
                    ps.setInt(params.size() + 1, moduleId);
                    ps.executeUpdate();
                }
            }

            // Update groups if provided
            if (groupIds != null) {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM module_groups WHERE module_id = ?")) {
                    del.setInt(1, moduleId);
                    del.executeUpdate();
                }
                if (!groupIds.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO module_groups (module_id, group_id) VALUES (?, ?)")) {
                        for (int gid : groupIds) {
                            ins.setInt(1, moduleId);
                            ins.setInt(2, gid);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
            }
        }

        writeJson(resp, "{\"success\":true}");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  HANDLER: Delete file
    // ═════════════════════════════════════════════════════════════════════

    private void handleDeleteFile(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        int moduleId = parseIntParam(req, "moduleId");
        String filePath = req.getParameter("path");

        if (moduleId <= 0) { sendError(resp, 400, "Missing moduleId"); return; }
        if (filePath == null || filePath.isEmpty()) {
            sendError(resp, 400, "Missing path"); return;
        }

        String[] moduleInfo = getModuleInfo(moduleId);
        if (moduleInfo == null) { sendError(resp, 404, "Module not found"); return; }

        String modulesRoot = getServletContext().getRealPath("/modules");
        Path resolved = resolveAndValidate(modulesRoot, moduleInfo[0], filePath);
        if (resolved == null) { sendError(resp, 403, "Access denied"); return; }

        if (!Files.exists(resolved)) {
            sendError(resp, 404, "File not found"); return;
        }

        if (Files.isDirectory(resolved)) {
            sendError(resp, 400, "Cannot delete directories"); return;
        }

        Files.delete(resolved);
        writeJson(resp, "{\"success\":true}");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SECURITY: Path traversal prevention
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Resolve a user-provided file path within a module directory.
     * Returns null if the path is invalid or attempts traversal.
     */
    private Path resolveAndValidate(String modulesRoot, String dirName, String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) return null;
            if (filePath.contains("..")) return null;
            if (filePath.startsWith("/") || filePath.startsWith("\\")) return null;
            if (filePath.contains("\\")) return null; // Reject backslashes

            Path moduleDir = Paths.get(modulesRoot, dirName).toAbsolutePath().normalize();
            Path target = moduleDir.resolve(filePath).toAbsolutePath().normalize();

            // CRITICAL: Must stay within module directory
            if (!target.startsWith(moduleDir)) return null;

            return target;
        } catch (Exception e) {
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DATABASE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Returns [directoryName, entryFile] or null if not found.
     */
    private String[] getModuleInfo(int moduleId) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT directory_name, entry_file FROM modules WHERE id = ?")) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{
                        rs.getString("directory_name"),
                        rs.getString("entry_file")
                    };
                }
            }
        }
        return null;
    }

    /**
     * Pre-load module → group names for all modules.
     */
    private Map<Integer, List<String>> loadModuleGroupNames(Connection conn) throws SQLException {
        Map<Integer, List<String>> map = new HashMap<>();
        String sql = "SELECT mg.module_id, g.name FROM module_groups mg "
                   + "JOIN `groups` g ON mg.group_id = g.id ORDER BY g.name";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int mid = rs.getInt("module_id");
                map.computeIfAbsent(mid, k -> new ArrayList<>()).add(rs.getString("name"));
            }
        }
        return map;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ═════════════════════════════════════════════════════════════════════

    private void writeJson(HttpServletResponse resp, String json) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write(json);
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        writeJson(resp, "{\"error\":" + JsonUtil.quote(message) + "}");
    }

    private int parseIntParam(HttpServletRequest req, String name) {
        String val = req.getParameter(name);
        if (val == null) return 0;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    /** Sanitize filename for Content-Disposition header (prevent header injection). */
    private String sanitizeFileName(String name) {
        if (name == null) return "download";
        return name.replaceAll("[\"\\\\\\r\\n]", "_");
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private String getMimeType(String ext) {
        switch (ext) {
            case ".html": case ".htm": return "text/html; charset=UTF-8";
            case ".css": return "text/css; charset=UTF-8";
            case ".js": return "application/javascript; charset=UTF-8";
            case ".json": return "application/json; charset=UTF-8";
            case ".md": case ".txt": return "text/plain; charset=UTF-8";
            case ".png": return "image/png";
            case ".jpg": case ".jpeg": return "image/jpeg";
            case ".gif": return "image/gif";
            case ".svg": return "image/svg+xml; charset=UTF-8";  // Note: SVGs served with Content-Disposition: attachment for security
            case ".ico": return "image/x-icon";
            case ".woff": return "font/woff";
            case ".woff2": return "font/woff2";
            case ".ttf": return "font/ttf";
            case ".eot": return "application/vnd.ms-fontobject";
            default: return "application/octet-stream";
        }
    }

    /**
     * Read a multipart form field as a string.
     */
    private String getPartValue(HttpServletRequest req, String name) throws IOException, ServletException {
        Part part = req.getPart(name);
        if (part == null) return null;
        try (InputStream is = part.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
    }

    /**
     * Extract filename from multipart Content-Disposition header.
     */
    private String getUploadFileName(Part part) {
        String header = part.getHeader("content-disposition");
        if (header == null) return null;
        for (String token : header.split(";")) {
            token = token.trim();
            if (token.startsWith("filename")) {
                String name = token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                // Handle IE/Edge sending full path
                int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                if (slash >= 0) name = name.substring(slash + 1);
                return name;
            }
        }
        return null;
    }

    // ─── JSON parsing helpers ───

    /**
     * Extract a large JSON string value without regex (avoids StackOverflowError on big strings).
     * Manually walks the JSON to find the key, then extracts the value handling all escapes.
     */
    private static String extractLargeJsonString(String json, String key) {
        if (json == null || key == null) return null;
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx < 0) return null;

        // Skip past the key and colon
        int i = keyIdx + needle.length();
        int len = json.length();
        while (i < len && json.charAt(i) != '"') i++;
        if (i >= len) return null;
        i++; // skip opening quote

        // Parse the JSON string value character by character
        StringBuilder sb = new StringBuilder();
        while (i < len) {
            char c = json.charAt(i);
            if (c == '"') break; // closing quote
            if (c == '\\' && i + 1 < len) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"');  i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                    case 'n':  sb.append('\n'); i += 2; continue;
                    case 'r':  sb.append('\r'); i += 2; continue;
                    case 't':  sb.append('\t'); i += 2; continue;
                    case '/':  sb.append('/');  i += 2; continue;
                    case 'u':
                        if (i + 5 < len) {
                            try {
                                int cp = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 6;
                                continue;
                            } catch (NumberFormatException e) { /* fall through */ }
                        }
                        break;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static List<Integer> extractJsonIntArray(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (!m.find()) return null;
        String content = m.group(1);
        List<Integer> result = new ArrayList<>();
        Matcher numMatcher = Pattern.compile("-?\\d+").matcher(content);
        while (numMatcher.find()) {
            try { result.add(Integer.parseInt(numMatcher.group())); }
            catch (NumberFormatException e) { /* skip */ }
        }
        return result;
    }
}
