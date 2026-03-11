package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge Base utility — handles MCP tool operations for KB collections.
 *
 * Each active KB collection exposes 3 MCP tools:
 *   {prefix}__search_documents(query, [tags])
 *   {prefix}__get_document(slug)
 *   {prefix}__list_documents([tag])
 *
 * Called by McpClientManager when routing tool calls with KB prefixes.
 */
public class KnowledgeBaseUtil {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseUtil.class);

    // ==================== MCP Integration ====================

    /**
     * Check if a tool prefix belongs to a KB collection.
     */
    public static boolean isKbPrefix(String prefix) {
        if (!"true".equalsIgnoreCase(AppConfig.get("kb.enabled", "true"))) return false;

        String sql = "SELECT COUNT(*) FROM kb_collections WHERE tool_prefix = ? AND is_active = 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.warn("isKbPrefix check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get tool definitions for all active KB collections.
     * Returns a JSON array string of MCP tool objects.
     */
    public static String getCollectionTools() {
        if (!"true".equalsIgnoreCase(AppConfig.get("kb.enabled", "true"))) return "[]";

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        String sql = "SELECT tool_prefix, name, description FROM kb_collections WHERE is_active = 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String prefix = rs.getString("tool_prefix");
                String name = rs.getString("name");
                String desc = rs.getString("description");
                if (desc == null) desc = name;

                // 3 tools per collection
                String[][] tools = {
                    {
                        prefix + "__search_documents",
                        "Search documents in " + name + ". " + desc,
                        "{\"type\":\"object\",\"properties\":{" +
                            "\"query\":{\"type\":\"string\",\"description\":\"Search query\"}," +
                            "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Optional tags to filter by\"}" +
                        "},\"required\":[\"query\"]}"
                    },
                    {
                        prefix + "__get_document",
                        "Get the full content of a specific document from " + name,
                        "{\"type\":\"object\",\"properties\":{" +
                            "\"slug\":{\"type\":\"string\",\"description\":\"Document slug identifier\"}" +
                        "},\"required\":[\"slug\"]}"
                    },
                    {
                        prefix + "__list_documents",
                        "List all available documents in " + name,
                        "{\"type\":\"object\",\"properties\":{" +
                            "\"tag\":{\"type\":\"string\",\"description\":\"Optional tag to filter by\"}" +
                        "}}"
                    }
                };

                for (String[] tool : tools) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"name\":").append(JsonUtil.quote(tool[0]));
                    sb.append(",\"description\":").append(JsonUtil.quote(tool[1]));
                    sb.append(",\"inputSchema\":").append(tool[2]);
                    sb.append("}");
                }
            }

        } catch (SQLException e) {
            log.error("Failed to get KB collection tools: {}", e.getMessage());
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Handle a tool call routed to a KB collection.
     *
     * @param prefix    The collection's tool_prefix
     * @param toolName  The tool name (without prefix), e.g., "search_documents"
     * @param arguments Raw JSON arguments string
     * @return MCP-formatted result JSON: {content: [{type: "text", text: "..."}]}
     */
    public static String handleToolCall(String prefix, String toolName, String arguments) {
        try {
            // Look up collection ID
            int collectionId = getCollectionIdByPrefix(prefix);
            if (collectionId < 0) {
                return mcpError("Collection not found: " + prefix);
            }

            switch (toolName) {
                case "search_documents":
                    String query = JsonUtil.extractJsonString(arguments, "query");
                    if (query == null || query.trim().isEmpty()) {
                        return mcpError("Missing required parameter: query");
                    }
                    String tagsJson = JsonUtil.extractJsonArray(arguments, "tags");
                    return searchDocuments(collectionId, query.trim(), tagsJson);

                case "get_document":
                    String slug = JsonUtil.extractJsonString(arguments, "slug");
                    if (slug == null || slug.trim().isEmpty()) {
                        return mcpError("Missing required parameter: slug");
                    }
                    return getDocument(collectionId, slug.trim());

                case "list_documents":
                    String tag = (arguments != null) ? JsonUtil.extractJsonString(arguments, "tag") : null;
                    return listDocuments(collectionId, tag);

                default:
                    return mcpError("Unknown tool: " + toolName);
            }

        } catch (Exception e) {
            log.error("KB tool call failed: prefix={}, tool={}, error={}", prefix, toolName, e.getMessage());
            return mcpError("Internal error: " + e.getMessage());
        }
    }

    // ==================== Tool implementations ====================

    private static String searchDocuments(int collectionId, String query, String tagsJson) throws SQLException {
        // Build search query with fulltext
        StringBuilder sql = new StringBuilder(
            "SELECT d.slug, d.title, d.tags, d.updated_at, " +
            "MATCH(d.title, d.content) AGAINST(? IN BOOLEAN MODE) AS relevance " +
            "FROM kb_documents d " +
            "JOIN kb_collection_documents cd ON d.id = cd.document_id " +
            "WHERE cd.collection_id = ? AND MATCH(d.title, d.content) AGAINST(? IN BOOLEAN MODE)"
        );

        // Parse tags for filtering
        List<String> tags = parseTags(tagsJson);
        if (!tags.isEmpty()) {
            for (String t : tags) {
                sql.append(" AND d.tags LIKE ?");
            }
        }
        sql.append(" ORDER BY relevance DESC LIMIT 20");

        StringBuilder result = new StringBuilder();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, query);
            ps.setInt(idx++, collectionId);
            ps.setString(idx++, query);
            for (String t : tags) {
                ps.setString(idx++, "%" + t + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                result.append("# Search results for: ").append(query).append("\n\n");
                int count = 0;
                while (rs.next()) {
                    count++;
                    result.append("- **").append(rs.getString("title")).append("**");
                    result.append(" (`").append(rs.getString("slug")).append("`)");
                    String docTags = rs.getString("tags");
                    if (docTags != null && !docTags.isEmpty()) {
                        result.append(" ").append(docTags);
                    }
                    result.append("\n");
                }
                if (count == 0) {
                    result.append("No documents found matching the query.");
                } else {
                    result.append("\n").append(count).append(" document(s) found. Use `get_document` with a slug to read the full content.");
                }
            }
        }

        return mcpText(result.toString());
    }

    private static String getDocument(int collectionId, String slug) throws SQLException {
        String sql =
            "SELECT d.title, d.slug, d.content, d.tags, d.file_type, d.updated_at " +
            "FROM kb_documents d " +
            "JOIN kb_collection_documents cd ON d.id = cd.document_id " +
            "WHERE cd.collection_id = ? AND d.slug = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, collectionId);
            ps.setString(2, slug);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return mcpError("Document not found: " + slug);
                }

                StringBuilder result = new StringBuilder();
                result.append("# ").append(rs.getString("title")).append("\n\n");

                String tags = rs.getString("tags");
                if (tags != null && !tags.isEmpty()) {
                    result.append("**Tags:** ").append(tags).append("\n");
                }
                result.append("**Updated:** ").append(rs.getTimestamp("updated_at")).append("\n\n");
                result.append("---\n\n");

                String content = rs.getString("content");
                if (content != null) {
                    result.append(content);
                }

                return mcpText(result.toString());
            }
        }
    }

    private static String listDocuments(int collectionId, String tag) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT d.slug, d.title, d.tags, d.file_type, d.updated_at " +
            "FROM kb_documents d " +
            "JOIN kb_collection_documents cd ON d.id = cd.document_id " +
            "WHERE cd.collection_id = ?"
        );
        if (tag != null && !tag.isEmpty()) {
            sql.append(" AND d.tags LIKE ?");
        }
        sql.append(" ORDER BY d.title");

        StringBuilder result = new StringBuilder();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, collectionId);
            if (tag != null && !tag.isEmpty()) {
                ps.setString(2, "%" + tag + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                result.append("# Documents\n\n");
                result.append("| Title | Slug | Tags | Updated |\n");
                result.append("|-------|------|------|---------|\n");
                int count = 0;
                while (rs.next()) {
                    count++;
                    result.append("| ").append(rs.getString("title"));
                    result.append(" | `").append(rs.getString("slug")).append("`");
                    String docTags = rs.getString("tags");
                    result.append(" | ").append(docTags != null ? docTags : "");
                    result.append(" | ").append(rs.getTimestamp("updated_at"));
                    result.append(" |\n");
                }
                if (count == 0) {
                    result.setLength(0);
                    result.append("No documents in this collection.");
                } else {
                    result.append("\n").append(count).append(" document(s). Use `get_document` with a slug to read the full content.");
                }
            }
        }

        return mcpText(result.toString());
    }

    // ==================== Helpers ====================

    private static int getCollectionIdByPrefix(String prefix) throws SQLException {
        String sql = "SELECT id FROM kb_collections WHERE tool_prefix = ? AND is_active = 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : -1;
            }
        }
    }

    private static List<String> parseTags(String tagsJson) {
        List<String> tags = new ArrayList<>();
        if (tagsJson == null || tagsJson.isEmpty()) return tags;
        // Simple parsing: extract strings from JSON array ["tag1","tag2"]
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(tagsJson);
        while (m.find()) {
            tags.add(m.group(1));
        }
        return tags;
    }

    /**
     * Format a text result in MCP content format.
     */
    private static String mcpText(String text) {
        return "{\"content\":[{\"type\":\"text\",\"text\":" + JsonUtil.quote(text) + "}]}";
    }

    /**
     * Format an error in MCP content format.
     */
    private static String mcpError(String message) {
        return "{\"content\":[{\"type\":\"text\",\"text\":" + JsonUtil.quote("Error: " + message) + "}],\"isError\":true}";
    }
}
