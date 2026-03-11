package com.infocaption.dashboard.util;

/**
 * HTML/XML escaping utility for JSP pages and servlets.
 * <p>
 * Provides a short, convenient static method {@code Esc.h()} for use in JSP scriptlets
 * as a replacement for JSTL's {@code fn:escapeXml()}, avoiding the need for the JSTL JAR.
 * <p>
 * Usage in JSP:
 * <pre>
 * &lt;%@ page import="com.infocaption.dashboard.util.Esc" %&gt;
 * &lt;div&gt;&lt;%= Esc.h(someString) %&gt;&lt;/div&gt;
 * &lt;input value="&lt;%= Esc.h(someValue) %&gt;"&gt;
 * </pre>
 */
public final class Esc {

    private Esc() {}

    /**
     * Escape a string for safe inclusion in HTML/XML content and attributes.
     * Handles the 5 XML special characters: &amp; &lt; &gt; &quot; &#x27;
     *
     * @param input the string to escape (may be null)
     * @return the escaped string, or empty string if input is null
     */
    public static String h(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#x27;"); break;
                default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }
}
