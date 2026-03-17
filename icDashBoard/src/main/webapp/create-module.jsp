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
            min-height: 200px;
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

        /* Wizard Progress */
        .wizard-progress {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 28px 20px 24px;
            gap: 0;
        }

        .wizard-progress-step {
            display: flex;
            flex-direction: column;
            align-items: center;
            cursor: pointer;
            position: relative;
            z-index: 1;
        }

        .wizard-progress-step.disabled {
            cursor: not-allowed;
        }

        .wizard-progress-circle {
            width: 36px;
            height: 36px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 0.85em;
            font-weight: 700;
            border: 2.5px solid #e5e7eb;
            background: #fff;
            color: #6c757d;
            transition: all 0.3s;
        }

        .wizard-progress-step.active .wizard-progress-circle {
            border-color: #5458F2;
            background: #5458F2;
            color: #fff;
        }

        .wizard-progress-step.completed .wizard-progress-circle {
            border-color: #10b981;
            background: #10b981;
            color: #fff;
        }

        .wizard-progress-label {
            margin-top: 8px;
            font-size: 0.78em;
            font-weight: 600;
            color: #6c757d;
            transition: color 0.3s;
        }

        .wizard-progress-step.active .wizard-progress-label {
            color: #5458F2;
        }

        .wizard-progress-step.completed .wizard-progress-label {
            color: #10b981;
        }

        .wizard-progress-line {
            flex: 1;
            height: 3px;
            background: #e5e7eb;
            margin: 0 -4px;
            margin-bottom: 22px;
            min-width: 60px;
            transition: background 0.3s;
        }

        .wizard-progress-line.completed {
            background: #10b981;
        }

        /* Wizard steps */
        .wizard-step {
            display: none;
        }

        .wizard-step.active {
            display: block;
        }

        /* Wizard nav */
        .wizard-nav {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 20px 32px;
            border-top: 1px solid #f0f0f0;
            background: #fafafe;
        }

        .wizard-nav-btn {
            padding: 12px 28px;
            border-radius: 10px;
            font-size: 0.9em;
            font-weight: 600;
            cursor: pointer;
            font-family: inherit;
            transition: all 0.2s;
            border: none;
        }

        .wizard-nav-btn.primary {
            background: #5458F2;
            color: #fff;
        }

        .wizard-nav-btn.primary:hover {
            background: #4347d9;
        }

        .wizard-nav-btn.secondary {
            background: none;
            border: 1px solid #e5e7eb;
            color: #6c757d;
        }

        .wizard-nav-btn.secondary:hover {
            border-color: #5458F2;
            color: #5458F2;
        }

        .wizard-nav-btn.cancel:hover {
            background: #fee2e2;
            color: #ef4444;
            border-color: #ef4444;
        }

        .wizard-nav-btn.submit {
            background: #5458F2;
            color: #fff;
            padding: 12px 32px;
        }

        .wizard-nav-btn.submit:hover {
            background: #4347d9;
        }

        /* Endpoint cards grid */
        .endpoint-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 12px;
            margin-top: 12px;
        }

        .endpoint-card {
            border: 1.5px solid #e5e7eb;
            border-radius: 12px;
            padding: 16px;
            cursor: pointer;
            transition: all 0.2s;
            user-select: none;
        }

        .endpoint-card:hover {
            border-color: #5458F2;
            background: #fafaff;
        }

        .endpoint-card.selected {
            border-color: #5458F2;
            background: #f0f0ff;
            box-shadow: 0 0 0 3px rgba(84, 88, 242, 0.1);
        }

        .endpoint-card-icon {
            font-size: 1.5em;
            margin-bottom: 8px;
        }

        .endpoint-card-name {
            font-size: 0.88em;
            font-weight: 600;
            color: #323232;
            margin-bottom: 4px;
        }

        .endpoint-card-desc {
            font-size: 0.78em;
            color: #6c757d;
            line-height: 1.4;
        }

        .endpoint-section-label {
            font-size: 0.88em;
            font-weight: 600;
            color: #323232;
            margin-bottom: 6px;
            margin-top: 20px;
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

        .error-alert {
            background: #fee2e2;
            color: #ef4444;
            padding: 14px 20px;
            border-radius: 10px;
            margin-bottom: 20px;
            font-size: 0.9em;
            font-weight: 500;
        }

        .validation-error {
            color: #ef4444;
            font-size: 0.82em;
            margin-top: 4px;
            display: none;
        }

        .form-group.has-error input,
        .form-group.has-error textarea {
            border-color: #ef4444;
        }

        .form-group.has-error .validation-error {
            display: block;
        }

        /* Prompt area */
        .prompt-area {
            padding: 20px;
            background: linear-gradient(135deg, #ede9fe 0%, #f0f0ff 100%);
            border-radius: 12px;
            border: 1px solid #d4d5fc;
            margin-top: 20px;
        }

        .prompt-area-inner {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 12px;
        }

        .prompt-area-text {
            flex: 1;
            min-width: 200px;
        }

        .prompt-area-title {
            font-weight: 700;
            color: #5458F2;
            font-size: 0.95em;
            margin-bottom: 6px;
        }

        .prompt-area-desc {
            font-size: 0.82em;
            color: #4a4a6a;
            line-height: 1.5;
        }

        .prompt-copy-btn {
            background: #5458F2;
            color: #fff;
            border: none;
            padding: 12px 24px;
            border-radius: 10px;
            font-size: 0.9em;
            font-weight: 600;
            cursor: pointer;
            font-family: inherit;
            white-space: nowrap;
            box-shadow: 0 2px 8px rgba(84,88,242,0.3);
            transition: background 0.2s;
        }

        .prompt-copy-btn:hover {
            background: #4347d9;
        }

        @media (max-width: 700px) {
            .endpoint-grid {
                grid-template-columns: repeat(2, 1fr);
            }
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

            .wizard-nav {
                padding: 16px 18px;
            }

            .wizard-nav-btn {
                padding: 10px 18px;
                font-size: 0.85em;
            }

            .endpoint-grid {
                grid-template-columns: 1fr;
            }

            .wizard-progress {
                padding: 20px 10px 16px;
            }

            .wizard-progress-line {
                min-width: 30px;
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

        <!-- Wizard Progress Bar -->
        <div class="wizard-progress">
            <div class="wizard-progress-step active" data-step="1" onclick="progressClick(1)">
                <div class="wizard-progress-circle" id="progressCircle1">1</div>
                <div class="wizard-progress-label">Info</div>
            </div>
            <div class="wizard-progress-line" id="progressLine1"></div>
            <div class="wizard-progress-step disabled" data-step="2" onclick="progressClick(2)">
                <div class="wizard-progress-circle" id="progressCircle2">2</div>
                <div class="wizard-progress-label">Beskriv</div>
            </div>
            <div class="wizard-progress-line" id="progressLine2"></div>
            <div class="wizard-progress-step disabled" data-step="3" onclick="progressClick(3)">
                <div class="wizard-progress-circle" id="progressCircle3">3</div>
                <div class="wizard-progress-label">Publicera</div>
            </div>
        </div>

        <form action="<%= ctxPath %>/module/create?_csrf=<%= session.getAttribute("csrfToken") %>" method="post" enctype="multipart/form-data" id="createForm">
            <div class="form-card">

                <!-- Step 1: Module Info -->
                <div class="wizard-step active" data-step="1">
                    <div class="form-section">
                        <div class="form-section-title">Vad ska modulen heta?</div>

                        <div class="form-group" id="nameGroup">
                            <label>Modulnamn <span class="hint">(obligatoriskt)</span></label>
                            <input type="text" name="name" placeholder="T.ex. Min Analysmodul" value="<%= Esc.h(savedName) %>" id="nameInput">
                            <div class="validation-error" id="nameError">Modulnamn kr&#228;vs</div>
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
                            <textarea name="description" placeholder="Beskriv vad modulen g&#246;r..." rows="3"><%= Esc.h(savedDesc) %></textarea>
                        </div>
                    </div>

                    <div class="wizard-nav">
                        <button type="button" class="wizard-nav-btn secondary cancel" onclick="cancelWizard()">Avbryt</button>
                        <button type="button" class="wizard-nav-btn primary" onclick="goToStep(2)">N&#228;sta &#8594;</button>
                    </div>
                </div>

                <!-- Step 2: Describe & Generate -->
                <div class="wizard-step" data-step="2">
                    <div class="form-section">
                        <div class="form-section-title">Beskriv vad modulen ska g&#246;ra</div>

                        <div class="form-group" style="margin-bottom: 12px;">
                            <label>Din beskrivning <span class="hint">(skriv fritt &mdash; som en best&#228;llning)</span></label>
                            <textarea name="aiSpecText" class="spec-textarea" id="specTextarea"
                                      placeholder="Beskriv vad modulen ska visa och g&#246;ra. T.ex:&#10;&#10;Jag vill ha en modul som visar en lista &#246;ver alla servrar med versionsnummer.&#10;Tabellen ska g&#229; att sortera och filtrera. Visa en f&#228;rgkodad badge baserat&#10;p&#229; version. Det ska finnas en knapp f&#246;r att exportera listan som CSV.&#10;&#10;Ju mer detaljer, desto b&#228;ttre resultat."><%= Esc.h(savedSpec) %></textarea>
                        </div>

                        <div style="margin-bottom: 16px;">
                            <button type="button" style="background: none; border: none; color: #5458F2; font-size: 0.82em; cursor: pointer; font-family: inherit; padding: 0; font-weight: 500;" onclick="toggleSpecTips()">&#128161; Tips f&#246;r bra beskrivningar <span id="specTipsArrow">&#9660;</span></button>
                            <div id="specTips" style="display: none; margin-top: 10px; padding: 14px; background: #f8f9fc; border-radius: 10px; font-size: 0.84em; line-height: 1.7; color: #323232;">
                                <ul style="margin: 0; padding-left: 20px;">
                                    <li><strong>Vad ska visas?</strong> &mdash; Tabell, kort, graf, lista?</li>
                                    <li><strong>Vilken data?</strong> &mdash; Servrar, kunder, certifikat, statistik?</li>
                                    <li><strong>Interaktioner?</strong> &mdash; Sortering, filtrering, s&#246;kning, export?</li>
                                    <li><strong>Layout?</strong> &mdash; Sidebar, tabs, toolbar, modaler?</li>
                                    <li><strong>S&#228;rskilda krav?</strong> &mdash; F&#228;rgkodning, badges, realtidsuppdatering?</li>
                                </ul>
                            </div>
                        </div>

                        <!-- Endpoint Cards -->
                        <div class="endpoint-section-label">Vilka datak&#228;llor beh&#246;ver modulen? <span class="hint">(klicka f&#246;r att v&#228;lja)</span></div>
                        <div class="endpoint-grid" id="endpointGrid"></div>

                        <!-- Prompt Generator -->
                        <div class="prompt-area">
                            <div class="prompt-area-inner">
                                <div class="prompt-area-text">
                                    <div class="prompt-area-title">&#10024; Generera AI-prompt</div>
                                    <div class="prompt-area-desc">
                                        Knappen skapar en f&#228;rdig prompt med din beskrivning + valda datk&#228;llor.<br>
                                        <strong>Klistra in prompten i Claude eller ChatGPT</strong> &mdash; du f&#229;r tillbaka en HTML-fil att ladda upp i n&#228;sta steg.
                                    </div>
                                </div>
                                <button type="button" class="prompt-copy-btn" onclick="generateAIPrompt()">&#128203; Kopiera AI-prompt</button>
                            </div>
                        </div>
                    </div>

                    <div class="wizard-nav">
                        <button type="button" class="wizard-nav-btn secondary" onclick="goToStep(1)">&#8592; Tillbaka</button>
                        <button type="button" class="wizard-nav-btn primary" onclick="goToStep(3)">N&#228;sta &#8594;</button>
                    </div>
                </div>

                <!-- Step 3: Upload & Publish -->
                <div class="wizard-step" data-step="3">
                    <div class="form-section">
                        <div class="form-section-title">Ladda upp &amp; publicera</div>
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

                    <div class="form-section">
                        <div class="form-section-title">Synlighet</div>

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
                            V&#228;lj vilka grupper som ska se modulen. Om ingen v&#228;ljs blir den synlig f&#246;r alla.
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

                    <div class="wizard-nav">
                        <button type="button" class="wizard-nav-btn secondary" onclick="goToStep(2)">&#8592; Tillbaka</button>
                        <button type="submit" class="wizard-nav-btn submit">Skapa modul &#128640;</button>
                    </div>
                </div>

            </div>
        </form>
    </div>

    <script nonce="<%= request.getAttribute("cspNonce") %>">
        var ctxPath = '<%= ctxPath %>';
        var currentStep = 1;
        var highestVisited = 1;

        // Endpoint data
        var endpoints = [
            { id: 'customer-stats', icon: '\uD83D\uDCCA', name: 'Kundstatistik', desc: 'Statistik och historik per kundsite', selected: false },
            { id: 'email', icon: '\uD83D\uDCE7', name: 'E-post', desc: 'Mallar, utskick och historik', selected: false },
            { id: 'contacts', icon: '\uD83D\uDC65', name: 'Kontakter', desc: 'S\u00f6k kontakter fr\u00e5n SmartAssIC', selected: false },
            { id: 'servers', icon: '\uD83D\uDDA5\uFE0F', name: 'Servrar', desc: 'Lista, versioner och h\u00e4lsokoll', selected: false },
            { id: 'certificates', icon: '\uD83D\uDD12', name: 'Certifikat', desc: 'SSL-cert och utg\u00e5ngsdatum', selected: false },
            { id: 'groups', icon: '\uD83C\uDFF7\uFE0F', name: 'Grupper', desc: 'Grupper och medlemskap', selected: false },
            { id: 'widgets', icon: '\uD83D\uDCE6', name: 'Widgets', desc: 'Widgetlista', selected: false },
            { id: 'modules', icon: '\uD83E\uDDE9', name: 'Moduler', desc: 'Modullista och AI-specs', selected: false },
            { id: 'admin', icon: '\u2699\uFE0F', name: 'Admin', desc: 'Anv\u00e4ndare, config och datasynk', selected: false }
        ];

        // Prompt sections per endpoint
        var promptSections = {
            'customer-stats': '#### Kundstatistik\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/customer-stats` | GET | Aggregerad statistik (30d f\u00f6nster). Params: `?from=yyyy-MM-dd&to=yyyy-MM-dd` |\n| `../../api/customer-stats/history` | GET | Dagliga snapshots per server. Params: `?url=X&from=Y&to=Z&limit=N` |\n| `../../api/customer-stats/import` | POST | Bulk-import JSON-array (kr\u00e4ver API-nyckel via `X-API-Key` header) |\n',
            'email': '#### E-post\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/email/templates` | GET | Lista anv\u00e4ndarens mallar |\n| `../../api/email/templates` | POST | Skapa mall. Body: `{name, subject, bodyHtml}` |\n| `../../api/email/templates/{id}` | PUT | Uppdatera mall |\n| `../../api/email/templates/{id}` | DELETE | Ta bort mall |\n| `../../api/email/send` | POST | Skicka utskick. Body: `{subject, bodyHtml, recipients:[], templateId}` |\n| `../../api/email/history` | GET | Utskickshistorik (senaste 100) |\n| `../../api/email/history/{id}` | GET | Detaljer + mottagarstatus |\n',
            'contacts': '#### Kontakter (extern DB \u2014 smartassic)\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/contacts/filters` | GET | Filterv\u00e4rden (ServerUrl, CompanyName, Language etc.) |\n| `../../api/contacts/query` | POST | S\u00f6k kontakter med filter. Body: `{filters:{...}, maxResults}` |\n',
            'servers': '#### Servrar\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/servers` | GET | Serverlista med f\u00f6retag + maskinnamn + version |\n| `../../api/servers/health` | GET | Async h\u00e4lsokontroll p\u00e5 alla servrar (severity: ok/low/medium/severe) |\n',
            'certificates': '#### Certifikat\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/certificates` | GET | Certifikatlista (daysLeft, isExpired, commonName, issuer, validTo) |\n',
            'groups': '#### Grupper\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/groups` | GET | Synliga grupper + medlemsstatus (isMember, memberCount) |\n| `../../api/groups` | POST | G\u00e5 med/l\u00e4mna grupp. Body: `{action:"join|leave", groupId:N}` |\n',
            'widgets': '#### Widgets\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/widgets` | GET | Gruppfiltrerad widgetlista |\n',
            'modules': '#### Moduler\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/modules` | GET | Gruppfiltrerad modullista (id, name, icon, path, moduleType, groups, aiSpecText) |\n| `../../api/module/spec?id=X` | GET | AI-spec f\u00f6r en modul som markdown |\n',
            'admin': '#### Admin-only (kr\u00e4ver is_admin=1)\n| Endpoint | Metod | Beskrivning |\n|----------|-------|-------------|\n| `../../api/admin/users` | GET/POST | Anv\u00e4ndarlista / toggla admin-status |\n| `../../api/admin/config` | GET/POST | AppConfig-lista / uppdatera inst\u00e4llning |\n| `../../api/sync/configs` | CRUD | Datasynk-konfigurationer |\n| `../../api/sync/run` | POST | K\u00f6r synk manuellt |\n'
        };

        // Render endpoint cards
        function renderEndpointCards() {
            var grid = document.getElementById('endpointGrid');
            grid.innerHTML = '';
            endpoints.forEach(function(ep) {
                var card = document.createElement('div');
                card.className = 'endpoint-card' + (ep.selected ? ' selected' : '');
                card.setAttribute('data-id', ep.id);
                card.innerHTML = '<div class="endpoint-card-icon">' + ep.icon + '</div>' +
                    '<div class="endpoint-card-name">' + ep.name + '</div>' +
                    '<div class="endpoint-card-desc">' + ep.desc + '</div>';
                card.addEventListener('click', function() {
                    ep.selected = !ep.selected;
                    card.classList.toggle('selected', ep.selected);
                });
                grid.appendChild(card);
            });
        }

        // Cancel — navigate home via postMessage if in iframe, else redirect
        function cancelWizard() {
            if (window !== window.top) {
                window.parent.postMessage({ type: 'LOAD_MODULE', moduleId: 'home' }, window.location.origin);
            } else {
                window.location.href = ctxPath + '/dashboard.jsp';
            }
        }

        // Wizard navigation
        function goToStep(step) {
            if (step < 1 || step > 3) return;

            // Validate before going forward
            if (step > currentStep) {
                for (var s = currentStep; s < step; s++) {
                    if (!validateStep(s)) return;
                }
            }

            currentStep = step;
            if (step > highestVisited) highestVisited = step;

            // Show/hide steps
            document.querySelectorAll('.wizard-step').forEach(function(el) {
                el.classList.toggle('active', parseInt(el.getAttribute('data-step')) === step);
            });

            updateProgress();
        }

        function updateProgress() {
            for (var i = 1; i <= 3; i++) {
                var stepEl = document.querySelector('.wizard-progress-step[data-step="' + i + '"]');
                var circle = document.getElementById('progressCircle' + i);

                stepEl.classList.remove('active', 'completed', 'disabled');

                if (i === currentStep) {
                    stepEl.classList.add('active');
                    circle.textContent = i;
                } else if (i < currentStep) {
                    stepEl.classList.add('completed');
                    circle.textContent = '\u2713';
                } else {
                    if (i <= highestVisited) {
                        // Can click back to it
                    } else {
                        stepEl.classList.add('disabled');
                    }
                    circle.textContent = i;
                }
            }

            // Lines
            document.getElementById('progressLine1').classList.toggle('completed', currentStep > 1);
            document.getElementById('progressLine2').classList.toggle('completed', currentStep > 2);
        }

        function progressClick(step) {
            if (step > highestVisited) return;
            // Going back is always ok; going forward validates
            if (step <= currentStep) {
                currentStep = step;
                document.querySelectorAll('.wizard-step').forEach(function(el) {
                    el.classList.toggle('active', parseInt(el.getAttribute('data-step')) === step);
                });
                updateProgress();
            } else {
                goToStep(step);
            }
        }

        function validateStep(step) {
            if (step === 1) {
                var name = document.getElementById('nameInput').value.trim();
                var group = document.getElementById('nameGroup');
                if (!name) {
                    group.classList.add('has-error');
                    document.getElementById('nameInput').focus();
                    return false;
                }
                group.classList.remove('has-error');
            }
            return true;
        }

        // Clear validation on input
        document.getElementById('nameInput').addEventListener('input', function() {
            document.getElementById('nameGroup').classList.remove('has-error');
        });

        // Icon preview
        document.getElementById('iconInput').addEventListener('input', function() {
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

            // Only include selected endpoint sections
            var selectedEndpoints = endpoints.filter(function(ep) { return ep.selected; });

            if (selectedEndpoints.length > 0) {
                prompt += '### Tillg\u00e4ngliga API-endpoints\n';
                prompt += 'Alla URL:er nedan \u00e4r relativa till applikationens rot. I moduler anv\u00e4nds `../../api/...`.\n\n';
                selectedEndpoints.forEach(function(ep) {
                    if (promptSections[ep.id]) {
                        prompt += promptSections[ep.id] + '\n';
                    }
                });
            } else {
                // If none selected, include all (backwards compatible)
                prompt += '### Tillg\u00e4ngliga API-endpoints\n';
                prompt += 'Alla URL:er nedan \u00e4r relativa till applikationens rot. I moduler anv\u00e4nds `../../api/...`.\n\n';
                Object.keys(promptSections).forEach(function(key) {
                    prompt += promptSections[key] + '\n';
                });
            }

            prompt += '### Konventioner\n';
            prompt += '- `const`/`let` ist\u00e4llet f\u00f6r `var`\n';
            prompt += '- Template literals f\u00f6r HTML-generering\n';
            prompt += '- `async`/`await` f\u00f6r promises\n';
            prompt += '- 4 mellanslag indentering\n';
            prompt += '- CSS-variabler f\u00f6r f\u00e4rger (inga h\u00e5rdkodade hex)\n';

            // Copy to clipboard
            navigator.clipboard.writeText(prompt).then(function() {
                var btn = document.querySelector('.prompt-copy-btn');
                var orig = btn.innerHTML;
                btn.textContent = '\u2705 Kopierad! Klistra in i Claude/ChatGPT';
                btn.style.background = '#10b981';
                setTimeout(function() {
                    btn.innerHTML = orig;
                    btn.style.background = '';
                }, 3000);
            }).catch(function() {
                var win = window.open('', '_blank');
                win.document.write('<pre style="white-space:pre-wrap;font-family:monospace;padding:20px;">' + prompt.replace(/</g, '&lt;') + '</pre>');
            });
        }

        // Handle server error repopulation — if there's saved data, jump to step 3
        <% if (error != null) { %>
        (function() {
            highestVisited = 3;
            goToStep(3);
        })();
        <% } %>

        // Init
        renderEndpointCards();
    </script>
    <script nonce="<%= request.getAttribute("cspNonce") %>">
    // If loaded inside dashboard iframe, hide page header
    if (window !== window.top) {
        var hdr = document.querySelector('.page-header');
        if (hdr) hdr.style.display = 'none';
        document.body.style.minHeight = 'auto';
    }
    </script>
</body>
</html>
