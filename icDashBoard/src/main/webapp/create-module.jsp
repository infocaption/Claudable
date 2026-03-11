<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.model.User" %>
<%@ page import="com.infocaption.dashboard.model.Group" %>
<%@ page import="java.util.List" %>
<%@ page import="com.infocaption.dashboard.util.Esc" %>
<%
    User user = (User) session.getAttribute("user");
    String ctxPath = request.getContextPath();
    String error = (String) request.getAttribute("error");
    String savedName = request.getAttribute("name") != null ? (String) request.getAttribute("name") : "";
    String savedDesc = request.getAttribute("description") != null ? (String) request.getAttribute("description") : "";
    String savedSpec = request.getAttribute("aiSpecText") != null ? (String) request.getAttribute("aiSpecText") : "";
    List<Group> allGroups = (List<Group>) request.getAttribute("allGroups");
    if (allGroups == null) allGroups = new java.util.ArrayList<>();
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= session.getAttribute("csrfToken") != null ? session.getAttribute("csrfToken") : "" %>">
    <title>Skapa modul - InfoCaption Dashboard</title>
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

        .back-link {
            color: #5458F2;
            text-decoration: none;
            font-size: 0.9em;
            font-weight: 500;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .back-link:hover {
            text-decoration: underline;
        }

        .page-content {
            max-width: 800px;
            margin: 30px auto;
            padding: 0 20px;
        }

        .form-card {
            background: #fff;
            border-radius: 16px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.06);
            overflow: hidden;
        }

        .form-section {
            padding: 28px 32px;
            border-bottom: 1px solid #f0f0f0;
        }

        .form-section:last-child {
            border-bottom: none;
        }

        .form-section-title {
            font-size: 1.05em;
            font-weight: 600;
            color: #323232;
            margin-bottom: 18px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .step-number {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 26px;
            height: 26px;
            border-radius: 50%;
            background: #5458F2;
            color: #fff;
            font-size: 0.78em;
            font-weight: 700;
            flex-shrink: 0;
        }

        .workflow-banner {
            background: linear-gradient(135deg, #ede9fe 0%, #f0f0ff 100%);
            border: 1px solid #d4d5fc;
            border-radius: 12px;
            padding: 20px 24px;
            margin-bottom: 20px;
        }

        .workflow-banner h3 {
            font-size: 0.95em;
            font-weight: 700;
            color: #5458F2;
            margin-bottom: 10px;
        }

        .workflow-steps {
            display: flex;
            align-items: center;
            gap: 0;
            flex-wrap: wrap;
        }

        .workflow-step {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 0.82em;
            color: #323232;
            font-weight: 500;
        }

        .workflow-step .ws-num {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 20px;
            height: 20px;
            border-radius: 50%;
            background: #5458F2;
            color: #fff;
            font-size: 0.72em;
            font-weight: 700;
        }

        .workflow-arrow {
            margin: 0 8px;
            color: #bec0f9;
            font-size: 1.1em;
        }

        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 18px;
        }

        .form-group {
            margin-bottom: 18px;
        }

        .form-group label {
            display: block;
            font-size: 0.85em;
            font-weight: 600;
            color: #323232;
            margin-bottom: 6px;
        }

        .form-group label .hint {
            font-weight: 400;
            color: #6c757d;
        }

        .form-group input[type="text"],
        .form-group select,
        .form-group textarea {
            width: 100%;
            padding: 10px 14px;
            border: 1.5px solid #e5e7eb;
            border-radius: 10px;
            font-size: 0.9em;
            font-family: inherit;
            color: #323232;
            transition: border-color 0.2s;
            background: #fff;
        }

        .form-group input:focus,
        .form-group select:focus,
        .form-group textarea:focus {
            outline: none;
            border-color: #5458F2;
            box-shadow: 0 0 0 3px rgba(84, 88, 242, 0.1);
        }

        .form-group textarea {
            min-height: 100px;
            resize: vertical;
        }

        .form-group .spec-textarea {
            min-height: 250px;
            font-family: 'Consolas', 'Monaco', monospace;
            font-size: 0.85em;
            line-height: 1.6;
        }

        /* Emoji picker */
        .icon-picker {
            display: flex;
            gap: 8px;
            align-items: center;
        }

        .icon-preview {
            width: 44px;
            height: 44px;
            border: 1.5px solid #e5e7eb;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.5em;
            background: #f8f9fc;
        }

        .icon-input {
            flex: 1;
        }

        /* File upload area */
        .upload-area {
            border: 2px dashed #D4D5FC;
            border-radius: 12px;
            padding: 40px 20px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s;
            background: #fafafe;
        }

        .upload-area:hover,
        .upload-area.dragover {
            border-color: #5458F2;
            background: #f0f0ff;
        }

        .upload-area.has-file {
            border-color: #10b981;
            background: #f0fdf4;
        }

        .upload-icon {
            font-size: 2.5em;
            margin-bottom: 12px;
        }

        .upload-text {
            font-size: 0.95em;
            color: #323232;
            font-weight: 500;
            margin-bottom: 6px;
        }

        .upload-hint {
            font-size: 0.8em;
            color: #6c757d;
        }

        .upload-filename {
            margin-top: 10px;
            font-size: 0.85em;
            color: #10b981;
            font-weight: 600;
        }

        .file-input {
            display: none;
        }

        /* Visibility toggle */
        .visibility-options {
            display: flex;
            gap: 12px;
        }

        .visibility-option {
            flex: 1;
            padding: 14px 18px;
            border: 1.5px solid #e5e7eb;
            border-radius: 12px;
            cursor: pointer;
            transition: all 0.2s;
            text-align: center;
        }

        .visibility-option:hover {
            border-color: #5458F2;
        }

        .visibility-option.selected {
            border-color: #5458F2;
            background: #f0f0ff;
        }

        .visibility-option input {
            display: none;
        }

        .visibility-icon {
            font-size: 1.5em;
            margin-bottom: 6px;
        }

        .visibility-label {
            font-size: 0.85em;
            font-weight: 600;
            color: #323232;
        }

        .visibility-desc {
            font-size: 0.75em;
            color: #6c757d;
            margin-top: 3px;
        }

        /* (tabs removed — simplified UI) */

        /* Submit area */
        .submit-area {
            padding: 24px 32px;
            background: #fafafe;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .submit-btn {
            background: #5458F2;
            color: #fff;
            border: none;
            padding: 12px 32px;
            border-radius: 10px;
            font-size: 0.95em;
            font-weight: 600;
            cursor: pointer;
            font-family: inherit;
            transition: background 0.2s;
        }

        .submit-btn:hover {
            background: #4347d9;
        }

        .cancel-btn {
            background: none;
            border: 1px solid #e5e7eb;
            padding: 12px 24px;
            border-radius: 10px;
            font-size: 0.9em;
            color: #6c757d;
            cursor: pointer;
            font-family: inherit;
            text-decoration: none;
            transition: all 0.2s;
        }

        .cancel-btn:hover {
            background: #fee2e2;
            color: #ef4444;
            border-color: #ef4444;
        }

        .error-alert {
            background: #fee2e2;
            color: #ef4444;
            padding: 14px 20px;
            border-radius: 10px;
            margin-bottom: 20px;
            font-size: 0.9em;
            font-weight: 500;
        }

        /* Group selection */
        .group-section-note {
            font-size: 0.82em;
            color: #6c757d;
            margin-bottom: 14px;
        }

        .group-checkbox-list {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }

        .group-checkbox-item {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 8px 14px;
            border: 1.5px solid #e5e7eb;
            border-radius: 10px;
            cursor: pointer;
            transition: all 0.2s;
            font-size: 0.88em;
            user-select: none;
        }

        .group-checkbox-item:hover {
            border-color: #5458F2;
        }

        .group-checkbox-item.checked {
            border-color: #5458F2;
            background: #f0f0ff;
        }

        .group-checkbox-item input[type="checkbox"] {
            accent-color: #5458F2;
            width: 16px;
            height: 16px;
            cursor: pointer;
        }

        .group-checkbox-label {
            color: #323232;
            font-weight: 500;
        }

        .group-hidden-badge {
            font-size: 0.8em;
            opacity: 0.7;
        }

        @media (max-width: 600px) {
            .form-row {
                grid-template-columns: 1fr;
            }

            .visibility-options {
                flex-direction: column;
            }

            .form-section {
                padding: 20px 18px;
            }

            .submit-area {
                flex-direction: column-reverse;
                gap: 12px;
            }

            .submit-btn, .cancel-btn {
                width: 100%;
                text-align: center;
            }
        }
    </style>
</head>
<body>
    <div class="page-header">
        <h1>&#10133; Skapa ny modul</h1>
        <a href="<%= ctxPath %>/dashboard.jsp" class="back-link">&#8592; Tillbaka till dashboard</a>
    </div>

    <div class="page-content">
        <% if (error != null) { %>
            <div class="error-alert"><%= Esc.h((String) request.getAttribute("error")) %></div>
        <% } %>

        <div class="workflow-banner">
            <h3>&#129302; S&aring; skapar du en modul</h3>
            <div class="workflow-steps">
                <span class="workflow-step"><span class="ws-num">1</span> Fyll i namn &amp; beskrivning</span>
                <span class="workflow-arrow">&#8594;</span>
                <span class="workflow-step"><span class="ws-num">2</span> Skriv vad modulen ska g&ouml;ra</span>
                <span class="workflow-arrow">&#8594;</span>
                <span class="workflow-step"><span class="ws-num">3</span> Generera AI-prompt</span>
                <span class="workflow-arrow">&#8594;</span>
                <span class="workflow-step"><span class="ws-num">4</span> Klistra i Claude/ChatGPT</span>
                <span class="workflow-arrow">&#8594;</span>
                <span class="workflow-step"><span class="ws-num">5</span> Ladda upp HTML-filen</span>
            </div>
        </div>

        <form action="<%= ctxPath %>/module/create?_csrf=<%= session.getAttribute("csrfToken") %>" method="post" enctype="multipart/form-data" id="createForm">
            <div class="form-card">

                <!-- Module Info -->
                <div class="form-section">
                    <div class="form-section-title"><span class="step-number">1</span> Modulinformation</div>

                    <div class="form-group">
                        <label>Modulnamn <span class="hint">(obligatoriskt)</span></label>
                        <input type="text" name="name" placeholder="T.ex. Min Analysmodul" required value="<%= Esc.h((String) request.getAttribute("name")) %>">
                    </div>

                    <div class="form-row">
                        <div class="form-group">
                            <label>Ikon <span class="hint">(emoji)</span></label>
                            <div class="icon-picker">
                                <div class="icon-preview" id="iconPreview">&#128230;</div>
                                <input type="text" name="icon" class="icon-input" placeholder="&#128230;" maxlength="10" id="iconInput" value="&#128230;">
                            </div>
                        </div>

                        <div class="form-group">
                            <label>Kategori</label>
                            <select name="category">
                                <option value="tools">&#128295; Verktyg</option>
                                <option value="analytics">&#128200; Analys &amp; Rapporter</option>
                                <option value="admin">&#9881; Administration</option>
                            </select>
                        </div>
                    </div>

                    <div class="form-group">
                        <label>Beskrivning <span class="hint">(valfritt)</span></label>
                        <textarea name="description" placeholder="Beskriv vad modulen g&#246;r..." rows="3"><%= Esc.h((String) request.getAttribute("description")) %></textarea>
                    </div>
                </div>

                <!-- AI Spec (Step 2) -->
                <div class="form-section">
                    <div class="form-section-title"><span class="step-number">2</span> Beskriv vad modulen ska g&#246;ra</div>

                    <div class="form-group" style="margin-bottom: 12px;">
                        <label>Din beskrivning <span class="hint">(skriv fritt &mdash; som en best&auml;llning)</span></label>
                        <textarea name="aiSpecText" class="spec-textarea" id="specTextarea"
                                  placeholder="Beskriv vad modulen ska visa och g&#246;ra. T.ex:&#10;&#10;Jag vill ha en modul som visar en lista &#246;ver alla servrar med versionsnummer.&#10;Tabellen ska g&#229; att sortera och filtrera. Visa en f&#228;rgkodad badge baserat&#10;p&#229; version. Det ska finnas en knapp f&#246;r att exportera listan som CSV.&#10;&#10;Ju mer detaljer, desto b&#228;ttre resultat."><%= Esc.h((String) request.getAttribute("aiSpecText")) %></textarea>
                    </div>

                    <div style="margin-bottom: 16px;">
                        <button type="button" style="background: none; border: none; color: #5458F2; font-size: 0.82em; cursor: pointer; font-family: inherit; padding: 0; font-weight: 500;" onclick="toggleSpecTips()">&#128161; Tips f&#246;r bra beskrivningar <span id="specTipsArrow">&#9660;</span></button>
                        <div id="specTips" style="display: none; margin-top: 10px; padding: 14px; background: #f8f9fc; border-radius: 10px; font-size: 0.84em; line-height: 1.7; color: #323232;">
                            <ul style="margin: 0; padding-left: 20px;">
                                <li><strong>Vad ska visas?</strong> &mdash; Tabell, kort, graf, lista?</li>
                                <li><strong>Vilken data?</strong> &mdash; Servrar, kunder, certifikat, statistik? (API:er ing&#229;r automatiskt i prompten)</li>
                                <li><strong>Interaktioner?</strong> &mdash; Sortering, filtrering, s&#246;kning, export?</li>
                                <li><strong>Layout?</strong> &mdash; Sidebar, tabs, toolbar, modaler?</li>
                                <li><strong>S&#228;rskilda krav?</strong> &mdash; F&#228;rgkodning, badges, realtidsuppdatering?</li>
                            </ul>
                        </div>
                    </div>

                    <!-- Prompt Generator (Step 3) -->
                    <div style="padding: 20px; background: linear-gradient(135deg, #ede9fe 0%, #f0f0ff 100%); border-radius: 12px; border: 1px solid #d4d5fc;">
                        <div style="display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 12px;">
                            <div style="flex: 1; min-width: 200px;">
                                <div style="font-weight: 700; color: #5458F2; font-size: 0.95em; margin-bottom: 6px;"><span class="step-number" style="width: 22px; height: 22px; font-size: 0.7em;">3</span> Generera AI-prompt</div>
                                <div style="font-size: 0.82em; color: #4a4a6a; line-height: 1.5;">
                                    Knappen skapar en f&#228;rdig prompt med din beskrivning + all teknisk info.<br>
                                    <strong>Klistra in prompten i Claude eller ChatGPT</strong> &mdash; du f&#229;r tillbaka en HTML-fil att ladda upp nedan.
                                </div>
                            </div>
                            <button type="button" onclick="generateAIPrompt()" style="background: #5458F2; color: #fff; border: none; padding: 12px 24px; border-radius: 10px; font-size: 0.9em; font-weight: 600; cursor: pointer; font-family: inherit; white-space: nowrap; box-shadow: 0 2px 8px rgba(84,88,242,0.3);">&#128203; Kopiera AI-prompt</button>
                        </div>
                    </div>
                </div>

                <!-- File Upload (Step 4) -->
                <div class="form-section">
                    <div class="form-section-title"><span class="step-number">4</span> Ladda upp HTML-filen</div>
                    <div style="font-size: 0.82em; color: #6c757d; margin-bottom: 14px;">
                        Spara koden fr&#229;n AI:n som en <code>.html</code>-fil och ladda upp den h&#228;r.
                    </div>

                    <div class="upload-area" id="uploadArea" onclick="document.getElementById('fileInput').click()">
                        <div class="upload-icon" id="uploadIcon">&#128228;</div>
                        <div class="upload-text" id="uploadText">Klicka eller dra filer hit</div>
                        <div class="upload-hint">Accepterar .html eller .zip (max 50 MB)</div>
                        <div class="upload-filename" id="uploadFilename" style="display: none;"></div>
                    </div>
                    <input type="file" name="moduleFile" class="file-input" id="fileInput" accept=".html,.htm,.zip">
                </div>

                <!-- Visibility (Step 5) -->
                <div class="form-section">
                    <div class="form-section-title"><span class="step-number">5</span> Synlighet</div>

                    <div class="visibility-options">
                        <label class="visibility-option selected" id="opt-private" onclick="selectVisibility('private')">
                            <input type="radio" name="visibility" value="private" checked>
                            <div class="visibility-icon">&#128274;</div>
                            <div class="visibility-label">Privat</div>
                            <div class="visibility-desc">Bara du kan se modulen</div>
                        </label>

                        <label class="visibility-option" id="opt-shared" onclick="selectVisibility('shared')">
                            <input type="radio" name="visibility" value="shared">
                            <div class="visibility-icon">&#128101;</div>
                            <div class="visibility-label">Delad</div>
                            <div class="visibility-desc">Alla inloggade kan se modulen</div>
                        </label>
                    </div>
                </div>

                <!-- Group Visibility -->
                <div class="form-section" id="groupSection" style="display: none;">
                    <div class="form-section-title">&#128101; Gruppsynlighet</div>
                    <div class="group-section-note">
                        V&auml;lj vilka grupper som ska se modulen. Om ingen v&auml;ljs blir den synlig f&ouml;r alla.
                    </div>
                    <div class="group-checkbox-list">
                        <% for (Group g : allGroups) { %>
                        <label class="group-checkbox-item<%= "Alla".equals(g.getName()) ? " checked" : "" %>">
                            <input type="checkbox" class="group-cb" value="<%= g.getId() %>"
                                   onchange="updateGroupCheckbox(this)"
                                   <%= "Alla".equals(g.getName()) ? "checked" : "" %>>
                            <span class="group-checkbox-label">
                                <%= g.getIcon() != null ? g.getIcon() : "" %> <%= g.getName() %>
                                <% if (g.isHidden()) { %><span class="group-hidden-badge">&#128274;</span><% } %>
                            </span>
                        </label>
                        <% } %>
                    </div>
                    <input type="hidden" name="groupIds" id="groupIdsInput" value="">
                </div>

                <!-- Submit -->
                <div class="submit-area">
                    <a href="<%= ctxPath %>/dashboard.jsp" class="cancel-btn">Avbryt</a>
                    <button type="submit" class="submit-btn">&#128640; Skapa modul</button>
                </div>
            </div>
        </form>
    </div>

    <script nonce="<%= request.getAttribute("cspNonce") %>">
        var ctxPath = '<%= ctxPath %>';

        // Icon preview
        var iconInput = document.getElementById('iconInput');
        iconInput.addEventListener('input', function() {
            document.getElementById('iconPreview').textContent = this.value || '\uD83D\uDCE6';
        });

        // File upload
        var uploadArea = document.getElementById('uploadArea');
        var fileInput = document.getElementById('fileInput');

        fileInput.addEventListener('change', function() {
            updateFileDisplay(this.files[0]);
        });

        uploadArea.addEventListener('dragover', function(e) {
            e.preventDefault();
            this.classList.add('dragover');
        });

        uploadArea.addEventListener('dragleave', function() {
            this.classList.remove('dragover');
        });

        uploadArea.addEventListener('drop', function(e) {
            e.preventDefault();
            this.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                fileInput.files = e.dataTransfer.files;
                updateFileDisplay(e.dataTransfer.files[0]);
            }
        });

        function updateFileDisplay(file) {
            if (!file) return;
            var uploadIcon = document.getElementById('uploadIcon');
            var uploadText = document.getElementById('uploadText');
            var uploadFilename = document.getElementById('uploadFilename');

            if (file.name.endsWith('.zip')) {
                uploadIcon.textContent = '\uD83D\uDDC4\uFE0F';
            } else {
                uploadIcon.textContent = '\u2705';
            }
            uploadText.textContent = 'Fil vald!';
            uploadFilename.textContent = file.name + ' (' + formatSize(file.size) + ')';
            uploadFilename.style.display = 'block';
            uploadArea.classList.add('has-file');
        }

        function formatSize(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        }

        // Visibility toggle
        function selectVisibility(value) {
            document.querySelectorAll('.visibility-option').forEach(function(el) {
                el.classList.remove('selected');
            });
            document.getElementById('opt-' + value).classList.add('selected');
            document.querySelector('input[name="visibility"][value="' + value + '"]').checked = true;

            // Show/hide group section based on visibility
            var groupSection = document.getElementById('groupSection');
            if (value === 'shared') {
                groupSection.style.display = '';
                updateGroupIds();
            } else {
                groupSection.style.display = 'none';
                document.getElementById('groupIdsInput').value = '';
            }
        }

        // Group checkbox handling
        function updateGroupCheckbox(cb) {
            var item = cb.closest('.group-checkbox-item');
            if (cb.checked) {
                item.classList.add('checked');
            } else {
                item.classList.remove('checked');
            }
            updateGroupIds();
        }

        function updateGroupIds() {
            var checked = document.querySelectorAll('.group-cb:checked');
            var ids = [];
            checked.forEach(function(cb) {
                ids.push(cb.value);
            });
            document.getElementById('groupIdsInput').value = ids.join(',');
        }

        // Spec tips toggle
        function toggleSpecTips() {
            var tips = document.getElementById('specTips');
            var arrow = document.getElementById('specTipsArrow');
            if (tips.style.display === 'none') {
                tips.style.display = 'block';
                arrow.textContent = '\u25B2';
            } else {
                tips.style.display = 'none';
                arrow.textContent = '\u25BC';
            }
        }

        // Prompt Generator
        function generateAIPrompt() {
            var specText = document.getElementById('specTextarea').value.trim();
            var moduleName = document.querySelector('input[name="name"]').value.trim() || 'Ny modul';
            var moduleDesc = document.querySelector('textarea[name="description"]').value.trim();
            var moduleCategory = document.querySelector('select[name="category"]').value;

            var prompt = 'Skapa en InfoCaption Dashboard-modul \u00e5t mig. Ge mig tillbaka en komplett HTML-fil som jag kan ladda upp direkt.\n\n';
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
            prompt += '- Feedback: `.ic-toast`, `.ic-spinner`, `.ic-modal`\n';
            prompt += '- Alerts: `.ic-alert`, `.ic-alert-info`, `.ic-alert-success`, `.ic-alert-warning`\n\n';

            prompt += '### ICUtils API (ic-utils.js)\n';
            prompt += '```javascript\n';
            prompt += 'ICUtils.notifyReady(moduleId)     // Meddela dashboard att modulen \u00e4r klar\n';
            prompt += 'ICUtils.showToast(msg, type)      // Visa toast (success/error)\n';
            prompt += 'ICUtils.showDocPopup()             // \u00d6ppna modulens dokumentationspopup\n';
            prompt += 'ICUtils.formatNumber(num)          // "1 234 567"\n';
            prompt += 'ICUtils.formatDate(date)           // "2025-02-05"\n';
            prompt += 'ICUtils.getQueryParam(name)        // H\u00e4mta URL-param\n';
            prompt += 'ICUtils.saveToStorage(key, data)   // localStorage med JSON\n';
            prompt += 'ICUtils.loadFromStorage(key, def)  // localStorage h\u00e4mtning\n';
            prompt += 'ICUtils.exportToCSV(data, fn, cols)// CSV-export\n';
            prompt += 'ICUtils.copyToClipboard(text)      // Kopiera till urklipp\n';
            prompt += 'ICUtils.debounce(func, wait)       // Debounce-wrapper\n';
            prompt += 'ICUtils.createElement(tag, attrs, content) // Skapa DOM-element\n';
            prompt += '```\n\n';

            prompt += '### URL-struktur & API-anrop\n';
            prompt += 'Applikationen k\u00f6rs under kontexts\u00f6kv\u00e4gen `' + ctxPath + '`.\n';
            prompt += 'Moduler laddas i en iframe fr\u00e5n s\u00f6kv\u00e4gen: `' + ctxPath + '/modules/{modul-mapp}/{entry-fil}`.\n\n';
            prompt += '**VIKTIGT:** Alla API-anrop fr\u00e5n en modul ska g\u00f6ras med **relativ s\u00f6kv\u00e4g** `../../api/...` (tv\u00e5 niv\u00e5er upp fr\u00e5n modulens mapp).\n';
            prompt += 'Cookies skickas automatiskt s\u00e5 sessionen \u00e4r autentiserad.\n\n';
            prompt += '```javascript\n';
            prompt += '// R\u00e4tt \u2014 relativ s\u00f6kv\u00e4g fr\u00e5n modules/{mapp}/{fil}.html\n';
            prompt += 'const resp = await fetch(\'../../api/servers\');\n';
            prompt += 'const data = await resp.json();\n\n';
            prompt += '// F\u00f6r m\u00e5nga endpoints \u2014 definiera en bas-konstant:\n';
            prompt += 'const API = \'../../api\';\n';
            prompt += 'const resp = await fetch(API + \'/email/templates\');\n';
            prompt += '```\n\n';

            prompt += '### Tillg\u00e4ngliga API-endpoints\n';
            prompt += 'Alla URL:er nedan \u00e4r relativa till applikationens rot. I moduler anv\u00e4nds `../../api/...`.\n\n';

            prompt += '#### Kundstatistik\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/customer-stats` | GET | Aggregerad statistik (30d f\u00f6nster). Params: `?from=yyyy-MM-dd&to=yyyy-MM-dd` |\n';
            prompt += '| `../../api/customer-stats/history` | GET | Dagliga snapshots per server. Params: `?url=X&from=Y&to=Z&limit=N` |\n';
            prompt += '| `../../api/customer-stats/import` | POST | Bulk-import JSON-array (kr\u00e4ver API-nyckel via `X-API-Key` header) |\n\n';

            prompt += '#### E-post\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/email/templates` | GET | Lista anv\u00e4ndarens mallar |\n';
            prompt += '| `../../api/email/templates` | POST | Skapa mall. Body: `{name, subject, bodyHtml}` |\n';
            prompt += '| `../../api/email/templates/{id}` | PUT | Uppdatera mall |\n';
            prompt += '| `../../api/email/templates/{id}` | DELETE | Ta bort mall |\n';
            prompt += '| `../../api/email/send` | POST | Skicka utskick. Body: `{subject, bodyHtml, recipients:[], templateId}` |\n';
            prompt += '| `../../api/email/history` | GET | Utskickshistorik (senaste 100) |\n';
            prompt += '| `../../api/email/history/{id}` | GET | Detaljer + mottagarstatus |\n\n';

            prompt += '#### Kontakter (extern DB \u2014 smartassic)\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/contacts/filters` | GET | Filterv\u00e4rden (ServerUrl, CompanyName, Language etc.) |\n';
            prompt += '| `../../api/contacts/query` | POST | S\u00f6k kontakter med filter. Body: `{filters:{...}, maxResults}` |\n\n';

            prompt += '#### Servrar\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/servers` | GET | Serverlista med f\u00f6retag + maskinnamn + version |\n';
            prompt += '| `../../api/servers/health` | GET | Async h\u00e4lsokontroll p\u00e5 alla servrar (severity: ok/low/medium/severe) |\n\n';

            prompt += '#### Certifikat\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/certificates` | GET | Certifikatlista (daysLeft, isExpired, commonName, issuer, validTo) |\n\n';

            prompt += '#### Grupper\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/groups` | GET | Synliga grupper + medlemsstatus (isMember, memberCount) |\n';
            prompt += '| `../../api/groups` | POST | G\u00e5 med/l\u00e4mna grupp. Body: `{action:"join|leave", groupId:N}` |\n\n';

            prompt += '#### Widgets\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/widgets` | GET | Gruppfiltrerad widgetlista |\n\n';

            prompt += '#### Moduler\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/modules` | GET | Gruppfiltrerad modullista (id, name, icon, path, moduleType, groups, aiSpecText) |\n';
            prompt += '| `../../api/module/spec?id=X` | GET | AI-spec f\u00f6r en modul som markdown |\n\n';

            prompt += '#### Admin-only (kr\u00e4ver is_admin=1)\n';
            prompt += '| Endpoint | Metod | Beskrivning |\n';
            prompt += '|----------|-------|-------------|\n';
            prompt += '| `../../api/admin/users` | GET/POST | Anv\u00e4ndarlista / toggla admin-status |\n';
            prompt += '| `../../api/admin/config` | GET/POST | AppConfig-lista / uppdatera inst\u00e4llning |\n';
            prompt += '| `../../api/sync/configs` | CRUD | Datasynk-konfigurationer |\n';
            prompt += '| `../../api/sync/run` | POST | K\u00f6r synk manuellt |\n\n';

            prompt += '### Konventioner\n';
            prompt += '- `const`/`let` ist\u00e4llet f\u00f6r `var`\n';
            prompt += '- Template literals f\u00f6r HTML-generering\n';
            prompt += '- `async`/`await` f\u00f6r promises\n';
            prompt += '- 4 mellanslag indentering\n';
            prompt += '- CSS-variabler f\u00f6r f\u00e4rger (inga h\u00e5rdkodade hex)\n';

            // Copy to clipboard
            navigator.clipboard.writeText(prompt).then(function() {
                var btn = event.target;
                var orig = btn.textContent;
                btn.textContent = '\u2705 Kopierad! Klistra in i Claude/ChatGPT';
                btn.style.background = '#10b981';
                setTimeout(function() {
                    btn.textContent = orig;
                    btn.style.background = '#5458F2';
                }, 3000);
            }).catch(function() {
                // Fallback: open in new window
                var win = window.open('', '_blank');
                win.document.write('<pre style="white-space:pre-wrap;font-family:monospace;padding:20px;">' + prompt.replace(/</g, '&lt;') + '</pre>');
            });
        }

        // (tabs removed — simplified UI)
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
