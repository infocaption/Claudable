<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.model.User" %>
<%@ page import="java.util.*" %>
<%
    User user = (User) session.getAttribute("user");
    String ctxPath = request.getContextPath();

    @SuppressWarnings("unchecked")
    List<Map<String, String>> users = (List<Map<String, String>>) request.getAttribute("users");
    @SuppressWarnings("unchecked")
    Map<Integer, List<String>> userGroups = (Map<Integer, List<String>>) request.getAttribute("userGroups");

    if (users == null) users = Collections.emptyList();
    if (userGroups == null) userGroups = Collections.emptyMap();
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Användare - InfoCaption Dashboard</title>
    <link rel="stylesheet" href="<%= ctxPath %>/shared/ic-styles.css">
    <style>
        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #f8f9fc;
            min-height: 100vh;
        }

        .page-header {
            background: #fff;
            border-bottom: 1px solid #e5e7eb;
            padding: 18px 30px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .page-header h1 {
            font-size: 1.3em;
            font-weight: 700;
            color: #323232;
        }

        .header-actions {
            display: flex;
            gap: 12px;
            align-items: center;
        }

        .back-link {
            color: #5458F2;
            text-decoration: none;
            font-size: 0.9em;
            font-weight: 500;
        }

        .back-link:hover { text-decoration: underline; }

        .page-content {
            max-width: 1000px;
            margin: 30px auto;
            padding: 0 20px;
        }

        .section-title {
            font-size: 1.05em;
            font-weight: 600;
            color: #323232;
            margin-bottom: 16px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .section-count {
            background: #D4D5FC;
            color: #5458F2;
            font-size: 0.75em;
            padding: 2px 8px;
            border-radius: 10px;
            font-weight: 600;
        }

        /* Search */
        .search-bar {
            margin-bottom: 20px;
        }

        .search-input {
            width: 100%;
            padding: 12px 18px;
            border: 1.5px solid #e5e7eb;
            border-radius: 10px;
            font-size: 0.9em;
            font-family: inherit;
            background: #fff;
            box-sizing: border-box;
        }

        .search-input:focus {
            outline: none;
            border-color: #5458F2;
        }

        .search-input::placeholder { color: #adb5bd; }

        /* Card grid */
        .module-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 18px;
            margin-bottom: 40px;
        }

        .mod-card {
            background: #fff;
            border-radius: 14px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.06);
            overflow: hidden;
            transition: box-shadow 0.2s;
        }

        .mod-card:hover {
            box-shadow: 0 6px 20px rgba(0,0,0,0.1);
        }

        .mod-card-top {
            padding: 20px 20px 14px;
            display: flex;
            gap: 14px;
            align-items: flex-start;
        }

        .mod-icon {
            width: 48px;
            height: 48px;
            background: #f0f0ff;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.5em;
            flex-shrink: 0;
        }

        .mod-icon img {
            width: 48px;
            height: 48px;
            border-radius: 12px;
            object-fit: cover;
        }

        .mod-info {
            flex: 1;
            min-width: 0;
        }

        .mod-name {
            font-size: 1em;
            font-weight: 600;
            color: #323232;
            margin-bottom: 3px;
        }

        .mod-desc {
            font-size: 0.8em;
            color: #6c757d;
            line-height: 1.4;
        }

        .mod-username {
            font-size: 0.75em;
            color: #adb5bd;
            margin-top: 2px;
        }

        .mod-meta {
            padding: 0 20px 14px;
            display: flex;
            gap: 6px;
            flex-wrap: wrap;
        }

        .mod-badge {
            font-size: 0.65em;
            padding: 2px 8px;
            border-radius: 8px;
            font-weight: 600;
        }

        .mod-badge.group-badge {
            background: #D4D5FC;
            color: #5458F2;
        }

        .mod-badge.login-badge {
            background: #f0f0f0;
            color: #6c757d;
        }

        .mod-actions {
            padding: 12px 20px;
            border-top: 1px solid #f0f0f0;
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
        }

        .teams-btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 0.78em;
            font-weight: 500;
            border: 1px solid #e5e7eb;
            background: #fff;
            cursor: pointer;
            font-family: inherit;
            transition: all 0.2s;
            text-decoration: none;
            color: #323232;
        }

        .teams-btn:hover {
            background: #ede9fe;
            border-color: #7c3aed;
            color: #7c3aed;
        }

        .email-link {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 0.78em;
            font-weight: 500;
            border: 1px solid #e5e7eb;
            background: #fff;
            cursor: pointer;
            font-family: inherit;
            transition: all 0.2s;
            text-decoration: none;
            color: #323232;
        }

        .email-link:hover {
            background: #f0f0ff;
            border-color: #5458F2;
            color: #5458F2;
        }

        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: #adb5bd;
            font-size: 0.95em;
        }

        .no-results {
            text-align: center;
            padding: 40px 20px;
            color: #adb5bd;
            font-size: 0.9em;
            display: none;
        }
    </style>
</head>
<body>

<div class="page-header">
    <h1>&#128100; Användare</h1>
    <div class="header-actions">
        <a class="back-link" href="<%= ctxPath %>/dashboard.jsp">&#8592; Dashboard</a>
    </div>
</div>

<div class="page-content">

    <div class="section-title">
        Alla användare
        <span class="section-count"><%= users.size() %></span>
    </div>

    <div class="search-bar">
        <input type="text" class="search-input" id="userSearch"
               placeholder="&#128269; Sök på namn, email eller användarnamn..."
               autocomplete="off">
    </div>

    <div class="no-results" id="noResults">Inga användare matchar sökningen.</div>

    <% if (users.isEmpty()) { %>
        <div class="empty-state">Inga användare hittades.</div>
    <% } else { %>
        <div class="module-grid" id="userGrid">
            <% for (Map<String, String> u : users) {
                int uid = Integer.parseInt(u.get("id"));
                String fullName = u.get("fullName");
                String email = u.get("email");
                String username = u.get("username");
                String profilePic = u.get("profilePictureUrl");
                String lastLogin = u.get("lastLogin");
                List<String> groups = userGroups.getOrDefault(uid, Collections.emptyList());
            %>
            <div class="mod-card user-card"
                 data-name="<%= fullName != null ? fullName.toLowerCase() : "" %>"
                 data-email="<%= email != null ? email.toLowerCase() : "" %>"
                 data-username="<%= username != null ? username.toLowerCase() : "" %>">
                <div class="mod-card-top">
                    <div class="mod-icon">
                        <% if (profilePic != null && !profilePic.isEmpty()) { %>
                            <img src="<%= profilePic %>" alt="">
                        <% } else { %>
                            &#128100;
                        <% } %>
                    </div>
                    <div class="mod-info">
                        <div class="mod-name"><%= fullName != null ? fullName : username %></div>
                        <div class="mod-desc"><%= email != null ? email : "" %></div>
                        <% if (username != null && !username.equals(fullName)) { %>
                            <div class="mod-username">@<%= username %></div>
                        <% } %>
                    </div>
                </div>

                <% if (!groups.isEmpty() || lastLogin != null) { %>
                <div class="mod-meta">
                    <% for (String g : groups) { %>
                        <span class="mod-badge group-badge"><%= g %></span>
                    <% } %>
                    <% if (lastLogin != null) { %>
                        <span class="mod-badge login-badge">Senast: <%= lastLogin.substring(0, Math.min(10, lastLogin.length())) %></span>
                    <% } %>
                </div>
                <% } %>

                <div class="mod-actions">
                    <% if (email != null && !email.isEmpty()) { %>
                        <a class="teams-btn"
                           href="https://teams.microsoft.com/l/chat/0/0?users=<%= email %>"
                           target="_blank"
                           title="Öppna Teams-chatt med <%= fullName != null ? fullName : username %>">
                            &#128172; Teams
                        </a>
                        <a class="email-link"
                           href="mailto:<%= email %>"
                           title="Skicka email till <%= email %>">
                            &#9993; Email
                        </a>
                    <% } %>
                </div>
            </div>
            <% } %>
        </div>
    <% } %>

</div>

<script nonce="<%= request.getAttribute("cspNonce") %>">
    (function() {
        var searchInput = document.getElementById('userSearch');
        var grid = document.getElementById('userGrid');
        var noResults = document.getElementById('noResults');

        if (!searchInput || !grid) return;

        searchInput.addEventListener('input', function() {
            var query = this.value.toLowerCase().trim();
            var cards = grid.querySelectorAll('.user-card');
            var visibleCount = 0;

            cards.forEach(function(card) {
                var name = card.getAttribute('data-name') || '';
                var email = card.getAttribute('data-email') || '';
                var username = card.getAttribute('data-username') || '';
                var match = !query || name.indexOf(query) !== -1 || email.indexOf(query) !== -1 || username.indexOf(query) !== -1;
                card.style.display = match ? '' : 'none';
                if (match) visibleCount++;
            });

            if (noResults) {
                noResults.style.display = (visibleCount === 0 && query) ? 'block' : 'none';
            }
        });
    })();
</script>

<script nonce="<%= request.getAttribute("cspNonce") %>">
if (window !== window.top) {
    var hdr = document.querySelector('.page-header');
    if (hdr) hdr.style.display = 'none';
    document.body.style.minHeight = 'auto';
}
</script>

</body>
</html>
