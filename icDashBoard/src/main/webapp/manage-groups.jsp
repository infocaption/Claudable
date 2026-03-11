<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.model.User" %>
<%@ page import="com.infocaption.dashboard.model.Group" %>
<%@ page import="java.util.*" %>
<%@ page import="com.infocaption.dashboard.util.Esc" %>
<%
    User user = (User) session.getAttribute("user");
    String ctxPath = request.getContextPath();

    @SuppressWarnings("unchecked")
    List<Group> myGroups = (List<Group>) request.getAttribute("myGroups");
    @SuppressWarnings("unchecked")
    List<Group> visibleGroups = (List<Group>) request.getAttribute("visibleGroups");
    @SuppressWarnings("unchecked")
    Map<Integer, List<Map<String, String>>> groupMembers = (Map<Integer, List<Map<String, String>>>) request.getAttribute("groupMembers");
    @SuppressWarnings("unchecked")
    List<Map<String, String>> allUsers = (List<Map<String, String>>) request.getAttribute("allUsers");

    Integer allaGroupIdObj = (Integer) request.getAttribute("allaGroupId");
    int allaGroupId = allaGroupIdObj != null ? allaGroupIdObj : -1;

    String success = request.getParameter("success");
    String error = request.getParameter("error");

    if (myGroups == null) myGroups = Collections.emptyList();
    if (visibleGroups == null) visibleGroups = Collections.emptyList();
    if (groupMembers == null) groupMembers = Collections.emptyMap();
    if (allUsers == null) allUsers = Collections.emptyList();
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= session.getAttribute("csrfToken") != null ? session.getAttribute("csrfToken") : "" %>">
    <title>Grupper - InfoCaption Dashboard</title>
    <script nonce="<%= request.getAttribute("cspNonce") %>">
    (function(){var t=document.querySelector('meta[name="csrf-token"]'),v=t?t.content:'';
    if(window.parent!==window){window.addEventListener('message',function(e){if(e.origin!==window.location.origin)return;if(e.data&&e.data.type==='CSRF_TOKEN'&&e.data.token){v=e.data.token;}});window.parent.postMessage({type:'GET_CSRF_TOKEN'},window.location.origin);}
    var o=window.fetch;
    window.fetch=function(u,i){i=i||{};var m=(i.method||'GET').toUpperCase();
    if(m!=='GET'&&m!=='HEAD'&&m!=='OPTIONS'){i.headers=i.headers||{};
    if(i.headers instanceof Headers){i.headers.set('X-CSRF-Token',v);}
    else{i.headers['X-CSRF-Token']=v;}}return o.call(this,u,i);};
    document.addEventListener('DOMContentLoaded',function(){document.querySelectorAll('form[method="post"],form[method="POST"]').forEach(function(f){
    if(!f.querySelector('input[name="_csrf"]')){var h=document.createElement('input');h.type='hidden';h.name='_csrf';h.value=v;f.appendChild(h);}});});})();
    </script>
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

        .create-btn {
            background: #5458F2;
            color: #fff;
            border: none;
            padding: 8px 18px;
            border-radius: 8px;
            font-size: 0.85em;
            font-weight: 600;
            text-decoration: none;
            cursor: pointer;
            font-family: inherit;
        }

        .create-btn:hover { background: #4347d9; }

        .page-content {
            max-width: 1000px;
            margin: 30px auto;
            padding: 0 20px;
        }

        .alert {
            padding: 14px 20px;
            border-radius: 10px;
            margin-bottom: 20px;
            font-size: 0.9em;
            font-weight: 500;
        }

        .alert-success {
            background: #d1fae5;
            color: #065f46;
        }

        .alert-error {
            background: #fee2e2;
            color: #ef4444;
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
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        .mod-meta {
            padding: 0 20px 14px;
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
        }

        .mod-badge {
            font-size: 0.7em;
            padding: 3px 9px;
            border-radius: 8px;
            font-weight: 600;
        }

        .mod-badge.system {
            background: #D4D5FC;
            color: #5458F2;
        }

        .mod-badge.hidden-badge {
            background: #fef3c7;
            color: #92400e;
        }

        .mod-badge.member-count {
            background: #d1fae5;
            color: #065f46;
        }

        .mod-badge.standard {
            background: #dbeafe;
            color: #1e40af;
        }

        .mod-badge.category {
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

        .mod-action-btn {
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

        .mod-action-btn:hover {
            background: #f0f0ff;
            border-color: #5458F2;
            color: #5458F2;
        }

        .mod-action-btn.danger:hover {
            background: #fee2e2;
            border-color: #ef4444;
            color: #ef4444;
        }

        .mod-action-btn.join {
            background: #f0fdf4;
            border-color: #10b981;
            color: #065f46;
        }

        .mod-action-btn.join:hover {
            background: #d1fae5;
        }

        .empty-state {
            text-align: center;
            padding: 50px 20px;
            color: #6c757d;
        }

        .empty-state-icon {
            font-size: 3em;
            margin-bottom: 16px;
        }

        .empty-state-text {
            font-size: 1em;
            margin-bottom: 12px;
        }

        /* Member list */
        .member-list {
            display: none;
            padding: 0 20px 14px;
        }

        .member-list.show {
            display: block;
        }

        .member-list-header {
            font-size: 0.78em;
            font-weight: 600;
            color: #6c757d;
            margin-bottom: 8px;
            padding-bottom: 6px;
            border-bottom: 1px solid #f0f0f0;
        }

        .member-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 6px 0;
            font-size: 0.82em;
            color: #323232;
        }

        .member-item + .member-item {
            border-top: 1px solid #f8f9fc;
        }

        .member-name {
            font-weight: 500;
        }

        .member-email {
            color: #6c757d;
            font-size: 0.9em;
            margin-left: 6px;
        }

        .member-remove-btn {
            padding: 3px 10px;
            border-radius: 6px;
            font-size: 0.75em;
            font-weight: 500;
            border: 1px solid #e5e7eb;
            background: #fff;
            cursor: pointer;
            font-family: inherit;
            color: #6c757d;
            transition: all 0.2s;
        }

        .member-remove-btn:hover {
            background: #fee2e2;
            border-color: #ef4444;
            color: #ef4444;
        }

        .teams-chat-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 26px;
            height: 26px;
            border-radius: 6px;
            font-size: 0.85em;
            border: 1px solid #e5e7eb;
            background: #fff;
            text-decoration: none;
            color: #6c757d;
            transition: all 0.2s;
        }

        .teams-chat-btn:hover {
            background: #ede9fe;
            border-color: #7c3aed;
            color: #7c3aed;
        }

        /* Create form section */
        .create-section {
            background: #fff;
            border-radius: 14px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.06);
            margin-bottom: 40px;
            overflow: hidden;
        }

        .create-section-header {
            padding: 16px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            cursor: pointer;
            user-select: none;
        }

        .create-section-header:hover {
            background: #f8f9fc;
        }

        .create-section-title {
            font-size: 0.95em;
            font-weight: 600;
            color: #323232;
        }

        .create-section-toggle {
            font-size: 1.1em;
            color: #6c757d;
            transition: transform 0.2s;
        }

        .create-section-toggle.open {
            transform: rotate(180deg);
        }

        .create-section-body {
            display: none;
            padding: 0 20px 20px;
        }

        .create-section-body.show {
            display: block;
        }

        .form-group {
            margin-bottom: 16px;
        }

        .form-group label {
            display: block;
            font-size: 0.85em;
            font-weight: 600;
            margin-bottom: 6px;
            color: #323232;
        }

        .form-group input,
        .form-group select,
        .form-group textarea {
            width: 100%;
            padding: 10px 14px;
            border: 1.5px solid #e5e7eb;
            border-radius: 10px;
            font-size: 0.9em;
            font-family: inherit;
            box-sizing: border-box;
        }

        .form-group input:focus,
        .form-group select:focus,
        .form-group textarea:focus {
            outline: none;
            border-color: #5458F2;
        }

        .form-group textarea {
            min-height: 80px;
            resize: vertical;
        }

        .form-row {
            display: flex;
            gap: 16px;
            align-items: flex-start;
        }

        .form-row .form-group {
            flex: 1;
        }

        .form-row .form-group.icon-group {
            flex: 0 0 120px;
        }

        .icon-preview {
            display: inline-block;
            font-size: 1.5em;
            margin-left: 10px;
            vertical-align: middle;
        }

        .checkbox-group {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .checkbox-group input[type="checkbox"] {
            width: auto;
            margin: 0;
        }

        .checkbox-group label {
            margin-bottom: 0;
            font-weight: 500;
        }

        .checkbox-hint {
            font-size: 0.78em;
            color: #6c757d;
            margin-top: 4px;
        }

        .form-submit-row {
            display: flex;
            justify-content: flex-end;
            margin-top: 8px;
        }

        /* Modal */
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0,0,0,0.5);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
        }

        .modal-overlay.show {
            display: flex;
        }

        .modal {
            background: #fff;
            border-radius: 16px;
            width: 95%;
            max-width: 550px;
            max-height: 90vh;
            overflow-y: auto;
            box-shadow: 0 20px 60px rgba(0,0,0,0.2);
        }

        .modal-header {
            padding: 20px 24px;
            border-bottom: 1px solid #e5e7eb;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .modal-title {
            font-size: 1.1em;
            font-weight: 600;
        }

        .modal-close {
            background: none;
            border: none;
            font-size: 1.3em;
            cursor: pointer;
            color: #6c757d;
            padding: 4px;
        }

        .modal-body {
            padding: 24px;
        }

        .modal-footer {
            padding: 16px 24px;
            border-top: 1px solid #e5e7eb;
            display: flex;
            justify-content: flex-end;
            gap: 10px;
        }

        .modal .form-group {
            margin-bottom: 16px;
        }

        .modal .form-group label {
            display: block;
            font-size: 0.85em;
            font-weight: 600;
            margin-bottom: 6px;
            color: #323232;
        }

        .modal .form-group input,
        .modal .form-group select,
        .modal .form-group textarea {
            width: 100%;
            padding: 10px 14px;
            border: 1.5px solid #e5e7eb;
            border-radius: 10px;
            font-size: 0.9em;
            font-family: inherit;
            box-sizing: border-box;
        }

        .modal .form-group input:focus,
        .modal .form-group select:focus,
        .modal .form-group textarea:focus {
            outline: none;
            border-color: #5458F2;
        }

        .modal .form-group textarea {
            min-height: 80px;
            resize: vertical;
        }

        .modal-btn {
            padding: 10px 22px;
            border-radius: 10px;
            font-size: 0.88em;
            font-weight: 600;
            cursor: pointer;
            font-family: inherit;
            border: none;
        }

        .modal-btn.primary {
            background: #5458F2;
            color: #fff;
        }

        .modal-btn.primary:hover {
            background: #4347d9;
        }

        .modal-btn.secondary {
            background: #f0f0f0;
            color: #323232;
        }

        .add-member-select {
            width: 100%;
            padding: 10px 14px;
            border: 1.5px solid #e5e7eb;
            border-radius: 10px;
            font-size: 0.9em;
            font-family: inherit;
            box-sizing: border-box;
        }

        .add-member-select:focus {
            outline: none;
            border-color: #5458F2;
        }

        @media (max-width: 600px) {
            .module-grid {
                grid-template-columns: 1fr;
            }
            .page-header {
                flex-direction: column;
                gap: 12px;
                align-items: flex-start;
            }
            .form-row {
                flex-direction: column;
                gap: 0;
            }
            .form-row .form-group.icon-group {
                flex: 1;
            }
        }
    </style>
</head>
<body>
    <div class="page-header">
        <h1>&#128101; Grupper</h1>
        <div class="header-actions">
            <a href="<%= ctxPath %>/dashboard.jsp" class="back-link">&#8592; Dashboard</a>
        </div>
    </div>

    <div class="page-content">
        <% if ("created".equals(success)) { %>
            <div class="alert alert-success">&#9989; Grupp skapad!</div>
        <% } else if ("updated".equals(success)) { %>
            <div class="alert alert-success">&#9989; Grupp uppdaterad!</div>
        <% } else if ("deleted".equals(success)) { %>
            <div class="alert alert-success">&#9989; Grupp borttagen.</div>
        <% } else if ("joined".equals(success)) { %>
            <div class="alert alert-success">&#9989; Du gick med i gruppen!</div>
        <% } else if ("left".equals(success)) { %>
            <div class="alert alert-success">&#9989; Du l&#228;mnade gruppen.</div>
        <% } else if ("member_added".equals(success)) { %>
            <div class="alert alert-success">&#9989; Medlem tillagd!</div>
        <% } else if ("member_removed".equals(success)) { %>
            <div class="alert alert-success">&#9989; Medlem borttagen.</div>
        <% } %>

        <% if (error != null) { %>
            <div class="alert alert-error">&#10060; Ett fel intr&#228;ffade: <%= Esc.h(request.getParameter("error")) %></div>
        <% } %>

        <!-- User's groups -->
        <div class="section-title">
            &#128100; Dina grupper <span class="section-count"><%= myGroups.size() %></span>
        </div>

        <% if (myGroups.isEmpty()) { %>
            <div class="empty-state">
                <div class="empty-state-icon">&#128101;</div>
                <div class="empty-state-text">Du &#228;r inte medlem i n&#229;gon grupp &#228;nnu.</div>
            </div>
        <% } else { %>
            <div class="module-grid">
                <% for (Group g : myGroups) {
                    boolean isAlla = g.getId() == allaGroupId;
                    List<Map<String, String>> members = groupMembers.getOrDefault(g.getId(), Collections.emptyList());
                %>
                    <div class="mod-card">
                        <div class="mod-card-top">
                            <div class="mod-icon"><%= Esc.h(g.getIcon() != null ? g.getIcon() : "\uD83D\uDC65") %></div>
                            <div class="mod-info">
                                <div class="mod-name"><%= Esc.h(g.getName()) %></div>
                                <div class="mod-desc"><%= Esc.h(g.getDescription() != null ? g.getDescription() : "") %></div>
                            </div>
                        </div>
                        <div class="mod-meta">
                            <span class="mod-badge member-count">&#128101; <%= g.getMemberCount() %> medlemmar</span>
                            <% if (isAlla) { %>
                                <span class="mod-badge standard">&#127760; Standard</span>
                            <% } %>
                            <% if (g.isHidden()) { %>
                                <span class="mod-badge hidden-badge">&#128274; Dold</span>
                            <% } %>
                        </div>
                        <div class="mod-actions">
                            <button type="button" class="mod-action-btn" onclick="toggleMembers(<%= g.getId() %>)">&#128065; Visa medlemmar</button>

                            <% if (g.isHidden()) { %>
                                <button type="button" class="mod-action-btn join group-add-member-btn"
                                    data-group-id="<%= g.getId() %>"
                                    data-group-name="<%= Esc.h(g.getName()) %>">&#10133; L&#228;gg till medlem</button>
                            <% } %>

                            <button type="button" class="mod-action-btn group-edit-btn"
                                data-id="<%= g.getId() %>"
                                data-name="<%= Esc.h(g.getName()) %>"
                                data-icon="<%= Esc.h(g.getIcon() != null ? g.getIcon() : "") %>"
                                data-description="<%= Esc.h(g.getDescription() != null ? g.getDescription() : "") %>"
                                data-hidden="<%= g.isHidden() %>">
                                &#9998; Redigera
                            </button>

                            <% if (!isAlla) { %>
                                <button type="button" class="mod-action-btn danger group-leave-btn"
                                    data-group-id="<%= g.getId() %>"
                                    data-group-name="<%= Esc.h(g.getName()) %>">&#128682; L&#228;mna</button>
                            <% } %>
                        </div>

                        <!-- Member list (hidden by default) -->
                        <div class="member-list" id="members-<%= g.getId() %>">
                            <div class="member-list-header">Medlemmar (<%= members.size() %>)</div>
                            <% if (members.isEmpty()) { %>
                                <div style="font-size: 0.82em; color: #6c757d; padding: 6px 0;">Inga medlemmar.</div>
                            <% } else { %>
                                <% for (Map<String, String> member : members) { %>
                                    <div class="member-item">
                                        <div>
                                            <span class="member-name"><%= Esc.h(member.get("fullName")) %></span>
                                            <span class="member-email">(<%= Esc.h(member.get("email")) %>)</span>
                                        </div>
                                        <div style="display: flex; gap: 6px; align-items: center;">
                                            <% if (member.get("email") != null && !member.get("email").isEmpty()) { %>
                                                <a href="https://teams.microsoft.com/l/chat/0/0?users=<%= Esc.h(member.get("email")) %>"
                                                   target="_blank"
                                                   class="teams-chat-btn"
                                                   title="Chatta i Teams med <%= Esc.h(member.get("fullName")) %>">&#128172;</a>
                                            <% } %>
                                            <% if (g.isHidden()) { %>
                                                <button type="button" class="member-remove-btn group-remove-member-btn"
                                                    data-group-id="<%= g.getId() %>"
                                                    data-user-id="<%= Integer.parseInt(member.get("id")) %>"
                                                    data-member-name="<%= Esc.h(member.get("fullName")) %>">&#10005; Ta bort</button>
                                            <% } %>
                                        </div>
                                    </div>
                                <% } %>
                            <% } %>
                        </div>
                    </div>
                <% } %>
            </div>
        <% } %>

        <!-- Available groups to join -->
        <% if (!visibleGroups.isEmpty()) { %>
            <div class="section-title" style="margin-top: 30px;">
                &#127760; Tillg&#228;ngliga grupper <span class="section-count"><%= visibleGroups.size() %></span>
            </div>

            <div class="module-grid">
                <% for (Group g : visibleGroups) { %>
                    <div class="mod-card">
                        <div class="mod-card-top">
                            <div class="mod-icon"><%= Esc.h(g.getIcon() != null ? g.getIcon() : "\uD83D\uDC65") %></div>
                            <div class="mod-info">
                                <div class="mod-name"><%= Esc.h(g.getName()) %></div>
                                <div class="mod-desc"><%= Esc.h(g.getDescription() != null ? g.getDescription() : "") %></div>
                            </div>
                        </div>
                        <div class="mod-meta">
                            <span class="mod-badge member-count">&#128101; <%= g.getMemberCount() %> medlemmar</span>
                        </div>
                        <div class="mod-actions">
                            <button type="button" class="mod-action-btn join group-join-btn"
                                data-group-id="<%= g.getId() %>"
                                data-group-name="<%= Esc.h(g.getName()) %>">&#10133; G&#229; med</button>
                        </div>
                    </div>
                <% } %>
            </div>
        <% } %>

        <!-- Create new group -->
        <div class="section-title" style="margin-top: 30px;">
            &#10133; Skapa ny grupp
        </div>

        <div class="create-section">
            <div class="create-section-header" onclick="toggleCreateForm()">
                <span class="create-section-title">Ny grupp</span>
                <span class="create-section-toggle" id="createToggle">&#9660;</span>
            </div>
            <div class="create-section-body" id="createForm">
                <form method="post" action="<%= ctxPath %>/group/manage">
                    <input type="hidden" name="action" value="create">
                    <div class="form-row">
                        <div class="form-group">
                            <label>Gruppnamn</label>
                            <input type="text" name="name" required placeholder="T.ex. Projektgrupp Alpha">
                        </div>
                        <div class="form-group icon-group">
                            <label>Ikon <span class="icon-preview" id="iconPreview">&#128101;</span></label>
                            <input type="text" name="icon" maxlength="10" placeholder="&#128101;" oninput="updateIconPreview(this.value)">
                        </div>
                    </div>
                    <div class="form-group">
                        <label>Beskrivning</label>
                        <textarea name="description" rows="3" placeholder="Valfri beskrivning av gruppen..."></textarea>
                    </div>
                    <div class="form-group">
                        <div class="checkbox-group">
                            <input type="checkbox" name="hidden" id="createHidden" value="true">
                            <label for="createHidden">Dold grupp</label>
                        </div>
                        <div class="checkbox-hint">Dolda grupper syns inte f&#246;r andra anv&#228;ndare. Medlemmar l&#228;ggs till manuellt.</div>
                    </div>
                    <div class="form-submit-row">
                        <button type="submit" class="create-btn">&#10133; Skapa grupp</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- Edit Group Modal -->
    <div class="modal-overlay" id="editGroupModal">
        <div class="modal">
            <div class="modal-header">
                <div class="modal-title">&#9998; Redigera grupp</div>
                <button class="modal-close" onclick="closeEditGroupModal()">&#10005;</button>
            </div>
            <form method="post" action="<%= ctxPath %>/group/manage">
                <input type="hidden" name="action" value="update">
                <input type="hidden" name="groupId" id="editGroupId">
                <div class="modal-body">
                    <div class="form-group">
                        <label>Gruppnamn</label>
                        <input type="text" name="name" id="editGroupName" required>
                    </div>
                    <div class="form-group">
                        <label>Ikon (emoji)</label>
                        <input type="text" name="icon" id="editGroupIcon" maxlength="10">
                    </div>
                    <div class="form-group">
                        <label>Beskrivning</label>
                        <textarea name="description" id="editGroupDescription" rows="3"></textarea>
                    </div>
                    <div class="form-group">
                        <div class="checkbox-group">
                            <input type="checkbox" name="hidden" id="editGroupHidden" value="true">
                            <label for="editGroupHidden">Dold grupp</label>
                        </div>
                        <div class="checkbox-hint">Dolda grupper syns inte f&#246;r andra anv&#228;ndare.</div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="modal-btn secondary" onclick="closeEditGroupModal()">Avbryt</button>
                    <button type="submit" class="modal-btn primary">Spara</button>
                </div>
            </form>
        </div>
    </div>

    <!-- Add Member Modal -->
    <div class="modal-overlay" id="addMemberModal">
        <div class="modal">
            <div class="modal-header">
                <div class="modal-title">&#10133; L&#228;gg till medlem</div>
                <button class="modal-close" onclick="closeAddMemberModal()">&#10005;</button>
            </div>
            <form method="post" action="<%= ctxPath %>/group/manage">
                <input type="hidden" name="action" value="add_member">
                <input type="hidden" name="groupId" id="addMemberGroupId">
                <div class="modal-body">
                    <div class="form-group">
                        <label>Grupp: <span id="addMemberGroupName" style="font-weight: 400;"></span></label>
                    </div>
                    <div class="form-group">
                        <label>V&#228;lj anv&#228;ndare</label>
                        <select name="userId" class="add-member-select" required>
                            <option value="">-- V&#228;lj anv&#228;ndare --</option>
                            <% for (Map<String, String> u : allUsers) { %>
                                <option value="<%= u.get("id") %>"><%= Esc.h(u.get("fullName")) %> (<%= Esc.h(u.get("email")) %>)</option>
                            <% } %>
                        </select>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="modal-btn secondary" onclick="closeAddMemberModal()">Avbryt</button>
                    <button type="submit" class="modal-btn primary">L&#228;gg till</button>
                </div>
            </form>
        </div>
    </div>

    <script nonce="<%= request.getAttribute("cspNonce") %>">
        var ctxPath = '<%= ctxPath %>';

        // Event delegation for data-attribute-driven buttons (XSS-safe)
        document.addEventListener('click', function(e) {
            var btn;
            if ((btn = e.target.closest('.group-edit-btn'))) {
                openEditGroupModal(
                    parseInt(btn.getAttribute('data-id')),
                    btn.getAttribute('data-name'),
                    btn.getAttribute('data-icon'),
                    btn.getAttribute('data-description'),
                    btn.getAttribute('data-hidden') === 'true'
                );
            } else if ((btn = e.target.closest('.group-add-member-btn'))) {
                openAddMemberModal(
                    parseInt(btn.getAttribute('data-group-id')),
                    btn.getAttribute('data-group-name')
                );
            } else if ((btn = e.target.closest('.group-leave-btn'))) {
                leaveGroup(
                    parseInt(btn.getAttribute('data-group-id')),
                    btn.getAttribute('data-group-name')
                );
            } else if ((btn = e.target.closest('.group-join-btn'))) {
                joinGroup(
                    parseInt(btn.getAttribute('data-group-id')),
                    btn.getAttribute('data-group-name')
                );
            } else if ((btn = e.target.closest('.group-remove-member-btn'))) {
                removeMember(
                    parseInt(btn.getAttribute('data-group-id')),
                    parseInt(btn.getAttribute('data-user-id')),
                    btn.getAttribute('data-member-name')
                );
            }
        });

        // Toggle member list visibility
        function toggleMembers(groupId) {
            var el = document.getElementById('members-' + groupId);
            if (el) {
                el.classList.toggle('show');
            }
        }

        // Toggle create form
        function toggleCreateForm() {
            var body = document.getElementById('createForm');
            var toggle = document.getElementById('createToggle');
            body.classList.toggle('show');
            toggle.classList.toggle('open');
        }

        // Icon preview in create form
        function updateIconPreview(value) {
            var preview = document.getElementById('iconPreview');
            preview.textContent = value || '\u{1F465}';
        }

        // Join group via fetch
        function joinGroup(groupId, groupName) {
            if (!confirm('Vill du g\u00e5 med i gruppen "' + groupName + '"?')) return;
            fetch(ctxPath + '/api/groups', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'join', groupId: groupId })
            }).then(function(resp) {
                if (resp.ok) {
                    window.location.href = ctxPath + '/group/manage?success=joined';
                } else {
                    return resp.json().then(function(data) {
                        window.location.href = ctxPath + '/group/manage?error=' + encodeURIComponent(data.error || 'Kunde inte g\u00e5 med i gruppen.');
                    });
                }
            }).catch(function() {
                window.location.href = ctxPath + '/group/manage?error=' + encodeURIComponent('N\u00e4tverksfel. F\u00f6rs\u00f6k igen.');
            });
        }

        // Leave group via fetch
        function leaveGroup(groupId, groupName) {
            if (!confirm('\u00c4r du s\u00e4ker p\u00e5 att du vill l\u00e4mna gruppen "' + groupName + '"?')) return;
            fetch(ctxPath + '/api/groups', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'leave', groupId: groupId })
            }).then(function(resp) {
                if (resp.ok) {
                    window.location.href = ctxPath + '/group/manage?success=left';
                } else {
                    return resp.json().then(function(data) {
                        window.location.href = ctxPath + '/group/manage?error=' + encodeURIComponent(data.error || 'Kunde inte l\u00e4mna gruppen.');
                    });
                }
            }).catch(function() {
                window.location.href = ctxPath + '/group/manage?error=' + encodeURIComponent('N\u00e4tverksfel. F\u00f6rs\u00f6k igen.');
            });
        }

        // Remove member via fetch
        function removeMember(groupId, userId, memberName) {
            if (!confirm('Vill du ta bort "' + memberName + '" fr\u00e5n gruppen?')) return;
            fetch(ctxPath + '/api/groups', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'remove_member', groupId: groupId, userId: userId })
            }).then(function(resp) {
                if (resp.ok) {
                    window.location.href = ctxPath + '/group/manage?success=member_removed';
                } else {
                    return resp.json().then(function(data) {
                        window.location.href = ctxPath + '/group/manage?error=' + encodeURIComponent(data.error || 'Kunde inte ta bort medlemmen.');
                    });
                }
            }).catch(function() {
                window.location.href = ctxPath + '/group/manage?error=' + encodeURIComponent('N\u00e4tverksfel. F\u00f6rs\u00f6k igen.');
            });
        }

        // Edit group modal
        function openEditGroupModal(id, name, icon, description, hidden) {
            document.getElementById('editGroupId').value = id;
            document.getElementById('editGroupName').value = name;
            document.getElementById('editGroupIcon').value = icon;
            document.getElementById('editGroupDescription').value = description;
            document.getElementById('editGroupHidden').checked = hidden;
            document.getElementById('editGroupModal').classList.add('show');
        }

        function closeEditGroupModal() {
            document.getElementById('editGroupModal').classList.remove('show');
        }

        // Add member modal
        function openAddMemberModal(groupId, groupName) {
            document.getElementById('addMemberGroupId').value = groupId;
            document.getElementById('addMemberGroupName').textContent = groupName;
            document.getElementById('addMemberModal').classList.add('show');
        }

        function closeAddMemberModal() {
            document.getElementById('addMemberModal').classList.remove('show');
        }

        // Close modals on overlay click
        document.getElementById('editGroupModal').addEventListener('click', function(e) {
            if (e.target === this) closeEditGroupModal();
        });
        document.getElementById('addMemberModal').addEventListener('click', function(e) {
            if (e.target === this) closeAddMemberModal();
        });

        // Close modals on Escape
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') {
                closeEditGroupModal();
                closeAddMemberModal();
            }
        });
    </script>
    <script nonce="<%= request.getAttribute("cspNonce") %>">
    // If loaded inside dashboard iframe, hide page header (back link is redundant)
    if (window !== window.top) {
        var hdr = document.querySelector('.page-header');
        if (hdr) hdr.style.display = 'none';
        document.body.style.minHeight = 'auto';
    }
    </script>
</body>
</html>
