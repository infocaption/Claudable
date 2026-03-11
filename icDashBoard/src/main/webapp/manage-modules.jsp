<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.model.User" %>
<%@ page import="com.infocaption.dashboard.model.Module" %>
<%@ page import="com.infocaption.dashboard.model.Group" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="com.infocaption.dashboard.util.Esc" %>
<%
    User user = (User) session.getAttribute("user");
    String ctxPath = request.getContextPath();

    @SuppressWarnings("unchecked")
    List<Module> userModules = (List<Module>) request.getAttribute("userModules");
    @SuppressWarnings("unchecked")
    List<Module> systemModules = (List<Module>) request.getAttribute("systemModules");

    String success = request.getParameter("success");
    String error = request.getParameter("error");

    if (userModules == null) userModules = java.util.Collections.emptyList();
    if (systemModules == null) systemModules = java.util.Collections.emptyList();

    @SuppressWarnings("unchecked")
    List<Group> allGroups = (List<Group>) request.getAttribute("allGroups");
    @SuppressWarnings("unchecked")
    Map<Integer, List<Integer>> moduleGroupIds = (Map<Integer, List<Integer>>) request.getAttribute("moduleGroupIds");
    @SuppressWarnings("unchecked")
    Map<Integer, List<String>> moduleGroupNames = (Map<Integer, List<String>>) request.getAttribute("moduleGroupNames");
    if (allGroups == null) allGroups = new java.util.ArrayList<>();
    if (moduleGroupIds == null) moduleGroupIds = new java.util.HashMap<>();
    if (moduleGroupNames == null) moduleGroupNames = new java.util.HashMap<>();
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= session.getAttribute("csrfToken") != null ? session.getAttribute("csrfToken") : "" %>">
    <title>Mina moduler - InfoCaption Dashboard</title>
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

        /* Module card grid */
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

        .mod-badge.private {
            background: #fef3c7;
            color: #92400e;
        }

        .mod-badge.shared {
            background: #d1fae5;
            color: #065f46;
        }

        .mod-badge.category {
            background: #f0f0f0;
            color: #6c757d;
        }

        .mod-badge.has-spec {
            background: #ede9fe;
            color: #7c3aed;
        }

        .mod-badge.group-badge {
            background: #e0e7ff;
            color: #3730a3;
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

        .mod-action-btn.visibility {
            background: #f0fdf4;
            border-color: #10b981;
            color: #065f46;
        }

        .mod-action-btn.spec-btn {
            background: #ede9fe;
            border-color: #7c3aed;
            color: #7c3aed;
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

        /* Edit modal */
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
        }

        .modal .form-group input:focus,
        .modal .form-group select:focus,
        .modal .form-group textarea:focus {
            outline: none;
            border-color: #5458F2;
        }

        .modal .form-group textarea {
            min-height: 120px;
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

        .group-edit-section {
            padding: 16px 24px 20px;
            border-top: 1px solid #e5e7eb;
        }

        .group-edit-header label {
            font-size: 0.85em;
            font-weight: 600;
            color: #323232;
            display: block;
            margin-bottom: 10px;
        }

        .group-checkboxes {
            display: flex;
            flex-wrap: wrap;
            gap: 8px 16px;
            margin-bottom: 12px;
        }

        .group-checkbox-label {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 0.85em;
            color: #323232;
            cursor: pointer;
        }

        .group-checkbox-label input[type="checkbox"] {
            width: 16px;
            height: 16px;
            accent-color: #5458F2;
            cursor: pointer;
        }

        .group-edit-actions {
            display: flex;
            justify-content: flex-end;
        }

        /* File upload drop area in modal */
        .upload-drop-area {
            border: 2px dashed #D4D5FC;
            border-radius: 12px;
            padding: 30px 20px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s;
            background: #fafafe;
        }

        .upload-drop-area:hover,
        .upload-drop-area.dragover {
            border-color: #5458F2;
            background: #f0f0ff;
        }

        .upload-drop-area.has-file {
            border-color: #10b981;
            background: #f0fdf4;
        }

        .upload-drop-icon {
            font-size: 2em;
            margin-bottom: 8px;
        }

        .upload-drop-text {
            font-size: 0.9em;
            font-weight: 500;
            color: #323232;
            margin-bottom: 4px;
        }

        .upload-drop-hint {
            font-size: 0.78em;
            color: #6c757d;
        }

        .upload-drop-filename {
            margin-top: 8px;
            font-size: 0.82em;
            color: #10b981;
            font-weight: 600;
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
        }
    </style>
</head>
<body>
    <div class="page-header">
        <h1>&#9881; Mina moduler</h1>
        <div class="header-actions">
            <a href="<%= ctxPath %>/dashboard.jsp" class="back-link">&#8592; Dashboard</a>
            <a href="<%= ctxPath %>/module/create" class="create-btn">&#10133; Skapa ny modul</a>
        </div>
    </div>

    <div class="page-content">
        <% if ("created".equals(success)) { %>
            <div class="alert alert-success">&#9989; Modul skapad!</div>
        <% } else if ("updated".equals(success)) { %>
            <div class="alert alert-success">&#9989; Modul uppdaterad!</div>
        <% } else if ("file_updated".equals(success)) { %>
            <div class="alert alert-success">&#9989; Modulfilen har uppdaterats!</div>
        <% } else if ("deleted".equals(success)) { %>
            <div class="alert alert-success">&#9989; Modul borttagen.</div>
        <% } else if ("visibility".equals(success)) { %>
            <div class="alert alert-success">&#9989; Synlighet &#228;ndrad!</div>
        <% } else if ("groups_updated".equals(success)) { %>
            <div class="alert alert-success">&#9989; Gruppsynligheten har uppdaterats!</div>
        <% } %>

        <% if (error != null) { %>
            <div class="alert alert-error">&#10060; Ett fel intr&#228;ffade: <%= Esc.h(request.getParameter("error")) %></div>
        <% } %>

        <!-- User's modules -->
        <div class="section-title">
            &#128100; Dina moduler <span class="section-count"><%= userModules.size() %></span>
        </div>

        <% if (userModules.isEmpty()) { %>
            <div class="empty-state">
                <div class="empty-state-icon">&#128230;</div>
                <div class="empty-state-text">Du har inga moduler &#228;nnu.</div>
                <a href="<%= ctxPath %>/module/create" class="create-btn">&#10133; Skapa din f&#246;rsta modul</a>
            </div>
        <% } else { %>
            <div class="module-grid">
                <% for (Module m : userModules) { %>
                    <div class="mod-card">
                        <div class="mod-card-top">
                            <div class="mod-icon"><%= Esc.h(m.getIcon()) %></div>
                            <div class="mod-info">
                                <div class="mod-name"><%= Esc.h(m.getName()) %></div>
                                <div class="mod-desc"><%= Esc.h(m.getDescription() != null ? m.getDescription() : "") %></div>
                            </div>
                        </div>
                        <div class="mod-meta">
                            <span class="mod-badge <%= m.getModuleType() %>">
                                <%= "shared".equals(m.getModuleType()) ? "&#128101; Delad" : "&#128274; Privat" %>
                            </span>
                            <span class="mod-badge category"><%= Esc.h(m.getCategory()) %></span>
                            <% if (m.getAiSpecText() != null && !m.getAiSpecText().isEmpty()) { %>
                                <span class="mod-badge has-spec">&#129302; AI-spec</span>
                            <% } %>
                            <% List<String> gNames = moduleGroupNames.get(m.getId());
                               if (gNames != null) { for (String gn : gNames) { %>
                                <span class="mod-badge group-badge"><%= Esc.h(gn) %></span>
                            <% } } %>
                        </div>
                        <div class="mod-actions">
                            <a href="<%= ctxPath %>/dashboard.jsp?module=<%= m.getDirectoryName() %>" class="mod-action-btn">&#9654; &#214;ppna</a>

                            <form method="post" action="<%= ctxPath %>/module/manage" style="display:inline;">
                                <input type="hidden" name="action" value="toggle-visibility">
                                <input type="hidden" name="moduleId" value="<%= m.getId() %>">
                                <button type="submit" class="mod-action-btn visibility">
                                    <%= "shared".equals(m.getModuleType()) ? "&#128274; G&#246;r privat" : "&#128101; Dela" %>
                                </button>
                            </form>

                            <button type="button" class="mod-action-btn edit-module-btn"
                                data-id="<%= m.getId() %>"
                                data-name="<%= Esc.h(m.getName()) %>"
                                data-icon="<%= Esc.h(m.getIcon()) %>"
                                data-description="<%= Esc.h(m.getDescription() != null ? m.getDescription() : "") %>"
                                data-category="<%= Esc.h(m.getCategory()) %>"
                                data-aispec="<%= Esc.h(m.getAiSpecText() != null ? m.getAiSpecText() : "") %>">
                                &#9998; Redigera
                            </button>

                            <button type="button" class="mod-action-btn upload-file-btn"
                                data-id="<%= m.getId() %>"
                                data-name="<%= Esc.h(m.getName()) %>">
                                &#128228; Uppdatera fil
                            </button>

                            <% if (m.getAiSpecText() != null && !m.getAiSpecText().isEmpty()) { %>
                                <a href="<%= ctxPath %>/api/module/spec?id=<%= m.getId() %>&download=true" class="mod-action-btn spec-btn">&#128196; Exportera spec</a>
                            <% } %>

                            <form method="post" action="<%= ctxPath %>/module/manage" style="display:inline;" onsubmit="return confirm('&#196;r du s&#228;ker p&#229; att du vill ta bort denna modul? Det g&#229;r inte att &#229;ngra.')">
                                <input type="hidden" name="action" value="delete">
                                <input type="hidden" name="moduleId" value="<%= m.getId() %>">
                                <button type="submit" class="mod-action-btn danger">&#128465; Ta bort</button>
                            </form>
                        </div>
                    </div>
                <% } %>
            </div>
        <% } %>

        <!-- System modules -->
        <div class="section-title" style="margin-top: 30px;">
            &#128187; Systemmoduler <span class="section-count"><%= systemModules.size() %></span>
        </div>

        <div class="module-grid">
            <% for (Module m : systemModules) { %>
                <div class="mod-card">
                    <div class="mod-card-top">
                        <div class="mod-icon"><%= Esc.h(m.getIcon()) %></div>
                        <div class="mod-info">
                            <div class="mod-name"><%= Esc.h(m.getName()) %></div>
                            <div class="mod-desc"><%= Esc.h(m.getDescription() != null ? m.getDescription() : "") %></div>
                        </div>
                    </div>
                    <div class="mod-meta">
                        <span class="mod-badge system">&#128187; System</span>
                        <span class="mod-badge category"><%= Esc.h(m.getCategory()) %></span>
                    </div>
                    <div class="mod-actions">
                        <a href="<%= ctxPath %>/dashboard.jsp?module=<%= m.getDirectoryName() %>" class="mod-action-btn">&#9654; &#214;ppna</a>
                        <span style="font-size: 0.75em; color: #6c757d; padding: 6px 0;">Systemmoduler kan inte redigeras</span>
                    </div>
                </div>
            <% } %>
        </div>
    </div>

    <!-- Edit Modal -->
    <div class="modal-overlay" id="editModal">
        <div class="modal">
            <div class="modal-header">
                <div class="modal-title">&#9998; Redigera modul</div>
                <button class="modal-close" onclick="closeEditModal()">&#10005;</button>
            </div>
            <form method="post" action="<%= ctxPath %>/module/manage">
                <input type="hidden" name="action" value="update">
                <input type="hidden" name="moduleId" id="editModuleId">
                <div class="modal-body">
                    <div class="form-group">
                        <label>Modulnamn</label>
                        <input type="text" name="name" id="editName" required>
                    </div>
                    <div class="form-group">
                        <label>Ikon (emoji)</label>
                        <input type="text" name="icon" id="editIcon" maxlength="10">
                    </div>
                    <div class="form-group">
                        <label>Beskrivning</label>
                        <textarea name="description" id="editDescription" rows="3"></textarea>
                    </div>
                    <div class="form-group">
                        <label>Kategori</label>
                        <select name="category" id="editCategory">
                            <option value="tools">Verktyg</option>
                            <option value="analytics">Analys &amp; Rapporter</option>
                            <option value="admin">Administration</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>AI-specifikation</label>
                        <textarea name="aiSpecText" id="editAiSpec" rows="6" style="font-family: monospace; font-size: 0.85em;"></textarea>
                    </div>
                    <div style="padding: 12px; background: linear-gradient(135deg, #ede9fe 0%, #f0f0ff 100%); border-radius: 8px; border: 1px solid #d4d5fc; display: flex; align-items: center; justify-content: space-between; gap: 10px;">
                        <div style="font-size: 0.78em; color: #6c757d;">&#129302; Kombinera spec + dokumentation till f&#228;rdig AI-prompt</div>
                        <button type="button" onclick="generateEditPrompt()" style="background: #5458F2; color: #fff; border: none; padding: 6px 14px; border-radius: 6px; font-size: 0.78em; font-weight: 600; cursor: pointer; font-family: inherit; white-space: nowrap;">&#128203; Generera prompt</button>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="modal-btn secondary" onclick="closeEditModal()">Avbryt</button>
                    <button type="submit" class="modal-btn primary">Spara</button>
                </div>
            </form>
            <% if (!allGroups.isEmpty()) { %>
            <div class="group-edit-section">
                <form method="post" action="<%= ctxPath %>/module/manage" id="groupForm">
                    <input type="hidden" name="action" value="update-groups">
                    <input type="hidden" name="moduleId" id="groupEditModuleId">
                    <div class="group-edit-header">
                        <label>Gruppsynlighet:</label>
                    </div>
                    <div class="group-checkboxes" id="groupCheckboxes">
                        <% for (Group g : allGroups) { %>
                            <label class="group-checkbox-label">
                                <input type="checkbox" name="groupIds" value="<%= g.getId() %>" class="group-cb">
                                <span><%= Esc.h(g.getIcon() != null ? g.getIcon() : "") %> <%= Esc.h(g.getName()) %></span>
                            </label>
                        <% } %>
                    </div>
                    <div class="group-edit-actions">
                        <button type="submit" class="modal-btn primary" style="padding: 7px 16px; font-size: 0.82em;">Spara grupper</button>
                    </div>
                </form>
            </div>
            <% } %>
        </div>
    </div>

    <!-- File Upload Modal -->
    <div class="modal-overlay" id="uploadModal">
        <div class="modal" style="max-width: 480px;">
            <div class="modal-header">
                <div class="modal-title">&#128228; Uppdatera modulfil</div>
                <button class="modal-close" onclick="closeUploadModal()">&#10005;</button>
            </div>
            <form method="post" enctype="multipart/form-data" id="uploadForm">
                <input type="hidden" name="action" value="update-file">
                <input type="hidden" name="moduleId" id="uploadModuleId">
                <div class="modal-body">
                    <div style="font-size: 0.88em; color: #6c757d; margin-bottom: 16px;">
                        Ladda upp en ny HTML-fil f&#246;r <strong id="uploadModuleName"></strong>.
                        Den gamla filen ers&#228;tts.
                    </div>
                    <div class="upload-drop-area" id="modalUploadArea" onclick="document.getElementById('modalFileInput').click()">
                        <div class="upload-drop-icon" id="modalUploadIcon">&#128228;</div>
                        <div class="upload-drop-text" id="modalUploadText">Klicka eller dra fil hit</div>
                        <div class="upload-drop-hint">Accepterar .html eller .zip</div>
                        <div class="upload-drop-filename" id="modalUploadFilename" style="display:none;"></div>
                    </div>
                    <input type="file" name="moduleFile" id="modalFileInput" accept=".html,.htm,.zip" style="display:none;">
                </div>
                <div class="modal-footer">
                    <button type="button" class="modal-btn secondary" onclick="closeUploadModal()">Avbryt</button>
                    <button type="submit" class="modal-btn primary" id="uploadSubmitBtn" disabled>&#128640; Uppdatera</button>
                </div>
            </form>
        </div>
    </div>

    <script nonce="<%= request.getAttribute("cspNonce") %>">
        var CTX = '<%= ctxPath %>';

        // Module-to-group mapping for checkbox pre-selection
        var moduleGroupMap = {};
        <% for (Module m : userModules) {
            List<Integer> gIds = moduleGroupIds.get(m.getId());
            if (gIds != null && !gIds.isEmpty()) { %>
                moduleGroupMap[<%= m.getId() %>] = [<% for (int gi = 0; gi < gIds.size(); gi++) { %><%= gIds.get(gi) %><%= gi < gIds.size() - 1 ? "," : "" %><% } %>];
        <%  } else { %>
                moduleGroupMap[<%= m.getId() %>] = [];
        <%  }
        } %>

        function generateEditPrompt() {
            var specText = document.getElementById('editAiSpec').value.trim();
            var moduleName = document.getElementById('editName').value.trim() || 'Modul';
            var moduleDesc = document.getElementById('editDescription').value.trim();
            var moduleCategory = document.getElementById('editCategory').value;

            var prompt = 'Skapa/uppdatera en InfoCaption Dashboard-modul \u00e5t mig.\n\n';
            prompt += '## Modulinfo\n';
            prompt += '- **Namn**: ' + moduleName + '\n';
            if (moduleDesc) prompt += '- **Beskrivning**: ' + moduleDesc + '\n';
            prompt += '- **Kategori**: ' + moduleCategory + '\n\n';

            if (specText) {
                prompt += '## Min specifikation\n' + specText + '\n\n';
            }

            prompt += '## Tekniska krav\n';
            prompt += 'Modulen ska vara en enda HTML-fil som laddas i en iframe i dashboarden.\n\n';
            prompt += '### Filformat\n';
            prompt += '- Ren HTML (ingen JSP, inget byggsystem)\n';
            prompt += '- Inkludera `../../shared/ic-styles.css` i <head>\n';
            prompt += '- Inkludera `../../shared/ic-utils.js` f\u00f6re modulens script\n';
            prompt += '- Anropa `ICUtils.notifyReady(\'modul-id\')` vid DOMContentLoaded\n';
            prompt += '- All UI-text p\u00e5 svenska\n';
            prompt += '- Responsiv design (320px\u20131920px)\n\n';

            prompt += '### Designsystem (CSS-variabler)\n';
            prompt += '```css\n';
            prompt += '--ic-primary: #5458F2;     /* Prim\u00e4rf\u00e4rg */\n';
            prompt += '--ic-secondary: #D4D5FC;   /* Bakgrund, hover */\n';
            prompt += '--ic-accent: #FFE496;      /* Gul accent */\n';
            prompt += '--ic-text-dark: #323232;   /* Huvudtext */\n';
            prompt += '--ic-text-muted: #6c757d;  /* Sekund\u00e4r text */\n';
            prompt += '--ic-success: #10b981;     /* Gr\u00f6n */\n';
            prompt += '--ic-danger: #ef4444;      /* R\u00f6d */\n';
            prompt += '--ic-warning: #f59e0b;     /* Orange */\n';
            prompt += '--ic-bg-light: #f8f9fc;    /* Ljus bakgrund */\n';
            prompt += '--ic-radius-sm/md/lg: 6/10/16px;\n';
            prompt += '```\n\n';

            prompt += '### Komponentklasser (ic-styles.css)\n';
            prompt += '- Knappar: `.ic-btn`, `.ic-btn-primary`, `.ic-btn-success`, `.ic-btn-danger`, `.ic-btn-ghost`\n';
            prompt += '- Kort: `.ic-card`, `.ic-card-header`, `.ic-card-body`, `.ic-card-footer`\n';
            prompt += '- Formul\u00e4r: `.ic-form-group`, `.ic-label`, `.ic-input`, `.ic-select`, `.ic-textarea`\n';
            prompt += '- Tabeller: `.ic-table`, `.ic-table-striped`, `.ic-table-hover`\n';
            prompt += '- Badges: `.ic-badge`, `.ic-badge-success`, `.ic-badge-danger`, `.ic-badge-warning`\n';
            prompt += '- Layout: `.ic-container`, `.ic-header`, `.ic-title`, `.ic-subtitle`\n';
            prompt += '- Feedback: `.ic-toast`, `.ic-spinner`, `.ic-modal`\n\n';

            prompt += '### ICUtils API (ic-utils.js)\n';
            prompt += '```javascript\n';
            prompt += 'ICUtils.notifyReady(moduleId)     // Meddela dashboard\n';
            prompt += 'ICUtils.showToast(msg, type)      // Visa toast\n';
            prompt += 'ICUtils.showDocPopup()             // \u00d6ppna modulens dokumentationspopup\n';
            prompt += 'ICUtils.formatNumber(num)          // "1 234 567"\n';
            prompt += 'ICUtils.formatDate(date)           // "2025-02-05"\n';
            prompt += 'ICUtils.getQueryParam(name)        // H\u00e4mta URL-param\n';
            prompt += 'ICUtils.saveToStorage(key, data)   // localStorage\n';
            prompt += 'ICUtils.loadFromStorage(key, def)  // localStorage\n';
            prompt += 'ICUtils.exportToCSV(data, fn, cols)\n';
            prompt += 'ICUtils.copyToClipboard(text)\n';
            prompt += 'ICUtils.debounce(func, wait)\n';
            prompt += 'ICUtils.createElement(tag, attrs, content)\n';
            prompt += '```\n\n';

            prompt += '### URL-struktur & API-anrop\n';
            prompt += 'Applikationen k\u00f6rs under kontexts\u00f6kv\u00e4gen `' + CTX + '`.\n';
            prompt += 'Moduler laddas i en iframe fr\u00e5n: `' + CTX + '/modules/{modul-mapp}/{entry-fil}`.\n\n';
            prompt += '**VIKTIGT:** Alla API-anrop fr\u00e5n en modul ska g\u00f6ras med **relativ s\u00f6kv\u00e4g** `../../api/...` (tv\u00e5 niv\u00e5er upp fr\u00e5n modulens mapp).\n';
            prompt += 'Cookies skickas automatiskt s\u00e5 sessionen \u00e4r autentiserad.\n\n';
            prompt += '```javascript\n';
            prompt += '// R\u00e4tt \u2014 relativ s\u00f6kv\u00e4g fr\u00e5n modules/{mapp}/{fil}.html\n';
            prompt += 'const resp = await fetch(\'../../api/servers\');\n\n';
            prompt += '// F\u00f6r m\u00e5nga endpoints \u2014 definiera en bas-konstant:\n';
            prompt += 'const API = \'../../api\';\n';
            prompt += 'const resp = await fetch(API + \'/email/templates\');\n';
            prompt += '```\n\n';

            prompt += '### Tillg\u00e4ngliga API-endpoints\n';
            prompt += 'Alla URL:er nedan \u00e4r relativa fr\u00e5n modulens mapp. Anv\u00e4nd `../../api/...` i fetch-anrop.\n\n';

            prompt += '#### Kundstatistik\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/customer-stats` | GET | Aggregerad statistik. `?from=&to=` |\n';
            prompt += '| `../../api/customer-stats/history` | GET | Dagliga snapshots. `?url=X&from=Y&to=Z` |\n';
            prompt += '| `../../api/customer-stats/import` | POST | Bulk-import (API-nyckel via `X-API-Key`) |\n\n';

            prompt += '#### E-post\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/email/templates` | GET | Lista mallar |\n';
            prompt += '| `../../api/email/templates` | POST | Skapa mall `{name, subject, bodyHtml}` |\n';
            prompt += '| `../../api/email/templates/{id}` | PUT/DELETE | Uppdatera/ta bort mall |\n';
            prompt += '| `../../api/email/send` | POST | Skicka utskick `{subject, bodyHtml, recipients:[]}` |\n';
            prompt += '| `../../api/email/history` | GET | Utskickshistorik |\n';
            prompt += '| `../../api/email/history/{id}` | GET | Detaljer + mottagarstatus |\n\n';

            prompt += '#### Kontakter (extern DB)\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/contacts/filters` | GET | Filterv\u00e4rden (ServerUrl, CompanyName, Language) |\n';
            prompt += '| `../../api/contacts/query` | POST | S\u00f6k kontakter `{filters:{...}}` |\n\n';

            prompt += '#### Servrar\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/servers` | GET | Serverlista + f\u00f6retag + maskinnamn |\n';
            prompt += '| `../../api/servers/health` | GET | H\u00e4lsokontroll (severity: ok/low/medium/severe) |\n\n';

            prompt += '#### Certifikat\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/certificates` | GET | Certifikatlista (daysLeft, isExpired, commonName) |\n\n';

            prompt += '#### Grupper & Widgets & Moduler\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/groups` | GET | Grupper + medlemsstatus |\n';
            prompt += '| `../../api/groups` | POST | G\u00e5 med/l\u00e4mna `{action:"join|leave", groupId:N}` |\n';
            prompt += '| `../../api/widgets` | GET | Gruppfiltrerad widgetlista |\n';
            prompt += '| `../../api/modules` | GET | Gruppfiltrerad modullista (inkl. aiSpecText) |\n\n';

            prompt += '#### Admin-only (kr\u00e4ver is_admin=1)\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/admin/users` | GET/POST | Anv\u00e4ndarlista / toggla admin |\n';
            prompt += '| `../../api/admin/config` | GET/POST | AppConfig CRUD |\n';
            prompt += '| `../../api/sync/configs` | CRUD | Datasynk-konfigurationer |\n';
            prompt += '| `../../api/sync/run` | POST | K\u00f6r synk manuellt |\n\n';

            prompt += '### Konventioner\n';
            prompt += '- const/let, template literals, async/await\n';
            prompt += '- 4 mellanslag, CSS-variabler f\u00f6r f\u00e4rger\n';

            navigator.clipboard.writeText(prompt).then(function() {
                var btn = event.target;
                var orig = btn.textContent;
                btn.textContent = '\u2705 Kopierad!';
                btn.style.background = '#10b981';
                setTimeout(function() {
                    btn.textContent = orig;
                    btn.style.background = '#5458F2';
                }, 2000);
            }).catch(function() {
                var win = window.open('', '_blank');
                win.document.write('<pre style="white-space:pre-wrap;font-family:monospace;padding:20px;">' + prompt.replace(/</g, '&lt;') + '</pre>');
            });
        }

        // Attach click handlers via event delegation (safe from inline XSS)
        document.addEventListener('click', function(e) {
            var btn = e.target.closest('.edit-module-btn');
            if (btn) {
                openEditModal(
                    btn.getAttribute('data-id'),
                    btn.getAttribute('data-name'),
                    btn.getAttribute('data-icon'),
                    btn.getAttribute('data-description'),
                    btn.getAttribute('data-category'),
                    btn.getAttribute('data-aispec')
                );
            }
        });

        function openEditModal(id, name, icon, description, category, aiSpec) {
            document.getElementById('editModuleId').value = id;
            document.getElementById('editName').value = name;
            document.getElementById('editIcon').value = icon;
            document.getElementById('editDescription').value = description;
            document.getElementById('editCategory').value = category;
            document.getElementById('editAiSpec').value = aiSpec;

            // Set group checkboxes
            var groupModuleIdEl = document.getElementById('groupEditModuleId');
            if (groupModuleIdEl) {
                groupModuleIdEl.value = id;
                var assignedGroups = moduleGroupMap[id] || [];
                var checkboxes = document.querySelectorAll('#groupCheckboxes .group-cb');
                checkboxes.forEach(function(cb) {
                    cb.checked = assignedGroups.indexOf(parseInt(cb.value)) !== -1;
                });
            }

            document.getElementById('editModal').classList.add('show');
        }

        function closeEditModal() {
            document.getElementById('editModal').classList.remove('show');
        }

        // Close modal on overlay click
        document.getElementById('editModal').addEventListener('click', function(e) {
            if (e.target === this) closeEditModal();
        });

        // Close modal on Escape
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') { closeEditModal(); closeUploadModal(); }
        });

        // ===== File Upload Modal =====
        document.addEventListener('click', function(e) {
            var btn = e.target.closest('.upload-file-btn');
            if (btn) {
                openUploadModal(btn.getAttribute('data-id'), btn.getAttribute('data-name'));
            }
        });

        function openUploadModal(moduleId, moduleName) {
            document.getElementById('uploadModuleId').value = moduleId;
            document.getElementById('uploadModuleName').textContent = moduleName;
            // Set form action with CSRF token in query string (multipart forms can't use body for CSRF)
            var csrfToken = document.querySelector('meta[name="csrf-token"]').content;
            document.getElementById('uploadForm').action = CTX + '/module/manage?_csrf=' + encodeURIComponent(csrfToken);
            // Reset file input
            document.getElementById('modalFileInput').value = '';
            document.getElementById('modalUploadIcon').textContent = '\uD83D\uDCE4';
            document.getElementById('modalUploadText').textContent = 'Klicka eller dra fil hit';
            document.getElementById('modalUploadFilename').style.display = 'none';
            document.getElementById('modalUploadArea').classList.remove('has-file');
            document.getElementById('uploadSubmitBtn').disabled = true;
            document.getElementById('uploadModal').classList.add('show');
        }

        function closeUploadModal() {
            document.getElementById('uploadModal').classList.remove('show');
        }

        document.getElementById('uploadModal').addEventListener('click', function(e) {
            if (e.target === this) closeUploadModal();
        });

        // File input change
        document.getElementById('modalFileInput').addEventListener('change', function() {
            var file = this.files[0];
            if (!file) return;
            var area = document.getElementById('modalUploadArea');
            var icon = document.getElementById('modalUploadIcon');
            var text = document.getElementById('modalUploadText');
            var fname = document.getElementById('modalUploadFilename');
            var submitBtn = document.getElementById('uploadSubmitBtn');

            icon.textContent = file.name.endsWith('.zip') ? '\uD83D\uDDC4\uFE0F' : '\u2705';
            text.textContent = 'Fil vald!';
            fname.textContent = file.name;
            fname.style.display = 'block';
            area.classList.add('has-file');
            submitBtn.disabled = false;
        });

        // Drag and drop on upload area
        var modalArea = document.getElementById('modalUploadArea');
        modalArea.addEventListener('dragover', function(e) {
            e.preventDefault();
            this.classList.add('dragover');
        });
        modalArea.addEventListener('dragleave', function() {
            this.classList.remove('dragover');
        });
        modalArea.addEventListener('drop', function(e) {
            e.preventDefault();
            this.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                document.getElementById('modalFileInput').files = e.dataTransfer.files;
                document.getElementById('modalFileInput').dispatchEvent(new Event('change'));
            }
        });
    </script>
    <script nonce="<%= request.getAttribute("cspNonce") %>">
    // If loaded inside dashboard iframe, adapt for embedded context
    if (window !== window.top) {
        var hdr = document.querySelector('.page-header');
        if (hdr) hdr.style.display = 'none';
        document.body.style.minHeight = 'auto';

        // Intercept "Öppna" links that point to dashboard.jsp?module=X
        document.querySelectorAll('.mod-action-btn').forEach(function(link) {
            var href = link.getAttribute('href') || '';
            var match = href.match(/dashboard\.jsp\?module=([^&]+)/);
            if (match) {
                link.addEventListener('click', function(e) {
                    e.preventDefault();
                    window.top.postMessage({ type: 'LOAD_MODULE', moduleId: match[1] }, window.location.origin);
                });
            }
        });

        // Intercept "Skapa ny modul" links
        document.querySelectorAll('.create-btn').forEach(function(link) {
            var href = link.getAttribute('href') || '';
            if (href.indexOf('/module/create') !== -1) {
                link.addEventListener('click', function(e) {
                    e.preventDefault();
                    window.top.postMessage({ type: 'LOAD_MANAGE_PAGE', pageId: 'create-module' }, window.location.origin);
                });
            }
        });
    }
    </script>
</body>
</html>
