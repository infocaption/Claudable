<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.infocaption.dashboard.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null || !user.isAdmin()) {
        response.sendRedirect(request.getContextPath() + "/dashboard.jsp");
        return;
    }
    String ctxPath = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= session.getAttribute("csrfToken") != null ? session.getAttribute("csrfToken") : "" %>">
    <title>Admin — InfoCaption Dashboard</title>
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
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="<%= ctxPath %>/shared/ic-styles.css">
    <style>
        .page-header {
            background: white;
            padding: 28px 32px;
            border-bottom: 1px solid #e5e7eb;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .page-header h1 { font-size: 1.6em; display: flex; align-items: center; gap: 10px; }
        .page-header .back-link { color: var(--ic-text-muted); text-decoration: none; font-size: 0.85em; }
        .page-header .back-link:hover { color: var(--ic-primary); }

        .tabs {
            display: flex;
            gap: 0;
            background: white;
            border-bottom: 2px solid #e5e7eb;
            padding: 0 32px;
        }
        .tab {
            padding: 14px 24px;
            font-weight: 500;
            color: var(--ic-text-muted);
            cursor: pointer;
            border-bottom: 3px solid transparent;
            margin-bottom: -2px;
            transition: all 0.15s;
            font-size: 0.9em;
        }
        .tab:hover { color: var(--ic-text-dark); }
        .tab.active { color: var(--ic-primary); border-bottom-color: var(--ic-primary); }

        .tab-content { display: none; padding: 24px 32px; max-width: 1200px; }
        .tab-content.active { display: block; }

        /* Users tab */
        .user-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; margin-top: 16px; }
        .user-card {
            background: white;
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            padding: 18px;
            display: flex;
            align-items: center;
            gap: 14px;
            transition: box-shadow 0.15s;
        }
        .user-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
        .user-avatar {
            width: 44px; height: 44px; border-radius: 50%;
            background: var(--ic-secondary); color: var(--ic-primary);
            display: flex; align-items: center; justify-content: center;
            font-weight: 600; font-size: 1.1em; flex-shrink: 0;
        }
        .user-avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
        .user-info { flex: 1; min-width: 0; }
        .user-name { font-weight: 600; font-size: 0.95em; }
        .user-email { color: var(--ic-text-muted); font-size: 0.8em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .user-meta { font-size: 0.75em; color: var(--ic-text-muted); margin-top: 2px; }

        /* Toggle switch */
        .toggle { position: relative; display: inline-block; width: 42px; height: 24px; flex-shrink: 0; }
        .toggle input { opacity: 0; width: 0; height: 0; }
        .toggle-slider {
            position: absolute; cursor: pointer; inset: 0; background: #d1d5db;
            border-radius: 24px; transition: 0.2s;
        }
        .toggle-slider:before {
            content: ""; position: absolute; height: 18px; width: 18px;
            left: 3px; bottom: 3px; background: white; border-radius: 50%; transition: 0.2s;
        }
        .toggle input:checked + .toggle-slider { background: var(--ic-primary); }
        .toggle input:checked + .toggle-slider:before { transform: translateX(18px); }
        .toggle input:disabled + .toggle-slider { opacity: 0.5; cursor: not-allowed; }

        /* Sync tab */
        .sync-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; margin-top: 16px; }
        .sync-card {
            background: white;
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            padding: 20px;
            transition: box-shadow 0.15s;
        }
        .sync-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
        .sync-card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
        .sync-card-title { font-weight: 600; font-size: 1em; }
        .sync-card-meta { font-size: 0.8em; color: var(--ic-text-muted); margin-top: 8px; }
        .sync-card-meta div { margin-bottom: 3px; }
        .sync-card-actions { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }

        .badge { display: inline-block; padding: 2px 8px; border-radius: 8px; font-size: 0.75em; font-weight: 600; }
        .badge-success { background: #d1fae5; color: #065f46; }
        .badge-failed { background: #fee2e2; color: #991b1b; }
        .badge-active { background: #dbeafe; color: #1e40af; }
        .badge-manual { background: #f3f4f6; color: #374151; }
        .badge-scheduled { background: #ede9fe; color: #5b21b6; }

        .btn { display: inline-flex; align-items: center; gap: 5px; padding: 7px 14px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.8em; font-weight: 500; cursor: pointer; background: white; font-family: inherit; transition: 0.15s; }
        .btn:hover { background: #f3f4f6; }
        .btn-primary { background: var(--ic-primary); color: white; border-color: var(--ic-primary); }
        .btn-primary:hover { background: var(--ic-primary-hover); }
        .btn-danger { color: #ef4444; border-color: #fca5a5; }
        .btn-danger:hover { background: #fee2e2; }
        .btn-sm { padding: 5px 10px; font-size: 0.75em; }

        /* Search */
        .toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
        .search-input {
            flex: 1; min-width: 200px; max-width: 400px; padding: 8px 12px;
            border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.85em; font-family: inherit;
        }
        .search-input:focus { outline: none; border-color: var(--ic-primary); }

        /* Modal */
        .modal-overlay {
            display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.4);
            z-index: 1000; align-items: center; justify-content: center;
        }
        .modal-overlay.open { display: flex; }
        .modal {
            background: white; border-radius: 16px; width: 90%; max-width: 700px;
            max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 60px rgba(0,0,0,0.15);
        }
        .modal-header {
            padding: 20px 24px; border-bottom: 1px solid #e5e7eb;
            display: flex; justify-content: space-between; align-items: center;
        }
        .modal-header h2 { font-size: 1.15em; }
        .modal-close { background: none; border: none; font-size: 1.3em; cursor: pointer; color: var(--ic-text-muted); }
        .modal-body { padding: 24px; }
        .modal-footer { padding: 16px 24px; border-top: 1px solid #e5e7eb; display: flex; gap: 10px; justify-content: flex-end; }

        .form-group { margin-bottom: 16px; }
        .form-group label { display: block; font-weight: 500; font-size: 0.85em; margin-bottom: 5px; }
        .form-group input, .form-group select, .form-group textarea {
            width: 100%; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px;
            font-size: 0.85em; font-family: inherit;
        }
        .form-group input:focus, .form-group select:focus { outline: none; border-color: var(--ic-primary); }
        .form-row { display: flex; gap: 12px; }
        .form-row .form-group { flex: 1; }
        .form-hint { font-size: 0.75em; color: var(--ic-text-muted); margin-top: 3px; }

        /* Field mapping */
        .mapping-grid { border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; }
        .mapping-header { display: grid; grid-template-columns: 1fr 30px 1fr 1fr 30px; gap: 0; padding: 8px 12px; background: #f8f9fa; font-weight: 600; font-size: 0.8em; color: var(--ic-text-muted); }
        .mapping-row { display: grid; grid-template-columns: 1fr 30px 1fr 1fr 30px; gap: 0; padding: 6px 12px; border-top: 1px solid #f0f0f0; align-items: center; }
        .mapping-row:hover { background: #f8f9fc; }
        .mapping-arrow { text-align: center; color: var(--ic-text-muted); }
        .mapping-remove { background: none; border: none; color: #ef4444; cursor: pointer; font-size: 1em; text-align: center; }
        .mapping-add { padding: 8px 12px; border-top: 1px solid #e5e7eb; }

        /* Alert */
        .alert { padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; font-size: 0.85em; }
        .alert-success { background: #d1fae5; color: #065f46; }
        .alert-error { background: #fee2e2; color: #991b1b; }
        .alert-info { background: #dbeafe; color: #1e40af; }

        /* History */
        .history-table { width: 100%; border-collapse: collapse; font-size: 0.8em; margin-top: 12px; }
        .history-table th { background: #f8f9fa; padding: 8px 10px; text-align: left; font-weight: 600; color: var(--ic-text-muted); }
        .history-table td { padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }

        .empty-state { text-align: center; padding: 40px; color: var(--ic-text-muted); }

        /* Config tab */
        .config-category { margin-bottom: 24px; }
        .config-category-title { font-size: 0.9em; font-weight: 600; color: var(--ic-primary); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 10px; padding-bottom: 6px; border-bottom: 2px solid var(--ic-secondary); }
        .config-table { width: 100%; border-collapse: collapse; font-size: 0.85em; }
        .config-table th { background: #f8f9fa; padding: 8px 12px; text-align: left; font-weight: 600; color: var(--ic-text-muted); font-size: 0.85em; }
        .config-table td { padding: 8px 12px; border-bottom: 1px solid #f0f0f0; vertical-align: top; }
        .config-table tr:hover { background: #f8f9fc; }
        .config-key { font-family: monospace; font-size: 0.9em; font-weight: 500; white-space: nowrap; }
        .config-value { font-family: monospace; font-size: 0.85em; color: var(--ic-text-dark); word-break: break-all; max-width: 400px; }
        .config-value.secret { color: var(--ic-text-muted); font-style: italic; }
        .config-desc { font-size: 0.8em; color: var(--ic-text-muted); }
        .config-edit-btn { background: none; border: none; cursor: pointer; padding: 3px 6px; border-radius: 4px; font-size: 0.85em; }
        .config-edit-btn:hover { background: #e5e7eb; }
        .config-meta { font-size: 0.75em; color: var(--ic-text-muted); }
        .secret-badge { display: inline-block; padding: 1px 6px; border-radius: 4px; font-size: 0.7em; background: #fee2e2; color: #991b1b; font-weight: 600; margin-left: 4px; }
        .badge-sso { background: #e0f2fe; color: #0369a1; }
        .badge-hidden { background: #f3e8ff; color: #6b21a8; }
        .badge-default { background: #d1fae5; color: #065f46; }
        .members-table { width: 100%; border-collapse: collapse; font-size: 0.85em; }
        .members-table th { background: #f8f9fa; padding: 8px 10px; text-align: left; font-weight: 600; color: var(--ic-text-muted); font-size: 0.85em; }
        .members-table td { padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }
        .members-table tr:hover { background: #f8f9fc; }

        /* Module file manager */
        .mf-table { width: 100%; border-collapse: collapse; font-size: 0.85em; margin-top: 8px; }
        .mf-table th { background: #f8f9fa; padding: 10px 12px; text-align: left; font-weight: 600; color: var(--ic-text-muted); font-size: 0.85em; }
        .mf-table td { padding: 8px 12px; border-bottom: 1px solid #f0f0f0; }
        .mf-table tr:hover { background: #f8f9fc; }
        .mf-table tr.mf-clickable { cursor: pointer; }
        .mf-table tr.mf-clickable:hover { background: #eef2ff; }
        .mf-file-icon { margin-right: 6px; }
        .mf-actions { display: flex; gap: 6px; justify-content: flex-end; }
        .mf-actions button { background: none; border: none; cursor: pointer; padding: 4px 6px; border-radius: 4px; font-size: 0.85em; }
        .mf-actions button:hover { background: #e5e7eb; }
        .mf-entry { display: inline-block; padding: 1px 6px; border-radius: 4px; font-size: 0.7em; background: #fef3c7; color: #92400e; font-weight: 600; margin-left: 4px; }
        .mf-size { color: var(--ic-text-muted); font-size: 0.9em; }
        .mf-type-badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 0.72em; font-weight: 600; text-transform: uppercase; }
        .mf-type-badge.system { background: #dbeafe; color: #1e40af; }
        .mf-type-badge.shared { background: #d1fae5; color: #065f46; }
        .mf-type-badge.private { background: #fef3c7; color: #92400e; }
    </style>
</head>
<body>
    <div class="page-header">
        <div>
            <a href="<%= ctxPath %>/dashboard.jsp" class="back-link">&larr; Tillbaka till Dashboard</a>
            <h1>🔐 Admin</h1>
        </div>
    </div>

    <div class="tabs">
        <div class="tab active" onclick="switchTab('users')">👤 Användare</div>
        <div class="tab" onclick="switchTab('groups')">👥 Grupper</div>
        <div class="tab" onclick="switchTab('sync')">🔄 Datasynk</div>
        <div class="tab" onclick="switchTab('widgets')">🔧 Widgets</div>
        <div class="tab" onclick="switchTab('config')">⚙️ Inställningar</div>
        <div class="tab" onclick="switchTab('modules')">📦 Moduler</div>
        <div class="tab" onclick="switchTab('servers')">📊 Kundstatistik</div>
        <div class="tab" onclick="switchTab('mcp')">🔌 MCP-servrar</div>
        <div class="tab" onclick="switchTab('kb')">📚 Kunskapsbas</div>
    </div>

    <!-- ============ USERS TAB ============ -->
    <div id="tab-users" class="tab-content active">
        <div class="toolbar">
            <input type="text" class="search-input" id="userSearch" placeholder="Sök användare...">
            <span id="userCount" style="color:var(--ic-text-muted);font-size:0.85em"></span>
        </div>
        <div id="userAlert"></div>
        <div class="user-grid" id="userGrid">
            <div class="empty-state">Laddar användare...</div>
        </div>
    </div>

    <!-- ============ GROUPS TAB ============ -->
    <div id="tab-groups" class="tab-content">
        <div class="toolbar">
            <input type="text" class="search-input" id="groupSearch" placeholder="Sök grupp...">
            <span id="groupCount" style="color:var(--ic-text-muted);font-size:0.85em"></span>
            <button class="btn btn-primary" onclick="openGroupModal()">+ Ny grupp</button>
        </div>
        <div id="groupAlert"></div>
        <div class="sync-grid" id="groupGrid">
            <div class="empty-state">Laddar grupper...</div>
        </div>
    </div>

    <!-- ============ GROUP MODAL ============ -->
    <div class="modal-overlay" id="groupModal">
        <div class="modal" style="max-width:550px">
            <div class="modal-header">
                <h2 id="groupModalTitle">Ny grupp</h2>
                <button class="modal-close" onclick="closeGroupModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="groupEditId">
                <div class="form-row">
                    <div class="form-group" style="flex:3">
                        <label>Namn</label>
                        <input type="text" id="groupName" placeholder="T.ex. Utveckling">
                    </div>
                    <div class="form-group" style="flex:1">
                        <label>Ikon (emoji)</label>
                        <input type="text" id="groupIcon" placeholder="👥" maxlength="4">
                    </div>
                </div>
                <div class="form-group">
                    <label>Beskrivning</label>
                    <textarea id="groupDesc" rows="2" placeholder="Kort beskrivning av gruppen"></textarea>
                </div>
                <div class="form-group" style="display:flex;align-items:center;gap:8px">
                    <label class="toggle" style="margin:0">
                        <input type="checkbox" id="groupHidden">
                        <span class="toggle-slider"></span>
                    </label>
                    <div>
                        <label style="margin:0;cursor:pointer" onclick="document.getElementById('groupHidden').click()">Dold grupp</label>
                        <div class="form-hint">Dolda grupper syns inte för icke-medlemmar och kan inte gås med i fritt</div>
                    </div>
                </div>
                <div class="form-group">
                    <label>SSO-avdelning (department)</label>
                    <input type="text" id="groupSsoDept" placeholder="T.ex. IT, Ekonomi, HR">
                    <div class="form-hint">Mappas mot SAML department-claim vid SSO-inloggning. Användare med matchande avdelning tilldelas automatiskt till denna grupp.</div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" onclick="closeGroupModal()">Avbryt</button>
                <button class="btn btn-primary" onclick="saveGroup()">Spara</button>
            </div>
        </div>
    </div>

    <!-- ============ GROUP MEMBERS MODAL ============ -->
    <div class="modal-overlay" id="groupMembersModal">
        <div class="modal" style="max-width:600px">
            <div class="modal-header">
                <h2 id="membersModalTitle">Medlemmar</h2>
                <button class="modal-close" onclick="closeGroupMembersModal()">&times;</button>
            </div>
            <div class="modal-body">
                <div id="membersAlert"></div>
                <div style="display:flex;gap:8px;margin-bottom:16px;align-items:end">
                    <div class="form-group" style="flex:1;margin-bottom:0">
                        <label>Lägg till medlem</label>
                        <select id="memberAddSelect" style="width:100%">
                            <option value="">Välj användare...</option>
                        </select>
                    </div>
                    <button class="btn btn-primary" onclick="addGroupMember()">Lägg till</button>
                </div>
                <div id="membersList"></div>
            </div>
        </div>
    </div>

    <!-- ============ SYNC TAB ============ -->
    <div id="tab-sync" class="tab-content">
        <div class="toolbar">
            <button class="btn btn-primary" onclick="openSyncModal()">+ Ny synk</button>
            <span id="syncCount" style="color:var(--ic-text-muted);font-size:0.85em"></span>
        </div>
        <div id="syncAlert"></div>
        <div class="sync-grid" id="syncGrid">
            <div class="empty-state">Laddar synk-konfigurationer...</div>
        </div>
    </div>

    <!-- ============ WIDGETS TAB ============ -->
    <div id="tab-widgets" class="tab-content">
        <div class="toolbar">
            <button class="btn btn-primary" onclick="openWidgetModal()">+ Ny custom widget</button>
            <span id="widgetCount" style="color:var(--ic-text-muted);font-size:0.85em"></span>
        </div>
        <div id="widgetAlert"></div>
        <div class="sync-grid" id="widgetGrid">
            <div class="empty-state">Laddar widgets...</div>
        </div>
    </div>

    <!-- ============ CONFIG TAB ============ -->
    <div id="tab-config" class="tab-content">
        <div class="toolbar">
            <input type="text" class="search-input" id="configSearch" placeholder="Sök inställning...">
            <span id="configCount" style="color:var(--ic-text-muted);font-size:0.85em"></span>
            <button class="btn" onclick="reloadConfig()" title="Uppdatera cache">🔄 Ladda om cache</button>
        </div>
        <div id="configAlert"></div>
        <div id="configContent">
            <div class="empty-state">Laddar inställningar...</div>
        </div>
    </div>

    <!-- ============ MODULES TAB ============ -->
    <div id="tab-modules" class="tab-content">
        <!-- Module list view -->
        <div id="modulesListView">
            <div class="toolbar">
                <input type="text" class="search-input" id="moduleSearch" placeholder="Sök modul...">
                <span id="moduleCount" style="color:var(--ic-text-muted);font-size:0.85em"></span>
                <select id="moduleTypeFilter" style="padding:8px 12px;border:1px solid #d1d5db;border-radius:var(--ic-radius-sm,6px);font-size:0.85em;background:#fff">
                    <option value="">Alla typer</option>
                    <option value="system">System</option>
                    <option value="shared">Delade</option>
                    <option value="private">Privata</option>
                </select>
            </div>
            <div id="moduleAlert"></div>
            <div class="sync-grid" id="moduleGrid">
                <div class="empty-state">Laddar moduler...</div>
            </div>
        </div>

        <!-- File browser view -->
        <div id="moduleFilesView" style="display:none">
            <div class="toolbar" style="flex-wrap:wrap">
                <button class="btn" onclick="backToModuleList()">← Tillbaka</button>
                <span id="moduleBreadcrumb" style="font-weight:600;font-size:0.95em"></span>
                <div style="margin-left:auto;display:flex;gap:8px;flex-wrap:wrap">
                    <button class="btn" onclick="downloadModuleZip()">📥 ZIP</button>
                    <button class="btn" onclick="downloadModuleSpec()" id="btnSpecDl">📄 Spec</button>
                    <label class="btn btn-primary" style="cursor:pointer;margin:0">
                        📤 Ladda upp
                        <input type="file" id="moduleFileUpload" style="display:none" onchange="uploadModuleFile(this)">
                    </label>
                    <button class="btn" onclick="openNewFileModal()">📝 Ny fil</button>
                    <button class="btn" onclick="openModuleMetadataModal()">✏️ Metadata</button>
                </div>
            </div>
            <div id="fileAlert"></div>
            <table class="mf-table" id="fileTable">
                <thead><tr><th>Namn</th><th style="width:100px">Storlek</th><th style="width:160px">Ändrad</th><th style="width:120px;text-align:right">Åtgärder</th></tr></thead>
                <tbody id="fileTableBody"></tbody>
            </table>
        </div>

        <!-- File editor view -->
        <div id="moduleEditorView" style="display:none">
            <div class="toolbar" style="flex-wrap:wrap">
                <button class="btn" onclick="backToFileList()">← Tillbaka</button>
                <span id="editorFilePath" style="font-weight:600;font-size:0.9em;font-family:'Cascadia Code','Fira Code',monospace"></span>
                <div style="margin-left:auto;display:flex;gap:8px;align-items:center">
                    <span id="editorStatus" style="font-size:0.8em;color:var(--ic-text-muted)"></span>
                    <button class="btn btn-primary" onclick="saveFileContent()">💾 Spara</button>
                </div>
            </div>
            <div id="editorAlert"></div>
            <textarea id="codeEditor" spellcheck="false" style="
                width:100%;height:calc(100vh - 220px);margin-top:8px;
                font-family:'Cascadia Code','Fira Code','Courier New',monospace;font-size:0.85em;
                padding:16px;border:1px solid #374151;border-radius:var(--ic-radius-md,10px);
                resize:vertical;tab-size:2;background:#1e1e1e;color:#d4d4d4;line-height:1.6;
                white-space:pre;overflow:auto;outline:none"></textarea>
        </div>
    </div>

    <!-- ============ SERVERS / CUSTOMER STATS TAB ============ -->
    <div id="tab-servers" class="tab-content">
        <div class="toolbar">
            <input type="text" class="search-input" id="serverSearch" placeholder="Sök server...">
            <span id="serverCount" style="color:var(--ic-text-muted);font-size:0.85em"></span>
        </div>
        <p style="color:var(--ic-text-muted);font-size:0.85em;margin:0 0 12px">
            Exkluderade servrar döljs från kundstatistik-vyn. Använd detta för beta-, test- och demomiljöer.
        </p>
        <div id="serverAlert"></div>
        <table class="history-table" id="serverTable">
            <thead>
                <tr>
                    <th>URL</th>
                    <th>Version</th>
                    <th>Senast sedd</th>
                    <th style="text-align:center">Exkluderad</th>
                </tr>
            </thead>
            <tbody id="serverTableBody">
                <tr><td colspan="4" class="empty-state">Laddar servrar...</td></tr>
            </tbody>
        </table>
    </div>

    <!-- ============ MCP TAB ============ -->
    <div id="tab-mcp" class="tab-content">
        <div class="toolbar">
            <button class="btn btn-primary" onclick="openMcpModal()">+ Ny MCP-server</button>
        </div>
        <div id="mcpAlert"></div>
        <div class="sync-grid" id="mcpGrid">
            <div class="empty-state">Laddar MCP-servrar...</div>
        </div>
        <h3 style="margin-top:32px;font-size:1em">Senaste MCP-anrop</h3>
        <table class="history-table" id="mcpAuditTable">
            <thead><tr><th>Tid</th><th>Användare</th><th>Server</th><th>Verktyg</th><th>Status</th><th>Tid (ms)</th></tr></thead>
            <tbody id="mcpAuditBody"></tbody>
        </table>
    </div>

    <!-- ============ KNOWLEDGE BASE TAB ============ -->
    <div id="tab-kb" class="tab-content">
        <div class="toolbar">
            <button class="btn btn-primary" onclick="openKbDocModal()">+ Nytt dokument</button>
            <button class="btn" onclick="openKbCollModal()" style="margin-left:8px">+ Ny samling</button>
            <input type="text" class="search-input" id="kbSearch" placeholder="Sök dokument..." style="margin-left:auto" oninput="filterKbDocs()">
        </div>
        <div id="kbAlert"></div>

        <h3 style="font-size:1em;margin-top:8px">Dokument</h3>
        <table class="history-table" id="kbDocTable">
            <thead><tr><th>Titel</th><th>Slug</th><th>Typ</th><th>Taggar</th><th>Uppdaterad</th><th style="width:100px"></th></tr></thead>
            <tbody id="kbDocBody"><tr><td colspan="6" class="empty-state">Laddar dokument...</td></tr></tbody>
        </table>

        <h3 style="font-size:1em;margin-top:32px">Samlingar (MCP-endpoints)</h3>
        <div class="sync-grid" id="kbCollGrid">
            <div class="empty-state">Laddar samlingar...</div>
        </div>
    </div>

    <!-- KB Document Modal -->
    <div class="modal-overlay" id="kbDocModal">
        <div class="modal" style="max-width:700px">
            <div class="modal-header">
                <h2 id="kbDocModalTitle">Nytt dokument</h2>
                <button class="modal-close" onclick="closeKbDocModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="kbDocEditId">
                <div class="form-group"><label>Titel</label><input type="text" id="kbDocTitle" placeholder="T.ex. API-guide v2" oninput="autoSlug()"></div>
                <div class="form-row">
                    <div class="form-group"><label>Slug</label><input type="text" id="kbDocSlug" placeholder="api-guide-v2"></div>
                    <div class="form-group"><label>Filtyp</label>
                        <select id="kbDocType"><option value="markdown">Markdown</option><option value="text">Text</option></select>
                    </div>
                </div>
                <div class="form-group"><label>Taggar (komma-separerade)</label><input type="text" id="kbDocTags" placeholder="t.ex. api, guide, intern"></div>
                <div class="form-group"><label>Innehåll</label><textarea id="kbDocContent" rows="16" style="font-family:monospace;font-size:0.85em;white-space:pre-wrap" placeholder="Skriv Markdown-innehåll här..."></textarea></div>
                <button class="btn btn-primary" onclick="saveKbDoc()" style="margin-top:12px">Spara dokument</button>
            </div>
        </div>
    </div>

    <!-- KB Collection Modal -->
    <div class="modal-overlay" id="kbCollModal">
        <div class="modal" style="max-width:500px">
            <div class="modal-header">
                <h2 id="kbCollModalTitle">Ny samling</h2>
                <button class="modal-close" onclick="closeKbCollModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="kbCollEditId">
                <div class="form-group"><label>Namn</label><input type="text" id="kbCollName" placeholder="T.ex. Support-dokumentation" oninput="autoPrefix()"></div>
                <div class="form-group"><label>Tool prefix (MCP-namnrymd)</label><input type="text" id="kbCollPrefix" placeholder="support_docs"></div>
                <div class="form-group"><label>Beskrivning</label><textarea id="kbCollDesc" rows="3" placeholder="Beskriv vad samlingen innehåller..."></textarea></div>
                <div class="form-group">
                    <label class="toggle-label">
                        <input type="checkbox" id="kbCollActive" checked>
                        <span>Aktiv (synlig som MCP-endpoint)</span>
                    </label>
                </div>
                <button class="btn btn-primary" onclick="saveKbColl()" style="margin-top:12px">Spara samling</button>
            </div>
        </div>
    </div>

    <!-- KB Link Documents Modal -->
    <div class="modal-overlay" id="kbLinkModal">
        <div class="modal" style="max-width:500px">
            <div class="modal-header">
                <h2 id="kbLinkModalTitle">Koppla dokument</h2>
                <button class="modal-close" onclick="closeKbLinkModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="kbLinkCollId">
                <div id="kbLinkList" style="max-height:400px;overflow-y:auto"></div>
                <button class="btn btn-primary" onclick="saveKbLinks()" style="margin-top:12px">Spara kopplingar</button>
            </div>
        </div>
    </div>

    <!-- ============ MCP SERVER MODAL ============ -->
    <div class="modal-overlay" id="mcpModal">
        <div class="modal" style="max-width:600px">
            <div class="modal-header">
                <h2 id="mcpModalTitle">Ny MCP-server</h2>
                <button class="modal-close" onclick="closeMcpModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="mcpServerId">
                <div class="form-group"><label>Namn</label><input type="text" id="mcpName" placeholder="t.ex. GitHub MCP"></div>
                <div class="form-row">
                    <div class="form-group"><label>Transport</label>
                        <select id="mcpTransport" onchange="toggleMcpTransport()">
                            <option value="http">HTTP</option>
                            <option value="stdio">Stdio (subprocess)</option>
                        </select>
                    </div>
                    <div class="form-group"><label>Verktygsprefix</label><input type="text" id="mcpPrefix" placeholder="auto-genereras"></div>
                </div>
                <div id="mcpHttpFields">
                    <div class="form-group"><label>Endpoint URL</label><input type="text" id="mcpEndpointUrl" placeholder="https://..."></div>
                    <div class="form-row">
                        <div class="form-group"><label>Auth-typ</label>
                            <select id="mcpAuthType" onchange="toggleMcpAuth()">
                                <option value="none">Ingen</option>
                                <option value="oauth">OAuth 2.0</option>
                                <option value="bearer">Bearer Token</option>
                                <option value="api_key">API Key</option>
                                <option value="basic">Basic Auth</option>
                            </select>
                        </div>
                    </div>
                    <div id="mcpAuthFields"></div>
                </div>
                <div id="mcpStdioFields" style="display:none">
                    <div class="form-group"><label>Kommando</label><input type="text" id="mcpCommand" placeholder="t.ex. npx"></div>
                    <div class="form-group"><label>Argument (JSON-array)</label><input type="text" id="mcpCommandArgs" placeholder='["-y","@modelcontextprotocol/server-memory"]'></div>
                </div>
                <div class="form-group" style="display:flex;align-items:center;gap:8px;margin-top:8px">
                    <label class="toggle"><input type="checkbox" id="mcpActive" checked><span class="toggle-slider"></span></label>
                    <span style="font-size:0.85em">Aktiv</span>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" onclick="closeMcpModal()">Avbryt</button>
                <button class="btn btn-primary" onclick="saveMcpServer()">Spara</button>
            </div>
        </div>
    </div>

    <!-- ============ MODULE METADATA MODAL ============ -->
    <div class="modal-overlay" id="moduleMetadataModal">
        <div class="modal" style="max-width:700px">
            <div class="modal-header">
                <h2>Redigera modul</h2>
                <button class="modal-close" onclick="closeModuleMetadataModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="moduleMetaId">
                <div style="display:flex;gap:12px">
                    <div class="form-group" style="flex:3"><label>Namn</label><input type="text" id="moduleMetaName"></div>
                    <div class="form-group" style="flex:1"><label>Ikon</label><input type="text" id="moduleMetaIcon" maxlength="4"></div>
                </div>
                <div style="display:flex;gap:12px">
                    <div class="form-group" style="flex:1"><label>Kategori</label><input type="text" id="moduleMetaCategory"></div>
                    <div class="form-group" style="flex:1"><label>Version</label><input type="text" id="moduleMetaVersion"></div>
                    <div class="form-group" style="flex:1"><label>Badge</label><input type="text" id="moduleMetaBadge" placeholder="t.ex. BETA"></div>
                </div>
                <div class="form-group"><label>Beskrivning</label><textarea id="moduleMetaDesc" rows="2"></textarea></div>
                <div class="form-group">
                    <label>AI Spec (Markdown)</label>
                    <textarea id="moduleMetaAiSpec" rows="8" style="font-family:monospace;font-size:0.85em"></textarea>
                    <div class="form-hint">Modulens specifikation. Visas i doc-popup och kan laddas ner som .md-fil.</div>
                </div>
                <div class="form-group">
                    <label>Gruppsynlighet</label>
                    <div id="moduleMetaGroups" style="display:flex;flex-wrap:wrap;gap:8px;margin-top:4px"></div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" onclick="closeModuleMetadataModal()">Avbryt</button>
                <button class="btn btn-primary" onclick="saveModuleMetadata()">Spara</button>
            </div>
        </div>
    </div>

    <!-- ============ NEW FILE MODAL ============ -->
    <div class="modal-overlay" id="newFileModal">
        <div class="modal" style="max-width:450px">
            <div class="modal-header">
                <h2>Skapa ny fil</h2>
                <button class="modal-close" onclick="closeNewFileModal()">&times;</button>
            </div>
            <div class="modal-body">
                <div class="form-group">
                    <label>Filnamn</label>
                    <input type="text" id="newFileName" placeholder="t.ex. style.css eller lib/utils.js">
                    <div class="form-hint">Tillåtna typer: .html .css .js .json .png .jpg .gif .svg .txt .md m.fl.</div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" onclick="closeNewFileModal()">Avbryt</button>
                <button class="btn btn-primary" onclick="createNewFile()">Skapa</button>
            </div>
        </div>
    </div>

    <!-- ============ CONFIG EDIT MODAL ============ -->
    <div class="modal-overlay" id="configModal">
        <div class="modal" style="max-width:550px">
            <div class="modal-header">
                <h2>Redigera inställning</h2>
                <button class="modal-close" onclick="closeConfigModal()">&times;</button>
            </div>
            <div class="modal-body">
                <div class="form-group">
                    <label>Nyckel</label>
                    <input type="text" id="configEditKey" readonly style="background:#f3f4f6;cursor:not-allowed">
                </div>
                <div class="form-group">
                    <label id="configEditDesc">Beskrivning</label>
                    <div class="form-hint" id="configEditDescText"></div>
                </div>
                <div class="form-group">
                    <label>Värde</label>
                    <textarea id="configEditValue" rows="3" style="font-family:monospace;font-size:0.85em"></textarea>
                    <div class="form-hint" id="configEditHint"></div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" onclick="closeConfigModal()">Avbryt</button>
                <button class="btn btn-primary" onclick="saveConfigValue()">Spara</button>
            </div>
        </div>
    </div>

    <!-- ============ SYNC MODAL ============ -->
    <div class="modal-overlay" id="syncModal">
        <div class="modal">
            <div class="modal-header">
                <h2 id="syncModalTitle">Ny synkkonfiguration</h2>
                <button class="modal-close" onclick="closeSyncModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="syncEditId">

                <div class="form-group">
                    <label>Namn</label>
                    <input type="text" id="syncName" placeholder="T.ex. SuperOffice Companies">
                </div>

                <div class="form-group">
                    <label>Käll-URL (REST API)</label>
                    <input type="text" id="syncUrl" placeholder="https://api.example.com/data">
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label>Autentisering</label>
                        <select id="syncAuthType" onchange="toggleAuthFields()">
                            <option value="none">Ingen</option>
                            <option value="api_key">API-nyckel</option>
                            <option value="bearer">Bearer Token</option>
                            <option value="basic">Basic Auth</option>
                        </select>
                    </div>
                    <div class="form-group" id="authField1Group" style="display:none">
                        <label id="authField1Label">Header-namn</label>
                        <input type="text" id="authField1" placeholder="">
                    </div>
                    <div class="form-group" id="authField2Group" style="display:none">
                        <label id="authField2Label">Värde</label>
                        <input type="text" id="authField2" placeholder="">
                    </div>
                </div>

                <div class="form-group">
                    <label>JSON Root Path</label>
                    <input type="text" id="syncRootPath" placeholder="T.ex. data.users (lämna tomt om svaret är en array)">
                    <div class="form-hint">Punkt-separerad sökväg till arrayen i JSON-svaret</div>
                </div>

                <div style="margin-bottom:16px">
                    <button class="btn" id="testBtn" onclick="testConnection()">🔍 Testa anslutning</button>
                    <span id="testResult" style="margin-left:10px;font-size:0.85em"></span>
                </div>

                <div id="testFieldsSection" style="display:none">
                    <div class="alert alert-info" id="testFieldsInfo"></div>
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label>Måltabell</label>
                        <select id="syncTargetTable" onchange="loadTableColumns()">
                            <option value="">Välj tabell...</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Schema (minuter, 0=manuell)</label>
                        <select id="syncSchedule">
                            <option value="0">Manuell</option>
                            <option value="5">Var 5:e minut</option>
                            <option value="10">Var 10:e minut</option>
                            <option value="30">Var 30:e minut</option>
                            <option value="60">Varje timme</option>
                        </select>
                    </div>
                </div>

                <div class="form-group" style="display:flex;align-items:center;gap:8px">
                    <label class="toggle" style="margin:0">
                        <input type="checkbox" id="syncUpdateOnly">
                        <span class="toggle-slider"></span>
                    </label>
                    <div>
                        <label style="margin:0;cursor:pointer" onclick="document.getElementById('syncUpdateOnly').click()">Bara uppdatera (skapa inga nya rader)</label>
                        <div class="form-hint">Använd UPDATE istället för INSERT/UPSERT — matchar på ID-fältet och uppdaterar bara befintliga rader</div>
                    </div>
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label>ID-fält (källa / JSON)</label>
                        <select id="syncIdSource"><option value="">Välj...</option></select>
                    </div>
                    <div class="form-group">
                        <label>ID-fält (mål / DB-kolumn)</label>
                        <select id="syncIdTarget"><option value="">Välj...</option></select>
                    </div>
                </div>

                <div class="form-group">
                    <label>Fältmappning</label>
                    <div class="mapping-grid" id="mappingGrid">
                        <div class="mapping-header">
                            <div>JSON-fält</div><div></div><div>DB-kolumn</div><div>FK Lookup</div><div></div>
                        </div>
                        <div id="mappingRows"></div>
                        <div class="mapping-add">
                            <button class="btn btn-sm" onclick="addMappingRow()">+ Lägg till mappning</button>
                        </div>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" onclick="closeSyncModal()">Avbryt</button>
                <button class="btn btn-primary" onclick="saveSyncConfig()">Spara</button>
            </div>
        </div>
    </div>

    <!-- ============ WIDGET MODAL ============ -->
    <div class="modal-overlay" id="widgetModal">
        <div class="modal" style="max-width:750px">
            <div class="modal-header">
                <h2 id="widgetModalTitle">Ny custom widget</h2>
                <button class="modal-close" onclick="closeWidgetModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="widgetEditId">
                <div class="form-row">
                    <div class="form-group" style="flex:3">
                        <label>Namn</label>
                        <input type="text" id="widgetName" placeholder="Min widget">
                    </div>
                    <div class="form-group" style="flex:1">
                        <label>Ikon (emoji)</label>
                        <input type="text" id="widgetIcon" placeholder="📊" maxlength="4">
                    </div>
                </div>
                <div class="form-group">
                    <label>Beskrivning</label>
                    <input type="text" id="widgetDesc" placeholder="Kort beskrivning av widgeten">
                </div>
                <div class="form-group">
                    <label>HTML</label>
                    <textarea id="widgetHtml" rows="4" style="font-family:monospace;font-size:0.85em" placeholder='<span class="widget-value">Mitt innehåll</span>'></textarea>
                    <div class="form-hint">HTML som renderas i widget-chippen. Använd <code>&lt;span class="widget-value"&gt;</code> för texten.</div>
                </div>
                <div class="form-group">
                    <label>JavaScript</label>
                    <textarea id="widgetJs" rows="8" style="font-family:monospace;font-size:0.85em" placeholder="// Variabler: container (DOM-element), chip (parent element)
// Exempel:
fetch('/api/servers')
  .then(r => r.json())
  .then(data => {
    container.innerHTML = '<span class=&quot;widget-value&quot;>' + data.length + ' servrar</span>';
  });"></textarea>
                    <div class="form-hint">JavaScript som körs i widget-kontexten. <code>container</code> = inneh&aring;lls-element, <code>chip</code> = parent chip-element. Du kan använda <code>chip.className = 'widget-chip widget-ok'</code> för färgkodning.</div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Uppdateringsintervall (sekunder, 0 = aldrig)</label>
                        <input type="number" id="widgetRefresh" value="0" min="0">
                    </div>
                    <div class="form-group" style="display:flex;align-items:center;gap:8px;padding-top:22px">
                        <label class="toggle" style="margin:0">
                            <input type="checkbox" id="widgetActive" checked>
                            <span class="toggle-slider"></span>
                        </label>
                        <label style="margin:0;cursor:pointer" onclick="document.getElementById('widgetActive').click()">Aktiv</label>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" onclick="closeWidgetModal()">Avbryt</button>
                <button class="btn btn-primary" onclick="saveWidget()">Spara</button>
            </div>
        </div>
    </div>

    <!-- ============ HISTORY MODAL ============ -->
    <div class="modal-overlay" id="historyModal">
        <div class="modal">
            <div class="modal-header">
                <h2>Körhistorik</h2>
                <button class="modal-close" onclick="closeHistoryModal()">&times;</button>
            </div>
            <div class="modal-body" id="historyContent">Laddar...</div>
        </div>
    </div>

<script nonce="<%= request.getAttribute("cspNonce") %>">
const CTX = '<%= ctxPath %>';
let allUsers = [];
let allSyncConfigs = [];
let allConfigEntries = [];
let allWidgets = [];
let detectedJsonFields = [];
let detectedDbColumns = [];
let allGroups = [];
let allGroupUsers = [];
let groupsLoaded = false;
let currentMembersGroupId = 0;
let allModulesAdmin = [];
let modulesLoaded = false;
let currentModuleId = null;
let currentModuleData = null;
let currentModuleFiles = [];
let allMcpServers = [];
let mcpLoaded = false;
let allStatServers = [];
let serversLoaded = false;

// ========== TAB SWITCHING ==========
function switchTab(tab) {
    const tabs = ['users', 'groups', 'sync', 'widgets', 'config', 'modules', 'servers', 'mcp', 'kb'];
    document.querySelectorAll('.tab').forEach((t, i) => {
        t.classList.toggle('active', tabs[i] === tab);
    });
    tabs.forEach(t => {
        const el = document.getElementById('tab-' + t);
        if (el) el.classList.toggle('active', t === tab);
    });
    if (tab === 'config' && allConfigEntries.length === 0) loadConfigEntries();
    if (tab === 'widgets' && allWidgets.length === 0) loadWidgets();
    if (tab === 'groups' && !groupsLoaded) { loadGroups(); groupsLoaded = true; }
    if (tab === 'modules' && !modulesLoaded) { loadAdminModules(); modulesLoaded = true; }
    if (tab === 'servers' && !serversLoaded) { loadStatServers(); serversLoaded = true; }
    if (tab === 'mcp' && !mcpLoaded) { loadMcpServers(); loadMcpAudit(); mcpLoaded = true; }
    if (tab === 'kb' && !kbLoaded) { loadKbDocs(); loadKbColls(); kbLoaded = true; }
}

// ========== USERS TAB ==========
async function loadUsers() {
    try {
        const resp = await fetch(CTX + '/api/admin/users');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allUsers = await resp.json();
        renderUsers();
    } catch (e) {
        document.getElementById('userGrid').innerHTML = '<div class="empty-state" style="color:#ef4444">Kunde inte ladda användare: ' + e.message + '</div>';
    }
}

function renderUsers() {
    const query = (document.getElementById('userSearch').value || '').toLowerCase();
    const filtered = allUsers.filter(u =>
        (u.fullName || '').toLowerCase().includes(query) ||
        (u.email || '').toLowerCase().includes(query) ||
        (u.username || '').toLowerCase().includes(query)
    );

    document.getElementById('userCount').textContent = filtered.length + ' av ' + allUsers.length + ' användare';

    if (filtered.length === 0) {
        document.getElementById('userGrid').innerHTML = '<div class="empty-state">Inga användare hittades</div>';
        return;
    }

    const currentUserId = <%= user.getId() %>;

    document.getElementById('userGrid').innerHTML = filtered.map(u => {
        const initials = (u.fullName || u.username || '?').split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
        const avatarHtml = u.profilePictureUrl
            ? '<img src="' + esc(u.profilePictureUrl) + '" alt="">'
            : initials;
        const isSelf = u.id === currentUserId;
        const lastLogin = u.lastLogin ? new Date(u.lastLogin).toLocaleDateString('sv-SE') : 'Aldrig';

        const badges = (u.isAdmin ? ' <span class="badge badge-active">Admin</span>' : '') +
                        (u.hasApiAccess ? ' <span class="badge badge-scheduled">🔑 API</span>' : '');

        return '<div class="user-card">' +
            '<div class="user-avatar">' + avatarHtml + '</div>' +
            '<div class="user-info">' +
                '<div class="user-name">' + esc(u.fullName || u.username) + badges + '</div>' +
                '<div class="user-email">' + esc(u.email) + '</div>' +
                '<div class="user-meta">Senast inloggad: ' + lastLogin + '</div>' +
            '</div>' +
            '<div style="display:flex;flex-direction:column;align-items:flex-end;gap:6px">' +
                '<div style="display:flex;align-items:center;gap:6px" title="' + (isSelf ? 'Kan inte ändra egen admin-status' : 'Admin-behörighet') + '">' +
                    '<span style="font-size:0.7em;color:var(--ic-text-muted)">Admin</span>' +
                    '<label class="toggle">' +
                        '<input type="checkbox" ' + (u.isAdmin ? 'checked' : '') + ' ' + (isSelf ? 'disabled' : '') +
                        ' onchange="toggleAdmin(' + u.id + ', this.checked)">' +
                        '<span class="toggle-slider"></span>' +
                    '</label>' +
                '</div>' +
                '<div style="display:flex;align-items:center;gap:6px" title="API-åtkomst (kan generera API-tokens)">' +
                    '<span style="font-size:0.7em;color:var(--ic-text-muted)">API 🔑</span>' +
                    '<label class="toggle">' +
                        '<input type="checkbox" ' + (u.hasApiAccess ? 'checked' : '') +
                        ' onchange="toggleApiAccess(' + u.id + ', this.checked)">' +
                        '<span class="toggle-slider"></span>' +
                    '</label>' +
                '</div>' +
                (isSelf ? '' : '<button class="btn btn-sm btn-danger" onclick="deleteUser(' + u.id + ', \'' + esc(u.fullName || u.username) + '\')" title="Ta bort användare">🗑️</button>') +
            '</div>' +
        '</div>';
    }).join('');
}

async function toggleAdmin(userId, isAdmin) {
    try {
        const resp = await fetch(CTX + '/api/admin/users', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId, isAdmin })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');

        // Update local state
        const u = allUsers.find(u => u.id === userId);
        if (u) u.isAdmin = isAdmin;
        renderUsers();

        showAlert('userAlert', 'success', 'Admin-status uppdaterad');
    } catch (e) {
        showAlert('userAlert', 'error', 'Fel: ' + e.message);
        loadUsers(); // reload to reset toggle state
    }
}

async function toggleApiAccess(userId, hasApiAccess) {
    try {
        const resp = await fetch(CTX + '/api/admin/users/api-access', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId, hasApiAccess })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');

        // Update local state
        const u = allUsers.find(u => u.id === userId);
        if (u) u.hasApiAccess = hasApiAccess;
        renderUsers();

        const msg = hasApiAccess ? 'API-åtkomst aktiverad' : 'API-åtkomst inaktiverad (alla tokens återkallade)';
        showAlert('userAlert', 'success', msg);
    } catch (e) {
        showAlert('userAlert', 'error', 'Fel: ' + e.message);
        loadUsers();
    }
}

async function deleteUser(userId, name) {
    if (!confirm('Vill du permanent ta bort användaren "' + name + '"? Detta kan inte ångras.')) return;
    try {
        const resp = await fetch(CTX + '/api/admin/users/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');
        showAlert('userAlert', 'success', 'Användare borttagen: ' + name);
        loadUsers();
    } catch (e) {
        showAlert('userAlert', 'error', 'Fel: ' + e.message);
    }
}

// ========== GROUPS TAB ==========
async function loadGroups() {
    try {
        const resp = await fetch(CTX + '/api/admin/groups');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json();
        allGroups = data.groups || [];
        allGroupUsers = data.users || [];
        renderGroups();
    } catch (e) {
        document.getElementById('groupGrid').innerHTML = '<div class="empty-state" style="color:#ef4444">Kunde inte ladda grupper: ' + e.message + '</div>';
    }
}

function renderGroups() {
    const query = (document.getElementById('groupSearch').value || '').toLowerCase();
    const filtered = allGroups.filter(g =>
        (g.name || '').toLowerCase().includes(query) ||
        (g.description || '').toLowerCase().includes(query) ||
        (g.ssoDepartment || '').toLowerCase().includes(query)
    );

    document.getElementById('groupCount').textContent = filtered.length + ' av ' + allGroups.length + ' grupper';

    if (filtered.length === 0) {
        document.getElementById('groupGrid').innerHTML = '<div class="empty-state">Inga grupper hittades</div>';
        return;
    }

    document.getElementById('groupGrid').innerHTML = filtered.map(g => {
        const badges = [];
        if (g.isDefault) badges.push('<span class="badge badge-default">Standard</span>');
        if (g.isHidden) badges.push('<span class="badge badge-hidden">Dold</span>');
        if (g.ssoDepartment) badges.push('<span class="badge badge-sso">SSO: ' + esc(g.ssoDepartment) + '</span>');

        const isDefault = g.isDefault;

        return '<div class="sync-card">' +
            '<div class="sync-card-header">' +
                '<div class="sync-card-title">' + esc(g.icon) + ' ' + esc(g.name) + '</div>' +
                '<div>' + badges.join(' ') + '</div>' +
            '</div>' +
            '<div class="sync-card-meta">' +
                '<div>👤 ' + g.memberCount + ' medlem' + (g.memberCount !== 1 ? 'mar' : '') + '</div>' +
                (g.description ? '<div>📝 ' + esc(g.description) + '</div>' : '') +
            '</div>' +
            '<div class="sync-card-actions">' +
                '<button class="btn btn-sm" onclick="openGroupMembersModal(' + g.id + ')">👥 Medlemmar</button>' +
                (!isDefault ? '<button class="btn btn-sm" onclick="openGroupModal(' + g.id + ')">✏️ Redigera</button>' : '') +
                (!isDefault ? '<button class="btn btn-sm btn-danger" onclick="deleteGroup(' + g.id + ', \'' + esc(g.name) + '\')">🗑️</button>' : '') +
            '</div>' +
        '</div>';
    }).join('');
}

function openGroupModal(editId) {
    document.getElementById('groupEditId').value = editId || '';
    document.getElementById('groupName').value = '';
    document.getElementById('groupIcon').value = '';
    document.getElementById('groupDesc').value = '';
    document.getElementById('groupHidden').checked = false;
    document.getElementById('groupSsoDept').value = '';
    document.getElementById('groupModalTitle').textContent = editId ? 'Redigera grupp' : 'Ny grupp';

    if (editId) {
        const g = allGroups.find(g => g.id === editId);
        if (g) {
            document.getElementById('groupName').value = g.name || '';
            document.getElementById('groupIcon').value = g.icon || '';
            document.getElementById('groupDesc').value = g.description || '';
            document.getElementById('groupHidden').checked = g.isHidden || false;
            document.getElementById('groupSsoDept').value = g.ssoDepartment || '';
        }
    }

    document.getElementById('groupModal').classList.add('open');
}

function closeGroupModal() {
    document.getElementById('groupModal').classList.remove('open');
}

async function saveGroup() {
    const editId = document.getElementById('groupEditId').value;
    const body = {
        name: document.getElementById('groupName').value,
        icon: document.getElementById('groupIcon').value || '👥',
        description: document.getElementById('groupDesc').value,
        isHidden: document.getElementById('groupHidden').checked,
        ssoDepartment: document.getElementById('groupSsoDept').value || null
    };

    if (!body.name) {
        alert('Namn krävs');
        return;
    }

    if (editId) body.id = parseInt(editId);

    try {
        const resp = await fetch(CTX + '/api/admin/groups', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');
        closeGroupModal();
        showAlert('groupAlert', 'success', editId ? 'Grupp uppdaterad' : 'Grupp skapad');
        loadGroups();
    } catch (e) {
        alert('Fel: ' + e.message);
    }
}

async function deleteGroup(id, name) {
    if (!confirm('Vill du ta bort gruppen "' + name + '"? Alla medlemskap och modulkopplingar tas bort.')) return;
    try {
        const resp = await fetch(CTX + '/api/admin/groups?id=' + id, { method: 'DELETE' });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');
        showAlert('groupAlert', 'success', 'Grupp borttagen: ' + name);
        loadGroups();
    } catch (e) {
        showAlert('groupAlert', 'error', 'Fel: ' + e.message);
    }
}

function openGroupMembersModal(groupId) {
    currentMembersGroupId = groupId;
    const g = allGroups.find(g => g.id === groupId);
    if (!g) return;

    document.getElementById('membersModalTitle').textContent = 'Medlemmar — ' + (g.icon || '') + ' ' + g.name;

    // Populate add-member dropdown (exclude current members)
    const memberIds = new Set((g.members || []).map(m => m.id));
    const select = document.getElementById('memberAddSelect');
    select.innerHTML = '<option value="">Välj användare...</option>' +
        allGroupUsers
            .filter(u => !memberIds.has(u.id))
            .map(u => '<option value="' + u.id + '">' + esc(u.fullName || u.email) + ' (' + esc(u.email) + ')</option>')
            .join('');

    // Render members list
    renderMembersList(g);

    document.getElementById('groupMembersModal').classList.add('open');
}

function renderMembersList(g) {
    const members = g.members || [];

    if (g.isDefault) {
        document.getElementById('membersList').innerHTML =
            '<div class="alert alert-info">Alla aktiva användare är implicita medlemmar i standardgruppen.</div>';
        return;
    }

    if (members.length === 0) {
        document.getElementById('membersList').innerHTML =
            '<div class="empty-state" style="padding:16px">Inga medlemmar</div>';
        return;
    }

    let html = '<table class="members-table"><thead><tr><th>Namn</th><th>E-post</th><th style="width:50px"></th></tr></thead><tbody>';
    members.forEach(m => {
        html += '<tr>' +
            '<td>' + esc(m.fullName || '-') + '</td>' +
            '<td>' + esc(m.email || '-') + '</td>' +
            '<td><button class="btn btn-sm btn-danger" onclick="removeGroupMember(' + g.id + ', ' + m.id + ', \'' + esc(m.fullName || m.email) + '\')" title="Ta bort">✕</button></td>' +
        '</tr>';
    });
    html += '</tbody></table>';
    document.getElementById('membersList').innerHTML = html;
}

function closeGroupMembersModal() {
    document.getElementById('groupMembersModal').classList.remove('open');
    currentMembersGroupId = 0;
}

async function addGroupMember() {
    const userId = parseInt(document.getElementById('memberAddSelect').value);
    if (!userId || !currentMembersGroupId) return;

    try {
        const resp = await fetch(CTX + '/api/admin/groups/members', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ groupId: currentMembersGroupId, userId: userId, action: 'add' })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');

        // Reload groups and refresh the modal
        await loadGroups();
        openGroupMembersModal(currentMembersGroupId);
    } catch (e) {
        showAlert('membersAlert', 'error', 'Fel: ' + e.message);
    }
}

async function removeGroupMember(groupId, userId, name) {
    if (!confirm('Ta bort ' + name + ' från gruppen?')) return;
    try {
        const resp = await fetch(CTX + '/api/admin/groups/members', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ groupId: groupId, userId: userId, action: 'remove' })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');

        await loadGroups();
        openGroupMembersModal(groupId);
    } catch (e) {
        showAlert('membersAlert', 'error', 'Fel: ' + e.message);
    }
}

// ========== SYNC TAB ==========
async function loadSyncConfigs() {
    try {
        const resp = await fetch(CTX + '/api/sync/configs');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allSyncConfigs = await resp.json();
        renderSyncConfigs();
    } catch (e) {
        document.getElementById('syncGrid').innerHTML = '<div class="empty-state" style="color:#ef4444">Kunde inte ladda: ' + e.message + '</div>';
    }
}

function renderSyncConfigs() {
    document.getElementById('syncCount').textContent = allSyncConfigs.length + ' konfigurationer';

    if (allSyncConfigs.length === 0) {
        document.getElementById('syncGrid').innerHTML = '<div class="empty-state">Inga synk-konfigurationer. Klicka "+ Ny synk" för att skapa en.</div>';
        return;
    }

    document.getElementById('syncGrid').innerHTML = allSyncConfigs.map(c => {
        const statusBadge = c.lastRunStatus === 'success' ? '<span class="badge badge-success">OK</span>' :
                            c.lastRunStatus === 'failed' ? '<span class="badge badge-failed">Fel</span>' : '';
        const scheduleBadge = c.scheduleMinutes > 0
            ? '<span class="badge badge-scheduled">Var ' + c.scheduleMinutes + ' min</span>'
            : '<span class="badge badge-manual">Manuell</span>';
        const updateOnlyBadge = c.updateOnly ? ' <span class="badge" style="background:#fef3c7;color:#92400e">Bara uppdatera</span>' : '';
        const lastRun = c.lastRunAt ? new Date(c.lastRunAt).toLocaleString('sv-SE') : 'Aldrig';

        return '<div class="sync-card">' +
            '<div class="sync-card-header">' +
                '<div class="sync-card-title">' + esc(c.name) + '</div>' +
                '<div>' + statusBadge + ' ' + scheduleBadge + updateOnlyBadge + '</div>' +
            '</div>' +
            '<div class="sync-card-meta">' +
                '<div>🔗 ' + esc(truncUrl(c.sourceUrl)) + '</div>' +
                '<div>📋 ' + esc(c.targetTable) + ' (matchning: ' + esc(c.idFieldSource) + ' → ' + esc(c.idFieldTarget) + ')</div>' +
                '<div>🕐 Senaste körning: ' + lastRun + (c.lastRunCount > 0 ? ' (' + c.lastRunCount + ' poster)' : '') + '</div>' +
                '<div>👤 Skapad av: ' + esc(c.creatorName || '-') + '</div>' +
            '</div>' +
            '<div class="sync-card-actions">' +
                '<button class="btn btn-sm" onclick="runSync(' + c.id + ')">▶ Kör nu</button>' +
                '<button class="btn btn-sm" onclick="openSyncModal(' + c.id + ')">✏️ Redigera</button>' +
                '<button class="btn btn-sm" onclick="openHistoryModal(' + c.id + ')">📜 Historik</button>' +
                '<button class="btn btn-sm btn-danger" onclick="deleteSync(' + c.id + ', \'' + esc(c.name) + '\')">🗑️</button>' +
            '</div>' +
        '</div>';
    }).join('');
}

async function runSync(configId) {
    showAlert('syncAlert', 'info', 'Kör synk...');
    try {
        const resp = await fetch(CTX + '/api/sync/run?configId=' + configId, { method: 'POST' });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');
        showAlert('syncAlert', 'success', 'Synk slutförd (historik-id: ' + data.historyId + ')');
        loadSyncConfigs();
    } catch (e) {
        showAlert('syncAlert', 'error', 'Synk-fel: ' + e.message);
    }
}

async function deleteSync(id, name) {
    if (!confirm('Radera synk-konfiguration "' + name + '"?')) return;
    try {
        const resp = await fetch(CTX + '/api/sync/configs?id=' + id, { method: 'DELETE' });
        if (!resp.ok) throw new Error('Failed');
        showAlert('syncAlert', 'success', 'Borttagen: ' + name);
        loadSyncConfigs();
    } catch (e) {
        showAlert('syncAlert', 'error', 'Fel: ' + e.message);
    }
}

// ========== SYNC MODAL ==========
async function openSyncModal(editId) {
    detectedJsonFields = [];
    detectedDbColumns = [];

    // Reset form
    document.getElementById('syncEditId').value = editId || '';
    document.getElementById('syncName').value = '';
    document.getElementById('syncUrl').value = '';
    document.getElementById('syncAuthType').value = 'none';
    document.getElementById('authField1').value = '';
    document.getElementById('authField2').value = '';
    document.getElementById('syncRootPath').value = '';
    document.getElementById('syncTargetTable').value = '';
    document.getElementById('syncSchedule').value = '0';
    document.getElementById('syncUpdateOnly').checked = false;
    document.getElementById('syncIdSource').innerHTML = '<option value="">Välj...</option>';
    document.getElementById('syncIdTarget').innerHTML = '<option value="">Välj...</option>';
    document.getElementById('mappingRows').innerHTML = '';
    document.getElementById('testResult').textContent = '';
    document.getElementById('testFieldsSection').style.display = 'none';
    toggleAuthFields();

    document.getElementById('syncModalTitle').textContent = editId ? 'Redigera synk' : 'Ny synkkonfiguration';

    // Load allowed tables
    try {
        const resp = await fetch(CTX + '/api/sync/tables');
        const tables = await resp.json();
        const sel = document.getElementById('syncTargetTable');
        sel.innerHTML = '<option value="">Välj tabell...</option>';
        tables.forEach(t => { sel.innerHTML += '<option value="' + t + '">' + t + '</option>'; });
    } catch (e) { /* ignore */ }

    // If editing, load config data
    if (editId) {
        const config = allSyncConfigs.find(c => c.id === editId);
        if (config) {
            document.getElementById('syncName').value = config.name;
            document.getElementById('syncUrl').value = config.sourceUrl;
            document.getElementById('syncAuthType').value = config.authType || 'none';
            document.getElementById('syncRootPath').value = config.jsonRootPath || '';
            document.getElementById('syncTargetTable').value = config.targetTable;
            document.getElementById('syncSchedule').value = config.scheduleMinutes;
            document.getElementById('syncUpdateOnly').checked = config.updateOnly || false;

            // Parse auth config
            if (config.authConfig) {
                const ac = config.authConfig;
                if (config.authType === 'api_key') {
                    document.getElementById('authField1').value = jsonVal(ac, 'headerName') || '';
                    document.getElementById('authField2').value = jsonVal(ac, 'keyValue') || '';
                } else if (config.authType === 'bearer') {
                    document.getElementById('authField1').value = jsonVal(ac, 'token') || '';
                } else if (config.authType === 'basic') {
                    document.getElementById('authField1').value = jsonVal(ac, 'username') || '';
                    document.getElementById('authField2').value = jsonVal(ac, 'password') || '';
                }
            }
            toggleAuthFields();

            // Load table columns then populate mappings
            await loadTableColumns();

            // Set ID fields
            document.getElementById('syncIdSource').value = config.idFieldSource || '';
            document.getElementById('syncIdTarget').value = config.idFieldTarget || '';

            // Parse and populate field mappings
            if (config.fieldMappings) {
                try {
                    const mappings = JSON.parse(config.fieldMappings);
                    mappings.forEach(m => addMappingRow(m.source, m.target, m.lookup));
                } catch (e) { /* ignore bad json */ }
            }
        }
    }

    document.getElementById('syncModal').classList.add('open');
}

function closeSyncModal() {
    document.getElementById('syncModal').classList.remove('open');
}

function toggleAuthFields() {
    const type = document.getElementById('syncAuthType').value;
    const f1 = document.getElementById('authField1Group');
    const f2 = document.getElementById('authField2Group');
    const l1 = document.getElementById('authField1Label');
    const l2 = document.getElementById('authField2Label');

    f1.style.display = type === 'none' ? 'none' : 'block';
    f2.style.display = (type === 'api_key' || type === 'basic') ? 'block' : 'none';

    if (type === 'api_key') { l1.textContent = 'Header-namn'; l2.textContent = 'API-nyckel'; }
    else if (type === 'bearer') { l1.textContent = 'Token'; }
    else if (type === 'basic') { l1.textContent = 'Användarnamn'; l2.textContent = 'Lösenord'; }
}

async function testConnection() {
    const btn = document.getElementById('testBtn');
    const result = document.getElementById('testResult');
    btn.disabled = true;
    result.textContent = 'Testar...';

    const body = {
        sourceUrl: document.getElementById('syncUrl').value,
        authType: document.getElementById('syncAuthType').value,
        authConfig: buildAuthConfig(),
        jsonRootPath: document.getElementById('syncRootPath').value
    };

    try {
        const resp = await fetch(CTX + '/api/sync/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await resp.json();

        if (data.success) {
            detectedJsonFields = data.fields || [];
            result.innerHTML = '<span style="color:#10b981">✓ OK — ' + data.sampleCount + ' poster, ' + detectedJsonFields.length + ' fält</span>';

            // Show detected fields
            const section = document.getElementById('testFieldsSection');
            section.style.display = 'block';
            document.getElementById('testFieldsInfo').textContent = 'Detekterade fält: ' + detectedJsonFields.join(', ');

            // Populate source field dropdowns
            populateSourceDropdowns();
        } else {
            result.innerHTML = '<span style="color:#ef4444">✗ ' + esc(data.error) + '</span>';
        }
    } catch (e) {
        result.innerHTML = '<span style="color:#ef4444">✗ ' + e.message + '</span>';
    }

    btn.disabled = false;
}

async function loadTableColumns() {
    const table = document.getElementById('syncTargetTable').value;
    if (!table) return;

    try {
        const resp = await fetch(CTX + '/api/sync/table-info?table=' + table);
        const data = await resp.json();
        detectedDbColumns = data.columns || [];

        // Populate target dropdowns
        populateTargetDropdowns();
    } catch (e) { /* ignore */ }
}

function populateSourceDropdowns() {
    const options = '<option value="">Välj...</option>' + detectedJsonFields.map(f => '<option value="' + esc(f) + '">' + esc(f) + '</option>').join('');
    document.getElementById('syncIdSource').innerHTML = options;

    // Update mapping row source selects
    document.querySelectorAll('.mapping-source').forEach(sel => {
        const current = sel.value;
        sel.innerHTML = '<option value="">Välj...</option>' + detectedJsonFields.map(f => '<option value="' + esc(f) + '"' + (f === current ? ' selected' : '') + '>' + esc(f) + '</option>').join('');
    });
}

function populateTargetDropdowns() {
    const options = '<option value="">Välj...</option>' + detectedDbColumns.map(c => '<option value="' + esc(c.name) + '">' + esc(c.name) + ' (' + c.type + (c.isKey ? ', PK' : '') + ')</option>').join('');
    document.getElementById('syncIdTarget').innerHTML = options;

    document.querySelectorAll('.mapping-target').forEach(sel => {
        const current = sel.value;
        sel.innerHTML = '<option value="">Välj...</option>' + detectedDbColumns.map(c => '<option value="' + esc(c.name) + '"' + (c.name === current ? ' selected' : '') + '>' + esc(c.name) + ' (' + c.type + ')</option>').join('');
    });
}

function addMappingRow(sourceVal, targetVal, lookupVal) {
    const container = document.getElementById('mappingRows');
    const row = document.createElement('div');
    row.className = 'mapping-row';

    const selStyle = 'width:100%;padding:6px;font-size:0.85em;border:1px solid #d1d5db;border-radius:4px';
    const sourceOpts = '<option value="">Välj...</option>' + detectedJsonFields.map(f =>
        '<option value="' + esc(f) + '"' + (f === sourceVal ? ' selected' : '') + '>' + esc(f) + '</option>').join('');
    const targetOpts = '<option value="">Välj...</option>' + detectedDbColumns.map(c =>
        '<option value="' + esc(c.name) + '"' + (c.name === targetVal ? ' selected' : '') + '>' + esc(c.name) + ' (' + c.type + ')</option>').join('');

    row.innerHTML =
        '<select class="mapping-source" style="' + selStyle + '">' + sourceOpts + '</select>' +
        '<div class="mapping-arrow">→</div>' +
        '<select class="mapping-target" style="' + selStyle + '">' + targetOpts + '</select>' +
        '<input class="mapping-lookup" type="text" placeholder="t.ex. customers.company_id" value="' + esc(lookupVal || '') + '" style="' + selStyle + '" title="FK Lookup: tabell.kolumn — översätter JSON-värdet via SELECT id FROM tabell WHERE kolumn = värde">' +
        '<button class="mapping-remove" onclick="this.parentElement.remove()">✕</button>';

    container.appendChild(row);
}

function buildAuthConfig() {
    const type = document.getElementById('syncAuthType').value;
    const f1 = document.getElementById('authField1').value;
    const f2 = document.getElementById('authField2').value;

    if (type === 'api_key') return { headerName: f1, keyValue: f2 };
    if (type === 'bearer') return { token: f1 };
    if (type === 'basic') return { username: f1, password: f2 };
    return {};
}

function collectMappings() {
    const rows = document.querySelectorAll('.mapping-row');
    const mappings = [];
    rows.forEach(row => {
        const source = row.querySelector('.mapping-source').value;
        const target = row.querySelector('.mapping-target').value;
        const lookup = row.querySelector('.mapping-lookup').value.trim();
        if (source && target) {
            const m = { source, target };
            if (lookup) m.lookup = lookup;
            mappings.push(m);
        }
    });
    return mappings;
}

async function saveSyncConfig() {
    const editId = document.getElementById('syncEditId').value;
    const body = {
        name: document.getElementById('syncName').value,
        sourceUrl: document.getElementById('syncUrl').value,
        authType: document.getElementById('syncAuthType').value,
        authConfig: buildAuthConfig(),
        jsonRootPath: document.getElementById('syncRootPath').value,
        targetTable: document.getElementById('syncTargetTable').value,
        idFieldSource: document.getElementById('syncIdSource').value,
        idFieldTarget: document.getElementById('syncIdTarget').value,
        fieldMappings: collectMappings(),
        scheduleMinutes: parseInt(document.getElementById('syncSchedule').value) || 0,
        updateOnly: document.getElementById('syncUpdateOnly').checked
    };

    if (!body.name || !body.sourceUrl || !body.targetTable || !body.idFieldSource || !body.idFieldTarget) {
        alert('Fyll i alla obligatoriska fält');
        return;
    }

    if (editId) body.id = parseInt(editId);

    try {
        const resp = await fetch(CTX + '/api/sync/configs', {
            method: editId ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');

        closeSyncModal();
        showAlert('syncAlert', 'success', editId ? 'Synk uppdaterad' : 'Ny synk skapad');
        loadSyncConfigs();
    } catch (e) {
        alert('Fel: ' + e.message);
    }
}

// ========== HISTORY MODAL ==========
async function openHistoryModal(configId) {
    document.getElementById('historyModal').classList.add('open');
    document.getElementById('historyContent').innerHTML = 'Laddar...';

    try {
        const resp = await fetch(CTX + '/api/sync/history?configId=' + configId);
        const history = await resp.json();

        if (history.length === 0) {
            document.getElementById('historyContent').innerHTML = '<div class="empty-state">Ingen körhistorik</div>';
            return;
        }

        let html = '<table class="history-table"><thead><tr>' +
            '<th>Starttid</th><th>Status</th><th>Hämtade</th><th>Synkade</th><th>Fel</th><th>Utförd av</th><th>Felmeddelande</th>' +
            '</tr></thead><tbody>';

        history.forEach(h => {
            const statusClass = h.status === 'success' ? 'badge-success' : h.status === 'failed' ? 'badge-failed' : 'badge-active';
            html += '<tr>' +
                '<td>' + (h.startedAt ? new Date(h.startedAt).toLocaleString('sv-SE') : '-') + '</td>' +
                '<td><span class="badge ' + statusClass + '">' + h.status + '</span></td>' +
                '<td>' + h.recordsFetched + '</td>' +
                '<td>' + h.recordsUpserted + '</td>' +
                '<td>' + h.recordsFailed + '</td>' +
                '<td>' + esc(h.triggeredByName || 'Schemalagd') + '</td>' +
                '<td style="max-width:200px;overflow:hidden;text-overflow:ellipsis" title="' + esc(h.errorMessage || '') + '">' + esc(h.errorMessage || '-') + '</td>' +
            '</tr>';
        });

        html += '</tbody></table>';
        document.getElementById('historyContent').innerHTML = html;
    } catch (e) {
        document.getElementById('historyContent').innerHTML = '<div class="empty-state" style="color:#ef4444">' + e.message + '</div>';
    }
}

function closeHistoryModal() {
    document.getElementById('historyModal').classList.remove('open');
}

// ========== HELPERS ==========
function showAlert(containerId, type, message) {
    const cls = type === 'success' ? 'alert-success' : type === 'error' ? 'alert-error' : 'alert-info';
    document.getElementById(containerId).innerHTML = '<div class="alert ' + cls + '">' + esc(message) + '</div>';
    setTimeout(() => { document.getElementById(containerId).innerHTML = ''; }, 5000);
}

function esc(s) { if (!s) return ''; return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
function truncUrl(url) { if (!url) return ''; return url.length > 60 ? url.substring(0, 60) + '...' : url; }
function jsonVal(json, key) {
    if (typeof json === 'string') {
        try { json = JSON.parse(json); } catch(e) {
            const m = json.match(new RegExp('"' + key + '"\\s*:\\s*"([^"]*)"'));
            return m ? m[1] : null;
        }
    }
    return json[key] || null;
}

// ========== WIDGETS TAB ==========
async function loadWidgets() {
    try {
        const resp = await fetch(CTX + '/api/admin/widgets');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allWidgets = await resp.json();
        renderWidgets();
    } catch (e) {
        document.getElementById('widgetGrid').innerHTML = '<div class="empty-state" style="color:#ef4444">Kunde inte ladda: ' + e.message + '</div>';
    }
}

function renderWidgets() {
    document.getElementById('widgetCount').textContent = allWidgets.length + ' widgets';

    if (allWidgets.length === 0) {
        document.getElementById('widgetGrid').innerHTML = '<div class="empty-state">Inga widgets. Klicka "+ Ny custom widget" för att skapa en.</div>';
        return;
    }

    document.getElementById('widgetGrid').innerHTML = allWidgets.map(w => {
        const customBadge = w.isCustom ? '<span class="badge badge-scheduled">Custom</span>' : '<span class="badge badge-manual">Inbyggd</span>';
        const activeBadge = w.isActive ? '<span class="badge badge-success">Aktiv</span>' : '<span class="badge badge-failed">Inaktiv</span>';
        const refreshText = w.refreshSeconds > 0 ? 'Var ' + w.refreshSeconds + 's' : 'Manuell';

        return '<div class="sync-card">' +
            '<div class="sync-card-header">' +
                '<div class="sync-card-title">' + esc(w.icon) + ' ' + esc(w.name) + '</div>' +
                '<div>' + customBadge + ' ' + activeBadge + '</div>' +
            '</div>' +
            '<div class="sync-card-meta">' +
                '<div>🔑 render_key: <code>' + esc(w.renderKey) + '</code></div>' +
                '<div>🔄 Uppdatering: ' + refreshText + '</div>' +
                (w.description ? '<div>📝 ' + esc(w.description) + '</div>' : '') +
                (w.creatorName ? '<div>👤 Skapad av: ' + esc(w.creatorName) + '</div>' : '') +
            '</div>' +
            '<div class="sync-card-actions">' +
                (w.isCustom ? '<button class="btn btn-sm" onclick="openWidgetModal(' + w.id + ')">✏️ Redigera</button>' : '') +
                (w.isCustom ? '<button class="btn btn-sm btn-danger" onclick="deleteWidget(' + w.id + ', \'' + esc(w.name) + '\')">🗑️</button>' : '') +
                (!w.isCustom ? '<span style="font-size:0.8em;color:var(--ic-text-muted)">Inbyggda widgets redigeras via kod</span>' : '') +
            '</div>' +
        '</div>';
    }).join('');
}

function openWidgetModal(editId) {
    document.getElementById('widgetEditId').value = editId || '';
    document.getElementById('widgetName').value = '';
    document.getElementById('widgetIcon').value = '';
    document.getElementById('widgetDesc').value = '';
    document.getElementById('widgetHtml').value = '';
    document.getElementById('widgetJs').value = '';
    document.getElementById('widgetRefresh').value = '0';
    document.getElementById('widgetActive').checked = true;
    document.getElementById('widgetModalTitle').textContent = editId ? 'Redigera widget' : 'Ny custom widget';

    if (editId) {
        const w = allWidgets.find(w => w.id === editId);
        if (w) {
            document.getElementById('widgetName').value = w.name || '';
            document.getElementById('widgetIcon').value = w.icon || '';
            document.getElementById('widgetDesc').value = w.description || '';
            document.getElementById('widgetHtml').value = w.customHtml || '';
            document.getElementById('widgetJs').value = w.customJs || '';
            document.getElementById('widgetRefresh').value = w.refreshSeconds || 0;
            document.getElementById('widgetActive').checked = w.isActive;
        }
    }

    document.getElementById('widgetModal').classList.add('open');
}

function closeWidgetModal() {
    document.getElementById('widgetModal').classList.remove('open');
}

async function saveWidget() {
    const editId = document.getElementById('widgetEditId').value;
    const body = {
        name: document.getElementById('widgetName').value,
        icon: document.getElementById('widgetIcon').value || '📦',
        description: document.getElementById('widgetDesc').value,
        customHtml: document.getElementById('widgetHtml').value,
        customJs: document.getElementById('widgetJs').value,
        refreshSeconds: parseInt(document.getElementById('widgetRefresh').value) || 0,
        isActive: document.getElementById('widgetActive').checked
    };

    if (!body.name) {
        alert('Namn krävs');
        return;
    }

    if (editId) body.id = parseInt(editId);

    try {
        const resp = await fetch(CTX + '/api/admin/widgets', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');
        closeWidgetModal();
        showAlert('widgetAlert', 'success', editId ? 'Widget uppdaterad' : 'Widget skapad');
        loadWidgets();
    } catch (e) {
        alert('Fel: ' + e.message);
    }
}

async function deleteWidget(id, name) {
    if (!confirm('Radera widget "' + name + '"?')) return;
    try {
        const resp = await fetch(CTX + '/api/admin/widgets?id=' + id, { method: 'DELETE' });
        if (!resp.ok) throw new Error('Failed');
        showAlert('widgetAlert', 'success', 'Borttagen: ' + name);
        loadWidgets();
    } catch (e) {
        showAlert('widgetAlert', 'error', 'Fel: ' + e.message);
    }
}

// ========== CONFIG TAB ==========
const categoryLabels = {
    database: '🗄️ Databas',
    email: '📧 E-post (Azure ACS)',
    security: '🔐 Säkerhet',
    general: '⚙️ Allmänt',
    notifications: '🔔 Notifikationer',
    monitoring: '📡 Övervakning'
};

async function loadConfigEntries() {
    try {
        const resp = await fetch(CTX + '/api/admin/config');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allConfigEntries = await resp.json();
        renderConfigEntries();
    } catch (e) {
        document.getElementById('configContent').innerHTML = '<div class="empty-state" style="color:#ef4444">Kunde inte ladda inställningar: ' + e.message + '</div>';
    }
}

function renderConfigEntries() {
    const query = (document.getElementById('configSearch').value || '').toLowerCase();
    const filtered = allConfigEntries.filter(c =>
        c.key.toLowerCase().includes(query) ||
        (c.description || '').toLowerCase().includes(query) ||
        (c.category || '').toLowerCase().includes(query)
    );

    document.getElementById('configCount').textContent = filtered.length + ' av ' + allConfigEntries.length + ' inställningar';

    if (filtered.length === 0) {
        document.getElementById('configContent').innerHTML = '<div class="empty-state">Inga inställningar hittades</div>';
        return;
    }

    // Group by category
    const groups = {};
    filtered.forEach(c => {
        const cat = c.category || 'general';
        if (!groups[cat]) groups[cat] = [];
        groups[cat].push(c);
    });

    // Render categories in order
    const categoryOrder = ['database', 'email', 'security', 'monitoring', 'notifications', 'general'];
    let html = '';

    categoryOrder.forEach(cat => {
        if (!groups[cat]) return;
        html += '<div class="config-category">';
        html += '<div class="config-category-title">' + (categoryLabels[cat] || cat) + '</div>';
        html += '<table class="config-table"><thead><tr><th style="width:25%">Nyckel</th><th style="width:35%">Värde</th><th>Beskrivning</th><th style="width:50px"></th></tr></thead><tbody>';

        groups[cat].forEach(c => {
            const secretClass = c.isSecret ? ' secret' : '';
            const secretBadge = c.isSecret ? '<span class="secret-badge">HEMLIG</span>' : '';
            const meta = c.updatedByName ? 'Ändrad av ' + esc(c.updatedByName) : '';

            html += '<tr>' +
                '<td><span class="config-key">' + esc(c.key) + '</span>' + secretBadge + '</td>' +
                '<td><span class="config-value' + secretClass + '">' + esc(c.value || '') + '</span></td>' +
                '<td><span class="config-desc">' + esc(c.description || '') + '</span>' +
                    (meta ? '<br><span class="config-meta">' + meta + '</span>' : '') + '</td>' +
                '<td><button class="config-edit-btn" onclick="openConfigModal(\'' + esc(c.key) + '\')" title="Redigera">✏️</button></td>' +
            '</tr>';
        });

        html += '</tbody></table></div>';
    });

    document.getElementById('configContent').innerHTML = html;
}

function openConfigModal(key) {
    const entry = allConfigEntries.find(c => c.key === key);
    if (!entry) return;

    document.getElementById('configEditKey').value = entry.key;
    document.getElementById('configEditDescText').textContent = entry.description || '';
    document.getElementById('configEditHint').textContent = entry.isSecret ? 'OBS: Hemligt värde — nuvarande visas maskerat. Ange hela det nya värdet.' : '';

    // For secrets, show empty field (they need to type the full new value)
    // For non-secrets, pre-fill current value
    document.getElementById('configEditValue').value = entry.isSecret ? '' : (entry.value || '');
    document.getElementById('configEditValue').placeholder = entry.isSecret ? 'Ange nytt värde...' : '';

    document.getElementById('configModal').classList.add('open');
}

function closeConfigModal() {
    document.getElementById('configModal').classList.remove('open');
}

async function saveConfigValue() {
    const key = document.getElementById('configEditKey').value;
    const value = document.getElementById('configEditValue').value;
    const entry = allConfigEntries.find(c => c.key === key);

    // For secrets: if empty field, user didn't change anything — cancel
    if (entry && entry.isSecret && value === '') {
        closeConfigModal();
        return;
    }

    try {
        const resp = await fetch(CTX + '/api/admin/config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key, value })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Failed');

        closeConfigModal();
        showAlert('configAlert', 'success', 'Inställning sparad: ' + key);
        loadConfigEntries(); // reload to see updated value
    } catch (e) {
        showAlert('configAlert', 'error', 'Fel: ' + e.message);
    }
}

async function reloadConfig() {
    showAlert('configAlert', 'info', 'Laddar om konfigurationscache...');
    await loadConfigEntries();
    showAlert('configAlert', 'success', 'Konfiguration uppdaterad');
}

// ========== SEARCH ==========
let searchTimeout;
document.getElementById('userSearch').addEventListener('input', () => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(renderUsers, 200);
});
document.getElementById('configSearch').addEventListener('input', () => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(renderConfigEntries, 200);
});
document.getElementById('groupSearch').addEventListener('input', () => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(renderGroups, 200);
});

// ========== MODULES TAB ==========

async function loadAdminModules() {
    try {
        const resp = await fetch(CTX + '/api/admin/modules');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allModulesAdmin = await resp.json();
        renderAdminModules();
    } catch (e) {
        document.getElementById('moduleGrid').innerHTML = '<div class="empty-state" style="color:#ef4444">Kunde inte ladda moduler: ' + e.message + '</div>';
    }
}

function renderAdminModules() {
    const query = (document.getElementById('moduleSearch').value || '').toLowerCase();
    const typeFilter = document.getElementById('moduleTypeFilter').value;

    const filtered = allModulesAdmin.filter(m =>
        ((m.name || '').toLowerCase().includes(query) ||
         (m.directoryName || '').toLowerCase().includes(query) ||
         (m.description || '').toLowerCase().includes(query)) &&
        (!typeFilter || m.moduleType === typeFilter)
    );

    document.getElementById('moduleCount').textContent = filtered.length + ' av ' + allModulesAdmin.length + ' moduler';

    if (filtered.length === 0) {
        document.getElementById('moduleGrid').innerHTML = '<div class="empty-state">Inga moduler hittades</div>';
        return;
    }

    document.getElementById('moduleGrid').innerHTML = filtered.map(m => {
        const typeBadge = '<span class="mf-type-badge ' + esc(m.moduleType) + '">' + esc(m.moduleType) + '</span>';
        const specBadge = m.hasAiSpec ? ' <span class="badge badge-success" style="font-size:0.7em">📄 Spec</span>' : '';
        const groups = (m.groups || []).map(g => '<span class="badge badge-default" style="font-size:0.7em">' + esc(g) + '</span>').join(' ');

        return '<div class="sync-card">' +
            '<div class="sync-card-header">' +
                '<div class="sync-card-title">' + esc(m.icon || '📦') + ' ' + esc(m.name) + '</div>' +
                '<div>' + typeBadge + specBadge + '</div>' +
            '</div>' +
            '<div class="sync-card-meta">' +
                '<div>📁 <code style="font-size:0.85em">' + esc(m.directoryName) + '/' + esc(m.entryFile) + '</code></div>' +
                (m.description ? '<div style="color:var(--ic-text-muted)">' + esc(m.description).substring(0, 120) + '</div>' : '') +
                (m.ownerName ? '<div>👤 ' + esc(m.ownerName) + '</div>' : '') +
                (groups ? '<div>' + groups + '</div>' : '') +
            '</div>' +
            '<div class="sync-card-actions">' +
                '<button class="btn btn-sm" onclick="openModuleFiles(' + m.id + ')">📁 Filer</button>' +
                '<button class="btn btn-sm" onclick="openModuleMetadataModalFor(' + m.id + ')">✏️ Metadata</button>' +
                (m.hasAiSpec ? '<button class="btn btn-sm" onclick="downloadModuleSpecFor(' + m.id + ')">📄 Spec</button>' : '') +
            '</div>' +
        '</div>';
    }).join('');
}

// Module search/filter
document.getElementById('moduleSearch').addEventListener('input', function() {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(renderAdminModules, 200);
});
document.getElementById('moduleTypeFilter').addEventListener('change', renderAdminModules);

// ─── File browser ───

async function openModuleFiles(moduleId) {
    currentModuleId = moduleId;
    currentModuleData = allModulesAdmin.find(m => m.id === moduleId);
    if (!currentModuleData) return;

    document.getElementById('modulesListView').style.display = 'none';
    document.getElementById('moduleFilesView').style.display = 'block';
    document.getElementById('moduleEditorView').style.display = 'none';
    document.getElementById('moduleBreadcrumb').textContent = (currentModuleData.icon || '📦') + ' ' + currentModuleData.name;
    document.getElementById('btnSpecDl').style.display = currentModuleData.hasAiSpec ? '' : 'none';

    await loadModuleFiles();
}

async function loadModuleFiles() {
    document.getElementById('fileTableBody').innerHTML = '<tr><td colspan="4" style="text-align:center;color:var(--ic-text-muted);padding:20px">Laddar filer...</td></tr>';
    try {
        const resp = await fetch(CTX + '/api/admin/modules/files?moduleId=' + currentModuleId);
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        currentModuleFiles = await resp.json();
        renderFileTable();
    } catch (e) {
        document.getElementById('fileTableBody').innerHTML = '<tr><td colspan="4" style="text-align:center;color:#ef4444;padding:20px">' + e.message + '</td></tr>';
    }
}

function renderFileTable() {
    if (currentModuleFiles.length === 0) {
        document.getElementById('fileTableBody').innerHTML = '<tr><td colspan="4" style="text-align:center;color:var(--ic-text-muted);padding:20px">Inga filer</td></tr>';
        return;
    }

    document.getElementById('fileTableBody').innerHTML = currentModuleFiles.map(f => {
        const icon = f.isDirectory ? '📁' : getFileIcon(f.extension);
        const entryBadge = f.isEntryFile ? '<span class="mf-entry">⭐ entry</span>' : '';
        const sizeStr = f.isDirectory ? '' : formatBytes(f.size);
        const dateStr = f.lastModified ? new Date(f.lastModified).toLocaleString('sv-SE') : '';
        const clickable = f.isTextFile && !f.isDirectory;
        const clickAttr = clickable ? ' class="mf-clickable" onclick="openFileEditor(\'' + escAttr(f.path) + '\')"' : '';

        let actions = '';
        if (!f.isDirectory) {
            actions = '<div class="mf-actions">' +
                '<button onclick="event.stopPropagation();downloadFile(\'' + escAttr(f.path) + '\')" title="Ladda ner">📥</button>' +
                '<button onclick="event.stopPropagation();deleteFile(\'' + escAttr(f.path) + '\')" title="Ta bort">🗑️</button>' +
            '</div>';
        }

        return '<tr' + clickAttr + '>' +
            '<td><span class="mf-file-icon">' + icon + '</span>' + esc(f.name) + entryBadge + '</td>' +
            '<td class="mf-size">' + sizeStr + '</td>' +
            '<td class="mf-size">' + dateStr + '</td>' +
            '<td>' + actions + '</td>' +
        '</tr>';
    }).join('');
}

function getFileIcon(ext) {
    switch (ext) {
        case '.html': case '.htm': return '🌐';
        case '.css': return '🎨';
        case '.js': return '⚡';
        case '.json': return '📋';
        case '.md': case '.txt': return '📝';
        case '.png': case '.jpg': case '.jpeg': case '.gif': case '.svg': return '🖼️';
        case '.map': return '🗺️';
        default: return '📄';
    }
}

function escAttr(s) { return esc(s).replace(/'/g, "\\'"); }

// ─── File editor ───

async function openFileEditor(filePath) {
    document.getElementById('moduleFilesView').style.display = 'none';
    document.getElementById('moduleEditorView').style.display = 'block';
    document.getElementById('editorFilePath').textContent = filePath;
    document.getElementById('editorAlert').innerHTML = '';
    document.getElementById('codeEditor').value = 'Laddar...';
    document.getElementById('editorStatus').textContent = '';

    try {
        const resp = await fetch(CTX + '/api/admin/modules/file?moduleId=' + currentModuleId + '&path=' + encodeURIComponent(filePath));
        if (!resp.ok) {
            const err = await resp.json().catch(() => ({error:'Request failed'}));
            throw new Error(err.error || 'HTTP ' + resp.status);
        }
        const data = await resp.json();
        document.getElementById('codeEditor').value = data.content || '';
        document.getElementById('editorStatus').textContent = formatBytes(data.size) + ' — senast ändrad: ' + new Date(data.lastModified).toLocaleString('sv-SE');
    } catch (e) {
        document.getElementById('codeEditor').value = '';
        showAlert('editorAlert', 'error', 'Kunde inte ladda fil: ' + e.message);
    }
}

async function saveFileContent() {
    const content = document.getElementById('codeEditor').value;
    const path = document.getElementById('editorFilePath').textContent;

    try {
        const resp = await fetch(CTX + '/api/admin/modules/file', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ moduleId: currentModuleId, path: path, content: content })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Save failed');
        document.getElementById('editorStatus').textContent = formatBytes(data.size) + ' — sparad ' + new Date().toLocaleTimeString('sv-SE');
        showAlert('editorAlert', 'success', 'Filen har sparats');
    } catch (e) {
        showAlert('editorAlert', 'error', 'Kunde inte spara: ' + e.message);
    }
}

// Ctrl+S / Cmd+S to save
document.getElementById('codeEditor').addEventListener('keydown', function(e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        saveFileContent();
    }
    // Tab inserts 2 spaces
    if (e.key === 'Tab') {
        e.preventDefault();
        const ta = e.target;
        const start = ta.selectionStart;
        const end = ta.selectionEnd;
        ta.value = ta.value.substring(0, start) + '  ' + ta.value.substring(end);
        ta.selectionStart = ta.selectionEnd = start + 2;
    }
});

// ─── File upload ───

async function uploadModuleFile(input) {
    const file = input.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('moduleId', currentModuleId);
    formData.append('path', '');
    formData.append('file', file);

    try {
        const resp = await fetch(CTX + '/api/admin/modules/file', {
            method: 'POST',
            body: formData
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Upload failed');
        showAlert('fileAlert', 'success', 'Uppladdad: ' + file.name);
        await loadModuleFiles();
    } catch (e) {
        showAlert('fileAlert', 'error', 'Uppladdning misslyckades: ' + e.message);
    }
    input.value = '';
}

// ─── File delete ───

async function deleteFile(filePath) {
    if (!confirm('Radera filen "' + filePath + '"? Detta kan inte ångras.')) return;
    try {
        const resp = await fetch(CTX + '/api/admin/modules/file?moduleId=' + currentModuleId + '&path=' + encodeURIComponent(filePath), { method: 'DELETE' });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Delete failed');
        showAlert('fileAlert', 'success', 'Borttagen: ' + filePath);
        await loadModuleFiles();
    } catch (e) {
        showAlert('fileAlert', 'error', 'Kunde inte ta bort: ' + e.message);
    }
}

// ─── File download ───

function downloadFile(filePath) {
    window.open(CTX + '/api/admin/modules/file?moduleId=' + currentModuleId + '&path=' + encodeURIComponent(filePath) + '&download=true', '_blank');
}

function downloadModuleZip() {
    window.open(CTX + '/api/admin/modules/download?moduleId=' + currentModuleId, '_blank');
}

function downloadModuleSpec() {
    window.open(CTX + '/api/admin/modules/spec?moduleId=' + currentModuleId, '_blank');
}

function downloadModuleSpecFor(moduleId) {
    window.open(CTX + '/api/admin/modules/spec?moduleId=' + moduleId, '_blank');
}

// ─── New file ───

function openNewFileModal() {
    document.getElementById('newFileName').value = '';
    document.getElementById('newFileModal').classList.add('open');
}

function closeNewFileModal() {
    document.getElementById('newFileModal').classList.remove('open');
}

async function createNewFile() {
    const fileName = document.getElementById('newFileName').value.trim();
    if (!fileName) { alert('Ange ett filnamn'); return; }

    try {
        const resp = await fetch(CTX + '/api/admin/modules/create-file', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ moduleId: currentModuleId, path: fileName })
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Create failed');
        closeNewFileModal();
        showAlert('fileAlert', 'success', 'Skapad: ' + fileName);
        await loadModuleFiles();
    } catch (e) {
        alert('Kunde inte skapa fil: ' + e.message);
    }
}

// ─── Module metadata ───

function openModuleMetadataModal() {
    openModuleMetadataModalFor(currentModuleId);
}

async function openModuleMetadataModalFor(moduleId) {
    const m = allModulesAdmin.find(x => x.id === moduleId);
    if (!m) return;

    document.getElementById('moduleMetaId').value = moduleId;
    document.getElementById('moduleMetaName').value = m.name || '';
    document.getElementById('moduleMetaIcon').value = m.icon || '';
    document.getElementById('moduleMetaCategory').value = m.category || '';
    document.getElementById('moduleMetaVersion').value = m.version || '';
    document.getElementById('moduleMetaBadge').value = m.badge || '';
    document.getElementById('moduleMetaDesc').value = m.description || '';
    document.getElementById('moduleMetaAiSpec').value = '';

    // Load full AI spec text via file read (it's not in the list response)
    try {
        const specResp = await fetch(CTX + '/api/admin/modules/spec?moduleId=' + moduleId);
        if (specResp.ok) {
            const specText = await specResp.text();
            document.getElementById('moduleMetaAiSpec').value = specText;
        }
    } catch (e) { /* no spec, ok */ }

    // Load groups
    const groupsEl = document.getElementById('moduleMetaGroups');
    const moduleGroups = m.groups || [];
    if (allGroups.length === 0) {
        try {
            const gResp = await fetch(CTX + '/api/admin/groups');
            const gData = await gResp.json();
            allGroups = gData.groups || [];
        } catch (e) { /* fallback empty */ }
    }
    groupsEl.innerHTML = allGroups.map(g =>
        '<label style="display:flex;align-items:center;gap:4px;font-size:0.85em;cursor:pointer">' +
        '<input type="checkbox" value="' + g.id + '"' + (moduleGroups.includes(g.name) ? ' checked' : '') + '> ' +
        esc(g.icon || '👥') + ' ' + esc(g.name) + '</label>'
    ).join('');

    document.getElementById('moduleMetadataModal').classList.add('open');
}

function closeModuleMetadataModal() {
    document.getElementById('moduleMetadataModal').classList.remove('open');
}

async function saveModuleMetadata() {
    const moduleId = parseInt(document.getElementById('moduleMetaId').value);
    const groupCheckboxes = document.querySelectorAll('#moduleMetaGroups input[type="checkbox"]:checked');
    const groupIds = Array.from(groupCheckboxes).map(cb => parseInt(cb.value));

    const body = {
        id: moduleId,
        name: document.getElementById('moduleMetaName').value.trim(),
        icon: document.getElementById('moduleMetaIcon').value.trim(),
        category: document.getElementById('moduleMetaCategory').value.trim(),
        version: document.getElementById('moduleMetaVersion').value.trim(),
        badge: document.getElementById('moduleMetaBadge').value.trim(),
        description: document.getElementById('moduleMetaDesc').value.trim(),
        aiSpecText: document.getElementById('moduleMetaAiSpec').value,
        groupIds: groupIds
    };

    try {
        const resp = await fetch(CTX + '/api/admin/modules/metadata', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'Save failed');
        closeModuleMetadataModal();
        // Show alert in whichever view is visible
        const alertTarget = document.getElementById('moduleFilesView').style.display !== 'none' ? 'fileAlert' : 'moduleAlert';
        showAlert(alertTarget, 'success', 'Modulen har uppdaterats');
        // Refresh list
        modulesLoaded = false;
        await loadAdminModules();
        modulesLoaded = true;
        // If we were in file view, update breadcrumb
        if (currentModuleId === moduleId) {
            currentModuleData = allModulesAdmin.find(x => x.id === moduleId);
            if (currentModuleData) {
                document.getElementById('moduleBreadcrumb').textContent = (currentModuleData.icon || '📦') + ' ' + currentModuleData.name;
            }
        }
    } catch (e) {
        alert('Kunde inte spara: ' + e.message);
    }
}

// ─── Navigation ───

function backToModuleList() {
    document.getElementById('moduleFilesView').style.display = 'none';
    document.getElementById('moduleEditorView').style.display = 'none';
    document.getElementById('modulesListView').style.display = 'block';
    currentModuleId = null;
    currentModuleData = null;
    currentModuleFiles = [];
}

function backToFileList() {
    document.getElementById('moduleEditorView').style.display = 'none';
    document.getElementById('moduleFilesView').style.display = 'block';
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// ========== SERVERS / CUSTOMER STATS TAB ==========
async function loadStatServers() {
    try {
        const resp = await fetch(CTX + '/api/customer-stats/servers');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allStatServers = await resp.json();
        renderStatServers();
    } catch (e) {
        document.getElementById('serverTableBody').innerHTML =
            '<tr><td colspan="4" class="empty-state" style="color:#ef4444">Kunde inte ladda servrar: ' + e.message + '</td></tr>';
    }
}

function renderStatServers() {
    const query = (document.getElementById('serverSearch').value || '').toLowerCase();
    const filtered = allStatServers.filter(s =>
        s.urlNormalized.toLowerCase().includes(query) || (s.version || '').toLowerCase().includes(query)
    );
    document.getElementById('serverCount').textContent = filtered.length + ' / ' + allStatServers.length + ' servrar';

    const tbody = document.getElementById('serverTableBody');
    if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="empty-state">Inga servrar hittades</td></tr>';
        return;
    }
    tbody.innerHTML = filtered.map(s =>
        '<tr style="' + (s.isExcluded ? 'opacity:0.5;' : '') + '">' +
        '<td><a href="' + escHtml(s.url) + '" target="_blank" style="color:var(--ic-primary)">' + escHtml(s.urlNormalized) + '</a></td>' +
        '<td>' + escHtml(s.version || '-') + '</td>' +
        '<td>' + escHtml(s.lastSeen || '-') + '</td>' +
        '<td style="text-align:center">' +
        '<label style="cursor:pointer"><input type="checkbox" ' + (s.isExcluded ? 'checked' : '') +
        ' onchange="toggleServerExclude(' + s.id + ', this.checked)"> Exkludera</label></td>' +
        '</tr>'
    ).join('');
}

async function toggleServerExclude(serverId, excluded) {
    try {
        const resp = await fetch(CTX + '/api/customer-stats/servers/exclude', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ serverId, excluded })
        });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const srv = allStatServers.find(s => s.id === serverId);
        if (srv) srv.isExcluded = excluded;
        renderStatServers();
    } catch (e) {
        alert('Kunde inte uppdatera: ' + e.message);
        loadStatServers();
    }
}

document.getElementById('serverSearch').addEventListener('input', renderStatServers);

// ========== MCP TAB ==========
function escHtml(s) { if (!s) return ''; return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }

function truncate(s, n) { return s && s.length > n ? s.substring(0, n) + '...' : (s || ''); }

async function loadMcpServers() {
    try {
        const resp = await fetch(CTX + '/api/mcp/admin/servers');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allMcpServers = await resp.json();
        renderMcpServers();
    } catch (e) {
        document.getElementById('mcpGrid').innerHTML = '<div class="empty-state" style="color:#ef4444">Kunde inte ladda MCP-servrar: ' + e.message + '</div>';
    }
}

function renderMcpServers() {
    const grid = document.getElementById('mcpGrid');
    if (allMcpServers.length === 0) {
        grid.innerHTML = '<div class="empty-state">Inga MCP-servrar konfigurerade</div>';
        return;
    }
    grid.innerHTML = allMcpServers.map(s => {
        const statusBadge = s.isActive
            ? (s.lastError ? '<span class="badge badge-failed">Fel</span>' : '<span class="badge badge-success">Aktiv</span>')
            : '<span class="badge badge-manual">Inaktiv</span>';
        const typeBadge = s.transportType === 'http'
            ? '<span class="badge badge-active">HTTP</span>'
            : '<span class="badge badge-scheduled">Stdio</span>';
        const endpoint = s.transportType === 'http' ? (s.endpointUrl || '-') : (s.command || '-');
        return '<div class="sync-card">' +
            '<div class="sync-card-header"><div class="sync-card-title">' + escHtml(s.name) + '</div><div>' + statusBadge + ' ' + typeBadge + '</div></div>' +
            '<div class="sync-card-meta">' +
                '<div><strong>Prefix:</strong> ' + escHtml(s.toolPrefix) + '</div>' +
                '<div><strong>Endpoint:</strong> ' + escHtml(truncate(endpoint, 60)) + '</div>' +
                '<div><strong>Verktyg:</strong> ' + (s.toolCount || 0) + ' st</div>' +
                (s.lastConnected ? '<div><strong>Senast:</strong> ' + escHtml(s.lastConnected) + '</div>' : '') +
                (s.lastError ? '<div style="color:#ef4444"><strong>Fel:</strong> ' + escHtml(truncate(s.lastError, 100)) + '</div>' : '') +
            '</div>' +
            '<div class="sync-card-actions">' +
                (s.authType === 'oauth' ? '<button class="btn btn-sm btn-primary" onclick="authorizeMcpServer(' + s.id + ')">🔑 Auktorisera</button>' : '') +
                '<button class="btn btn-sm" onclick="testMcpServer(' + s.id + ')">🔍 Testa</button>' +
                '<button class="btn btn-sm" onclick="showMcpTools(' + s.id + ')">🧰 Verktyg</button>' +
                '<button class="btn btn-sm" onclick="editMcpServer(' + s.id + ')">✏️ Redigera</button>' +
                '<button class="btn btn-sm btn-danger" onclick="deleteMcpServer(' + s.id + ',\'' + escHtml(s.name) + '\')">🗑️ Ta bort</button>' +
            '</div>' +
        '</div>';
    }).join('');
}

function openMcpModal() {
    document.getElementById('mcpServerId').value = '';
    document.getElementById('mcpName').value = '';
    document.getElementById('mcpTransport').value = 'http';
    document.getElementById('mcpPrefix').value = '';
    document.getElementById('mcpEndpointUrl').value = '';
    document.getElementById('mcpAuthType').value = 'none';
    document.getElementById('mcpCommand').value = '';
    document.getElementById('mcpCommandArgs').value = '';
    document.getElementById('mcpActive').checked = true;
    document.getElementById('mcpModalTitle').textContent = 'Ny MCP-server';
    toggleMcpTransport();
    toggleMcpAuth();
    document.getElementById('mcpModal').classList.add('open');
}

function closeMcpModal() {
    document.getElementById('mcpModal').classList.remove('open');
}

function toggleMcpTransport() {
    const isHttp = document.getElementById('mcpTransport').value === 'http';
    document.getElementById('mcpHttpFields').style.display = isHttp ? '' : 'none';
    document.getElementById('mcpStdioFields').style.display = isHttp ? 'none' : '';
}

function toggleMcpAuth() {
    const type = document.getElementById('mcpAuthType').value;
    const container = document.getElementById('mcpAuthFields');
    if (type === 'oauth') {
        container.innerHTML = '<div class="form-row"><div class="form-group"><label>Client ID</label><input type="text" id="mcpOauthClientId" placeholder="Valfritt namn"></div><div class="form-group"><label>Client Secret (valfri)</label><input type="password" id="mcpOauthClientSecret" placeholder="Lämna tomt om ej krävs"></div></div><div class="form-hint">OAuth-token hämtas automatiskt via discovery från MCP-servern.</div>';
    } else if (type === 'bearer') {
        container.innerHTML = '<div class="form-group"><label>Token</label><input type="password" id="mcpAuthToken" placeholder="Bearer token"></div>';
    } else if (type === 'api_key') {
        container.innerHTML = '<div class="form-row"><div class="form-group"><label>Header</label><input type="text" id="mcpAuthHeader" placeholder="X-API-Key"></div><div class="form-group"><label>Värde</label><input type="password" id="mcpAuthValue"></div></div>';
    } else if (type === 'basic') {
        container.innerHTML = '<div class="form-row"><div class="form-group"><label>Användare</label><input type="text" id="mcpAuthUsername"></div><div class="form-group"><label>Lösenord</label><input type="password" id="mcpAuthPassword"></div></div>';
    } else {
        container.innerHTML = '';
    }
}

function editMcpServer(id) {
    const s = allMcpServers.find(x => x.id === id);
    if (!s) return;
    document.getElementById('mcpServerId').value = s.id;
    document.getElementById('mcpName').value = s.name || '';
    document.getElementById('mcpTransport').value = s.transportType || 'http';
    document.getElementById('mcpPrefix').value = s.toolPrefix || '';
    document.getElementById('mcpEndpointUrl').value = s.endpointUrl || '';
    document.getElementById('mcpAuthType').value = s.authType || 'none';
    document.getElementById('mcpCommand').value = s.command || '';
    document.getElementById('mcpCommandArgs').value = s.commandArgs || '';
    document.getElementById('mcpActive').checked = s.isActive;
    document.getElementById('mcpModalTitle').textContent = 'Redigera MCP-server';
    toggleMcpTransport();
    toggleMcpAuth();
    document.getElementById('mcpModal').classList.add('open');
}

async function saveMcpServer() {
    const id = document.getElementById('mcpServerId').value;
    const authType = document.getElementById('mcpAuthType').value;
    let authConfig = null;
    if (authType === 'oauth') {
        const clientId = (document.getElementById('mcpOauthClientId') || {}).value || '';
        const clientSecret = (document.getElementById('mcpOauthClientSecret') || {}).value || '';
        authConfig = '{"clientId":"' + clientId + '"' + (clientSecret ? ',"clientSecret":"' + clientSecret + '"' : '') + '}';
    } else if (authType === 'bearer') {
        const token = (document.getElementById('mcpAuthToken') || {}).value || '';
        authConfig = '{"token":"' + token + '"}';
    } else if (authType === 'api_key') {
        const header = (document.getElementById('mcpAuthHeader') || {}).value || '';
        const value = (document.getElementById('mcpAuthValue') || {}).value || '';
        authConfig = '{"header":"' + header + '","value":"' + value + '"}';
    } else if (authType === 'basic') {
        const username = (document.getElementById('mcpAuthUsername') || {}).value || '';
        const password = (document.getElementById('mcpAuthPassword') || {}).value || '';
        authConfig = '{"username":"' + username + '","password":"' + password + '"}';
    }

    const body = {
        name: document.getElementById('mcpName').value,
        transportType: document.getElementById('mcpTransport').value,
        endpointUrl: document.getElementById('mcpEndpointUrl').value,
        command: document.getElementById('mcpCommand').value,
        commandArgs: document.getElementById('mcpCommandArgs').value,
        authType: authType,
        authConfig: authConfig,
        toolPrefix: document.getElementById('mcpPrefix').value,
        isActive: document.getElementById('mcpActive').checked
    };
    if (id) body.id = parseInt(id);

    try {
        const resp = await fetch(CTX + '/api/mcp/admin/servers', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'HTTP ' + resp.status);
        closeMcpModal();
        showMcpAlert('MCP-server sparad!', 'success');
        loadMcpServers();
    } catch (e) {
        showMcpAlert(e.message, 'error');
    }
}

async function deleteMcpServer(id, name) {
    if (!confirm('Ta bort MCP-server "' + name + '"?')) return;
    try {
        const resp = await fetch(CTX + '/api/mcp/admin/servers?id=' + id, {method: 'DELETE'});
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        showMcpAlert('MCP-server borttagen', 'success');
        loadMcpServers();
    } catch (e) {
        showMcpAlert(e.message, 'error');
    }
}

async function authorizeMcpServer(id) {
    showMcpAlert('Startar OAuth-auktorisering...', 'info');
    try {
        const resp = await fetch(CTX + '/api/mcp/admin/servers/oauth/authorize?id=' + id, {method: 'POST'});
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'HTTP ' + resp.status);
        // Redirect browser to OAuth authorization page
        window.location.href = data.authUrl;
    } catch (e) {
        showMcpAlert('OAuth-fel: ' + e.message, 'error');
    }
}

async function testMcpServer(id) {
    showMcpAlert('Testar anslutning...', 'info');
    try {
        const resp = await fetch(CTX + '/api/mcp/admin/servers/test?id=' + id, {method: 'POST'});
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.error || 'HTTP ' + resp.status);
        showMcpAlert('Anslutning OK! Verktyg uppdaterade.', 'success');
        loadMcpServers();
    } catch (e) {
        showMcpAlert('Anslutningsfel: ' + e.message, 'error');
        loadMcpServers();
    }
}

async function showMcpTools(id) {
    try {
        const resp = await fetch(CTX + '/api/mcp/admin/servers/tools?id=' + id);
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json();

        let tools = [];
        if (data.result && data.result.tools) tools = data.result.tools;
        else if (data.tools) tools = data.tools;
        else if (Array.isArray(data)) tools = data;

        if (tools.length === 0) {
            alert('Inga verktyg hittades. Testa att ansluta först.');
            return;
        }

        const list = tools.map(t => '• ' + (t.name || '?') + ': ' + (t.description || '-')).join('\n');
        alert('Verktyg (' + tools.length + '):\n\n' + list);
    } catch (e) {
        alert('Kunde inte hämta verktyg: ' + e.message);
    }
}

async function loadMcpAudit() {
    try {
        const resp = await fetch(CTX + '/api/mcp/admin/audit');
        if (!resp.ok) return;
        const entries = await resp.json();
        const tbody = document.getElementById('mcpAuditBody');
        if (entries.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--ic-text-muted)">Inga loggar ännu</td></tr>';
            return;
        }
        tbody.innerHTML = entries.map(e => {
            const statusClass = e.responseStatus === 'success' ? 'badge-success' : 'badge-failed';
            return '<tr>' +
                '<td>' + escHtml(e.createdAt || '') + '</td>' +
                '<td>' + escHtml(e.userEmail || '-') + '</td>' +
                '<td>' + escHtml(e.serverName || e.toolPrefix || '-') + '</td>' +
                '<td>' + escHtml(e.toolName || e.method || '-') + '</td>' +
                '<td><span class="badge ' + statusClass + '">' + escHtml(e.responseStatus || '-') + '</span></td>' +
                '<td>' + (e.durationMs != null ? e.durationMs + ' ms' : '-') + '</td>' +
            '</tr>';
        }).join('');
    } catch (e) { /* ignore */ }
}

function showMcpAlert(msg, type) {
    const el = document.getElementById('mcpAlert');
    el.innerHTML = '<div class="alert alert-' + type + '">' + escHtml(msg) + '</div>';
    if (type !== 'info') setTimeout(() => { el.innerHTML = ''; }, 5000);
}

// ========== KNOWLEDGE BASE TAB ==========
let kbLoaded = false;
let allKbDocs = [];
let allKbColls = [];

async function loadKbDocs() {
    try {
        const resp = await fetch(CTX + '/api/admin/kb/documents');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allKbDocs = await resp.json();
        renderKbDocs();
    } catch (e) {
        document.getElementById('kbDocBody').innerHTML = '<tr><td colspan="6" class="empty-state" style="color:#ef4444">' + e.message + '</td></tr>';
    }
}

function renderKbDocs() {
    const filter = (document.getElementById('kbSearch').value || '').toLowerCase();
    const docs = allKbDocs.filter(d => !filter || d.title.toLowerCase().includes(filter) || (d.slug||'').includes(filter) || (d.tags||'').toLowerCase().includes(filter));
    if (docs.length === 0) {
        document.getElementById('kbDocBody').innerHTML = '<tr><td colspan="6" class="empty-state">Inga dokument' + (filter ? ' matchar sökningen' : ' ännu') + '</td></tr>';
        return;
    }
    document.getElementById('kbDocBody').innerHTML = docs.map(d => {
        const tags = d.tags ? d.tags.replace(/[\[\]"]/g, '').split(',').map(t => '<span class="badge-active" style="font-size:0.7em;padding:1px 6px">' + t.trim() + '</span>').join(' ') : '';
        const date = d.updatedAt ? new Date(d.updatedAt).toLocaleDateString('sv-SE') : '';
        return '<tr>' +
            '<td><strong>' + esc(d.title) + '</strong></td>' +
            '<td style="font-family:monospace;font-size:0.85em">' + esc(d.slug) + '</td>' +
            '<td>' + esc(d.fileType || 'markdown') + '</td>' +
            '<td>' + tags + '</td>' +
            '<td>' + date + '</td>' +
            '<td><button class="btn" style="padding:2px 8px;font-size:0.8em" onclick="editKbDoc(' + d.id + ')">Redigera</button> ' +
            '<button class="btn btn-danger" style="padding:2px 8px;font-size:0.8em" onclick="deleteKbDoc(' + d.id + ')">Ta bort</button></td>' +
            '</tr>';
    }).join('');
}

function filterKbDocs() { renderKbDocs(); }

function openKbDocModal(doc) {
    document.getElementById('kbDocEditId').value = doc ? doc.id : '';
    document.getElementById('kbDocTitle').value = doc ? doc.title : '';
    document.getElementById('kbDocSlug').value = doc ? doc.slug : '';
    document.getElementById('kbDocType').value = doc ? (doc.fileType || 'markdown') : 'markdown';
    document.getElementById('kbDocTags').value = doc ? (doc.tags || '').replace(/[\[\]"]/g, '') : '';
    document.getElementById('kbDocContent').value = doc ? (doc.content || '') : '';
    document.getElementById('kbDocModalTitle').textContent = doc ? 'Redigera dokument' : 'Nytt dokument';
    document.getElementById('kbDocModal').classList.add('open');
}
function closeKbDocModal() { document.getElementById('kbDocModal').classList.remove('open'); }

function autoSlug() {
    if (document.getElementById('kbDocEditId').value) return;
    const title = document.getElementById('kbDocTitle').value;
    document.getElementById('kbDocSlug').value = title.toLowerCase().replace(/[åä]/g,'a').replace(/ö/g,'o').replace(/[^a-z0-9\s-]/g,'').trim().replace(/\s+/g,'-');
}

async function editKbDoc(id) {
    try {
        const resp = await fetch(CTX + '/api/admin/kb/documents?id=' + id);
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const doc = await resp.json();
        openKbDocModal(doc);
    } catch (e) { showKbAlert('Kunde inte ladda dokument: ' + e.message, 'error'); }
}

async function saveKbDoc() {
    const id = document.getElementById('kbDocEditId').value;
    const tags = document.getElementById('kbDocTags').value.split(',').map(t => t.trim()).filter(t => t);
    const body = {
        title: document.getElementById('kbDocTitle').value,
        slug: document.getElementById('kbDocSlug').value,
        fileType: document.getElementById('kbDocType').value,
        tags: JSON.stringify(tags),
        content: document.getElementById('kbDocContent').value
    };
    if (id) body.id = parseInt(id);
    try {
        const resp = await fetch(CTX + '/api/admin/kb/documents', {
            method: id ? 'PUT' : 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
        if (!resp.ok) { const e = await resp.json(); throw new Error(e.error || 'HTTP ' + resp.status); }
        closeKbDocModal();
        loadKbDocs();
        showKbAlert('Dokument ' + (id ? 'uppdaterat' : 'skapat') + '!', 'success');
    } catch (e) { showKbAlert(e.message, 'error'); }
}

async function deleteKbDoc(id) {
    if (!confirm('Radera dokumentet permanent?')) return;
    try {
        const resp = await fetch(CTX + '/api/admin/kb/documents?id=' + id, { method: 'DELETE' });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        loadKbDocs();
        showKbAlert('Dokument raderat', 'info');
    } catch (e) { showKbAlert(e.message, 'error'); }
}

// --- Collections ---
async function loadKbColls() {
    try {
        const resp = await fetch(CTX + '/api/admin/kb/collections');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        allKbColls = await resp.json();
        renderKbColls();
    } catch (e) {
        document.getElementById('kbCollGrid').innerHTML = '<div class="empty-state" style="color:#ef4444">' + e.message + '</div>';
    }
}

function renderKbColls() {
    if (allKbColls.length === 0) {
        document.getElementById('kbCollGrid').innerHTML = '<div class="empty-state">Inga samlingar ännu. Skapa en samling för att exponera dokument som MCP-endpoint.</div>';
        return;
    }
    document.getElementById('kbCollGrid').innerHTML = allKbColls.map(c => {
        const status = c.isActive ? '<span class="badge-active">Aktiv</span>' : '<span class="badge-failed">Inaktiv</span>';
        return '<div class="sync-card">' +
            '<div class="sync-card-header"><strong>' + esc(c.name) + '</strong>' + status + '</div>' +
            '<div style="font-size:0.85em;color:var(--ic-text-muted);margin:4px 0">Prefix: <code>' + esc(c.toolPrefix) + '</code></div>' +
            '<div style="font-size:0.85em">' + (c.documentCount || 0) + ' dokument</div>' +
            '<div style="margin-top:8px;display:flex;gap:6px">' +
            '<button class="btn" style="padding:2px 8px;font-size:0.8em" onclick="editKbColl(' + c.id + ')">Redigera</button>' +
            '<button class="btn" style="padding:2px 8px;font-size:0.8em" onclick="openKbLinkModal(' + c.id + ')">Koppla dokument</button>' +
            '<button class="btn btn-danger" style="padding:2px 8px;font-size:0.8em" onclick="deleteKbColl(' + c.id + ')">Ta bort</button>' +
            '</div></div>';
    }).join('');
}

function openKbCollModal(coll) {
    document.getElementById('kbCollEditId').value = coll ? coll.id : '';
    document.getElementById('kbCollName').value = coll ? coll.name : '';
    document.getElementById('kbCollPrefix').value = coll ? coll.toolPrefix : '';
    document.getElementById('kbCollDesc').value = coll ? (coll.description || '') : '';
    document.getElementById('kbCollActive').checked = coll ? coll.isActive : true;
    document.getElementById('kbCollModalTitle').textContent = coll ? 'Redigera samling' : 'Ny samling';
    document.getElementById('kbCollModal').classList.add('open');
}
function closeKbCollModal() { document.getElementById('kbCollModal').classList.remove('open'); }

function autoPrefix() {
    if (document.getElementById('kbCollEditId').value) return;
    const name = document.getElementById('kbCollName').value;
    document.getElementById('kbCollPrefix').value = name.toLowerCase().replace(/[åä]/g,'a').replace(/ö/g,'o').replace(/[^a-z0-9\s_]/g,'').trim().replace(/\s+/g,'_');
}

function editKbColl(id) {
    const c = allKbColls.find(x => x.id === id);
    if (c) openKbCollModal(c);
}

async function saveKbColl() {
    const id = document.getElementById('kbCollEditId').value;
    const body = {
        name: document.getElementById('kbCollName').value,
        toolPrefix: document.getElementById('kbCollPrefix').value,
        description: document.getElementById('kbCollDesc').value,
        isActive: document.getElementById('kbCollActive').checked
    };
    if (id) body.id = parseInt(id);
    try {
        const resp = await fetch(CTX + '/api/admin/kb/collections', {
            method: id ? 'PUT' : 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
        if (!resp.ok) { const e = await resp.json(); throw new Error(e.error || 'HTTP ' + resp.status); }
        closeKbCollModal();
        loadKbColls();
        showKbAlert('Samling ' + (id ? 'uppdaterad' : 'skapad') + '!', 'success');
    } catch (e) { showKbAlert(e.message, 'error'); }
}

async function deleteKbColl(id) {
    if (!confirm('Radera samlingen permanent?')) return;
    try {
        const resp = await fetch(CTX + '/api/admin/kb/collections?id=' + id, { method: 'DELETE' });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        loadKbColls();
        showKbAlert('Samling raderad', 'info');
    } catch (e) { showKbAlert(e.message, 'error'); }
}

// --- Link documents to collection ---
async function openKbLinkModal(collId) {
    document.getElementById('kbLinkCollId').value = collId;
    const coll = allKbColls.find(c => c.id === collId);
    document.getElementById('kbLinkModalTitle').textContent = 'Koppla dokument till ' + (coll ? coll.name : '');
    // Load current links
    let linked = [];
    try {
        const resp = await fetch(CTX + '/api/admin/kb/collections/documents?id=' + collId);
        if (resp.ok) linked = (await resp.json()).map(d => d.id);
    } catch(e) {}
    // Render checkboxes for all documents
    document.getElementById('kbLinkList').innerHTML = allKbDocs.length === 0 ?
        '<div class="empty-state">Inga dokument att koppla. Skapa dokument först.</div>' :
        allKbDocs.map(d =>
            '<label style="display:block;padding:6px 0;border-bottom:1px solid var(--ic-border);cursor:pointer">' +
            '<input type="checkbox" value="' + d.id + '"' + (linked.includes(d.id) ? ' checked' : '') + ' style="margin-right:8px">' +
            esc(d.title) + ' <span style="color:var(--ic-text-muted);font-size:0.85em">(' + esc(d.slug) + ')</span></label>'
        ).join('');
    document.getElementById('kbLinkModal').classList.add('open');
}
function closeKbLinkModal() { document.getElementById('kbLinkModal').classList.remove('open'); }

async function saveKbLinks() {
    const collId = parseInt(document.getElementById('kbLinkCollId').value);
    const checks = document.querySelectorAll('#kbLinkList input[type=checkbox]:checked');
    const docIds = Array.from(checks).map(c => parseInt(c.value));
    try {
        const resp = await fetch(CTX + '/api/admin/kb/collections/documents', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ collectionId: collId, documentIds: docIds })
        });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        closeKbLinkModal();
        loadKbColls();
        showKbAlert('Kopplingar sparade!', 'success');
    } catch (e) { showKbAlert(e.message, 'error'); }
}

function showKbAlert(msg, type) {
    const el = document.getElementById('kbAlert');
    el.innerHTML = '<div class="alert alert-' + type + '">' + esc(msg) + '</div>';
    if (type !== 'info') setTimeout(() => { el.innerHTML = ''; }, 5000);
}

// ========== INIT ==========
loadUsers();
loadSyncConfigs();

// Handle OAuth callback redirect
if (new URLSearchParams(window.location.search).get('mcpAuth') === 'success') {
    switchTab('mcp');
    setTimeout(() => showMcpAlert('OAuth-auktorisering lyckades! Klicka Testa för att verifiera.', 'success'), 500);
    // Clean URL
    history.replaceState(null, '', window.location.pathname);
}
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
