package com.infocaption.dashboard.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared JSON utility methods for building and parsing JSON
 * without an external JSON library.
 */
public class JsonUtil {

    /**
     * JSON-escape a string and wrap in double quotes.
     * Returns the literal "null" (unquoted) for null input.
     */
    public static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Unescape a JSON string value (handles \", \\, \n, \r, \t, \b, \f).
     */
    static String unescapeJson(String s) {
        if (s == null || s.indexOf('\\') == -1) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case '/':  sb.append('/');  break;
                    default:   sb.append('\\').append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── JSON parsing helpers (regex-based, no external library) ──

    /**
     * Extract a string value from JSON by key.
     * Returns null if not found.
     */
    public static String extractJsonString(String json, String key) {
        if (json == null || key == null) return null;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    /**
     * Extract an integer value from JSON by key.
     * Returns 0 if not found.
     */
    public static int extractJsonInt(String json, String key) {
        if (json == null || key == null) return 0;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /**
     * Extract a boolean value from JSON by key.
     * Returns false if not found.
     */
    public static boolean extractJsonBoolean(String json, String key) {
        if (json == null || key == null) return false;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() && "true".equals(m.group(1));
    }

    /**
     * Extract a JSON object value (the part between { and }, inclusive).
     * Returns null if not found.
     */
    public static String extractJsonObject(String json, String key) {
        if (json == null || key == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;

        int start = m.end() - 1;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return null;
    }

    /**
     * Extract a JSON array value as raw string (the part between [ and ], inclusive).
     * Returns null if not found.
     */
    public static String extractJsonArray(String json, String key) {
        if (json == null || key == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;

        int start = m.end() - 1;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return null;
    }

    /**
     * Extract a JSON array of strings/integers by key, returning parsed values as List.
     * Handles both quoted strings and unquoted integers.
     * Returns null if key not found, empty list if array is empty.
     */
    public static List<String> extractJsonStringList(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (!m.find()) return null;

        String arrayContent = m.group(1).trim();
        if (arrayContent.isEmpty()) return new ArrayList<>();

        List<String> result = new ArrayList<>();
        Matcher valueMatcher = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(arrayContent);
        while (valueMatcher.find()) {
            result.add(unescapeJson(valueMatcher.group(1)));
        }
        if (result.isEmpty()) {
            Matcher intMatcher = Pattern.compile("(\\d+)").matcher(arrayContent);
            while (intMatcher.find()) {
                result.add(intMatcher.group(1));
            }
        }
        return result;
    }
}
