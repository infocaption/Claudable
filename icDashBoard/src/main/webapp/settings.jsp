<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) { response.sendRedirect(request.getContextPath() + "/login"); return; }
    String ctxPath = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= session.getAttribute("csrfToken") != null ? session.getAttribute("csrfToken") : "" %>">
    <title>Inställningar</title>
    <script nonce="<%= request.getAttribute("cspNonce") %>">
    (function(){
        var t=document.querySelector('meta[name="csrf-token"]'),v=t?t.content:'';
        // If in iframe, request CSRF token from parent dashboard (most reliable source)
        if(window.parent!==window){
            window.addEventListener('message',function(e){
                if(e.origin!==window.location.origin)return;
                if(e.data&&e.data.type==='CSRF_TOKEN'&&e.data.token){v=e.data.token;}
            });
            window.parent.postMessage({type:'GET_CSRF_TOKEN'},window.location.origin);
        }
        var o=window.fetch;
        window.fetch=function(u,i){i=i||{};var m=(i.method||'GET').toUpperCase();
        if(m!=='GET'&&m!=='HEAD'&&m!=='OPTIONS'){i.headers=i.headers||{};
        if(i.headers instanceof Headers){i.headers.set('X-CSRF-Token',v);}
        else{i.headers['X-CSRF-Token']=v;}}return o.call(this,u,i);};
    })();
    </script>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="<%= ctxPath %>/shared/ic-styles.css">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--ic-bg-light, #f8f9fc);
            color: var(--ic-text-dark, #323232);
            font-size: 14px;
            padding: 0;
        }

        .page-header {
            background: linear-gradient(135deg, var(--ic-primary, #5458F2) 0%, #764ba2 100%);
            color: white;
            padding: 30px 32px;
        }
        .page-header h1 { font-size: 1.5em; font-weight: 700; }
        .page-header p { font-size: 0.85em; opacity: 0.85; margin-top: 4px; }

        .settings-container {
            max-width: 700px;
            margin: 0 auto;
            padding: 28px 24px;
        }

        .settings-section {
            background: white;
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 20px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.06);
            border: 1px solid #e5e7eb;
        }

        .settings-section-title {
            font-size: 1.05em;
            font-weight: 700;
            color: var(--ic-text-dark, #323232);
            margin-bottom: 4px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .settings-section-desc {
            font-size: 0.82em;
            color: var(--ic-text-muted, #6c757d);
            margin-bottom: 20px;
        }

        .avatar-section {
            display: flex;
            align-items: center;
            gap: 18px;
            margin-bottom: 20px;
        }

        .avatar-large {
            width: 72px;
            height: 72px;
            border-radius: 50%;
            background: var(--ic-secondary, #D4D5FC);
            color: var(--ic-primary, #5458F2);
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            font-size: 1.5em;
            flex-shrink: 0;
            position: relative;
            cursor: pointer;
            overflow: hidden;
        }
        .avatar-large img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            border-radius: 50%;
        }
        .avatar-large:hover::after {
            content: '\1F4F7';
            position: absolute;
            inset: 0;
            background: rgba(0,0,0,0.45);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.2em;
            border-radius: 50%;
        }
        .avatar-actions {
            display: flex;
            gap: 8px;
            margin-top: 6px;
        }
        .avatar-actions button {
            font-size: 0.75em;
            padding: 3px 10px;
            border-radius: 12px;
            border: 1px solid #d1d5db;
            background: #f9fafb;
            cursor: pointer;
            font-family: inherit;
        }
        .avatar-actions button:hover { background: #e5e7eb; }
        .avatar-actions .btn-remove { color: #dc2626; border-color: #fca5a5; }
        .avatar-actions .btn-remove:hover { background: #fee2e2; }

        .avatar-info { flex: 1; }
        .avatar-info .name { font-weight: 600; font-size: 1.05em; }
        .avatar-info .role {
            font-size: 0.78em;
            color: var(--ic-text-muted, #6c757d);
            margin-top: 2px;
        }

        .form-group { margin-bottom: 16px; }
        .form-group label {
            display: block;
            font-weight: 500;
            font-size: 0.85em;
            margin-bottom: 5px;
            color: var(--ic-text-dark, #323232);
        }
        .form-group input {
            width: 100%;
            padding: 9px 12px;
            border: 1px solid #d1d5db;
            border-radius: 6px;
            font-size: 0.85em;
            font-family: inherit;
            transition: border-color 0.2s;
        }
        .form-group input:focus {
            outline: none;
            border-color: var(--ic-primary, #5458F2);
            box-shadow: 0 0 0 3px rgba(84,88,242,0.1);
        }
        .form-group input[readonly] {
            background: #f3f4f6;
            cursor: not-allowed;
            color: var(--ic-text-muted, #6c757d);
        }
        .form-hint {
            font-size: 0.75em;
            color: var(--ic-text-muted, #6c757d);
            margin-top: 4px;
        }
        .form-row {
            display: flex;
            gap: 12px;
        }
        .form-row .form-group { flex: 1; }

        .btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 9px 20px;
            border-radius: 6px;
            font-size: 0.85em;
            font-weight: 600;
            cursor: pointer;
            font-family: inherit;
            border: 1px solid #e5e7eb;
            transition: all 0.15s;
            background: white;
            color: var(--ic-text-dark, #323232);
        }
        .btn:hover { background: var(--ic-bg-light, #f8f9fc); }
        .btn-primary {
            background: var(--ic-primary, #5458F2);
            color: white;
            border-color: var(--ic-primary, #5458F2);
        }
        .btn-primary:hover { background: var(--ic-primary-hover, #4347d9); }

        .alert {
            padding: 10px 14px;
            border-radius: 8px;
            margin-bottom: 16px;
            font-size: 0.85em;
            display: none;
        }
        .alert.show { display: block; }
        .alert-success { background: #d1fae5; color: #065f46; }
        .alert-error { background: #fee2e2; color: #991b1b; }

        .sso-badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 2px 8px;
            background: #dbeafe;
            color: #1e40af;
            border-radius: 12px;
            font-size: 0.72em;
            font-weight: 600;
        }

        .placeholder-section {
            text-align: center;
            padding: 20px;
            color: var(--ic-text-muted, #6c757d);
            font-size: 0.88em;
        }

        @media (max-width: 600px) {
            .settings-container { padding: 16px 12px; }
            .form-row { flex-direction: column; gap: 0; }
            .avatar-section { flex-direction: column; text-align: center; }
        }
    </style>
</head>
<body>
    <div class="page-header">
        <h1>&#9881; Inställningar</h1>
        <p>Hantera din profil och dina preferenser</p>
    </div>

    <div class="settings-container">
        <!-- Alert area -->
        <div class="alert" id="settingsAlert"></div>

        <!-- Profile Section -->
        <div class="settings-section">
            <div class="settings-section-title">
                &#128100; Profil
                <% if (user.isSsoUser()) { %><span class="sso-badge">&#128279; SSO</span><% } %>
            </div>
            <div class="settings-section-desc">Uppdatera ditt namn och din e-postadress</div>

            <div class="avatar-section">
                <div class="avatar-large" id="settingsAvatar" onclick="document.getElementById('avatarFileInput').click()" title="Klicka f&ouml;r att byta bild"><%
                    if (user.getProfilePictureUrl() != null && !user.getProfilePictureUrl().isEmpty()) {
                %><img src="<%= request.getContextPath() + "/" + user.getProfilePictureUrl() %>" alt="Profilbild" id="avatarImg"><%
                    } else {
                %><span id="avatarInitial"><%= user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName().substring(0, 1).toUpperCase() : "?" %></span><%
                    }
                %></div>
                <div class="avatar-info">
                    <div class="name" id="settingsDisplayName"><%= user.getFullName() != null ? user.getFullName() : "" %></div>
                    <div class="role"><%= user.isAdmin() ? "Administrat&ouml;r" : "Anv&auml;ndare" %><% if (user.isSsoUser()) { %> &middot; SSO<% } %></div>
                    <div class="avatar-actions">
                        <button onclick="document.getElementById('avatarFileInput').click()">&#128247; Byt bild</button>
                        <button class="btn-remove" onclick="removeAvatar()" id="removeAvatarBtn" style="<%= user.getProfilePictureUrl() == null || user.getProfilePictureUrl().isEmpty() ? "display:none" : "" %>">&#128465; Ta bort</button>
                    </div>
                </div>
                <input type="file" id="avatarFileInput" accept="image/jpeg,image/png,image/gif,image/webp" style="display:none" onchange="uploadAvatar(this)">
            </div>

            <div class="form-group">
                <label>Användarnamn</label>
                <input type="text" value="<%= user.getUsername() != null ? user.getUsername() : "" %>" readonly>
                <div class="form-hint">Användarnamn kan inte ändras</div>
            </div>

            <div class="form-row">
                <div class="form-group">
                    <label>Namn</label>
                    <input type="text" id="settingsFullName" value="<%= user.getFullName() != null ? user.getFullName() : "" %>" placeholder="Ditt namn">
                </div>
                <div class="form-group">
                    <label>E-post</label>
                    <input type="email" id="settingsEmail" value="<%= user.getEmail() != null ? user.getEmail() : "" %>" placeholder="din@epost.se"
                        <% if (user.isSsoUser()) { %>readonly<% } %>>
                    <% if (user.isSsoUser()) { %><div class="form-hint">E-post hanteras av SSO-leverantören</div><% } %>
                </div>
            </div>

            <button class="btn btn-primary" onclick="saveProfile()">Spara profil</button>
        </div>

        <!-- Password Section (hidden for SSO users) -->
        <% if (!user.isSsoUser()) { %>
        <div class="settings-section">
            <div class="settings-section-title">&#128274; Lösenord</div>
            <div class="settings-section-desc">Byt ditt lösenord</div>

            <div class="form-group">
                <label>Nuvarande lösenord</label>
                <input type="password" id="currentPassword" placeholder="Ange nuvarande lösenord">
            </div>

            <div class="form-row">
                <div class="form-group">
                    <label>Nytt lösenord</label>
                    <input type="password" id="newPassword" placeholder="Minst 6 tecken">
                </div>
                <div class="form-group">
                    <label>Bekräfta nytt lösenord</label>
                    <input type="password" id="confirmPassword" placeholder="Upprepa lösenordet">
                </div>
            </div>
            <div class="form-hint" style="margin-bottom:14px">Lösenordet måste vara minst 6 tecken långt</div>

            <button class="btn btn-primary" onclick="changePassword()">Byt lösenord</button>
        </div>
        <% } %>

        <!-- API Tokens Section -->
        <div class="settings-section" id="apiTokenSection" style="display:none">
            <div class="settings-section-title">&#128273; API-tokens</div>
            <div class="settings-section-desc">Hantera personliga API-tokens f&ouml;r script och automation</div>

            <div id="tokenList" style="margin-bottom:16px">
                <div style="color:var(--ic-text-muted);font-size:0.85em">Laddar tokens...</div>
            </div>

            <div id="createTokenForm" style="display:none;margin-bottom:16px;padding:16px;background:#f8f9fa;border-radius:8px;border:1px solid #e5e7eb">
                <div class="form-group" style="margin-bottom:12px">
                    <label>Tokennamn (valfritt)</label>
                    <input type="text" id="newTokenName" placeholder="T.ex. Mitt PowerShell-script" maxlength="100">
                    <div class="form-hint">Ett beskrivande namn s&aring; du k&auml;nner igen token senare</div>
                </div>
                <div style="display:flex;gap:8px">
                    <button class="btn btn-primary" onclick="generateToken()">Generera token</button>
                    <button class="btn" onclick="document.getElementById('createTokenForm').style.display='none'">Avbryt</button>
                </div>
            </div>

            <div id="newTokenResult" style="display:none;margin-bottom:16px;padding:16px;background:#d1fae5;border-radius:8px;border:1px solid #6ee7b7">
                <div style="font-weight:600;margin-bottom:8px;color:#065f46">&#9989; Token skapad! Kopiera den nu &mdash; den visas aldrig igen.</div>
                <div style="display:flex;gap:8px;align-items:center">
                    <input type="text" id="newTokenValue" readonly
                        style="flex:1;font-family:monospace;font-size:0.85em;padding:8px 12px;border:1px solid #6ee7b7;border-radius:6px;background:white">
                    <button class="btn" onclick="copyToken()" title="Kopiera">&#128203; Kopiera</button>
                </div>
            </div>

            <button class="btn btn-primary" id="createTokenBtn" onclick="document.getElementById('createTokenForm').style.display='block';document.getElementById('newTokenResult').style.display='none'">
                &#10133; Skapa ny token
            </button>
        </div>

        <!-- Jira Integration Section -->
        <div class="settings-section">
            <div class="settings-section-title">&#128203; Jira-integration</div>
            <div class="settings-section-desc">Anslut till ditt Jira-konto f&ouml;r att visa dina &auml;renden</div>

            <div class="form-group">
                <label>Jira-dom&auml;n</label>
                <input type="text" id="jiraDomain" placeholder="mycompany.atlassian.net">
                <div class="form-hint">Din Jira Cloud-dom&auml;n (utan https://)</div>
            </div>

            <div class="form-row">
                <div class="form-group">
                    <label>E-post</label>
                    <input type="email" id="jiraEmail" placeholder="din@epost.se">
                    <div class="form-hint">Ditt Jira-kontos e-postadress</div>
                </div>
                <div class="form-group">
                    <label>API-token</label>
                    <input type="password" id="jiraApiToken" placeholder="Klistra in din token">
                    <div class="form-hint">
                        Skapa p&aring;
                        <a href="https://id.atlassian.com/manage-profile/security/api-tokens" target="_blank" style="color:var(--ic-primary,#5458F2)">id.atlassian.com</a>
                    </div>
                </div>
            </div>

            <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;">
                <button class="btn btn-primary" onclick="saveJiraSettings()">Spara Jira-inst&auml;llningar</button>
                <button class="btn" onclick="testJiraConnection()">Testa anslutning</button>
            </div>
            <div id="jiraTestResult" style="margin-top:12px;font-size:0.85em;display:none;padding:8px 12px;border-radius:6px;"></div>
        </div>

        <!-- Widget Preferences Section -->
        <div class="settings-section">
            <div class="settings-section-title">&#128295; Widgetinställningar</div>
            <div class="settings-section-desc">Välj vilka widgets som visas i widgetbaren</div>
            <div class="placeholder-section">
                Widgetinställningar hanteras via kugghjulet (&#9881;) i widgetbaren på dashboarden.
            </div>
        </div>

        <!-- Appearance Section -->
        <div class="settings-section">
            <div class="settings-section-title">&#127912; Utseende</div>
            <div class="settings-section-desc">Tema och visningsinställningar</div>
            <div class="placeholder-section">
                Tema- och utseendeinställningar kommer snart.
            </div>
        </div>
    </div>

    <script nonce="<%= request.getAttribute("cspNonce") %>">
        var CTX = '<%= ctxPath %>';

        function showSettingsAlert(type, message) {
            var el = document.getElementById('settingsAlert');
            el.className = 'alert alert-' + type + ' show';
            el.textContent = message;
            setTimeout(function() { el.classList.remove('show'); }, 5000);
        }

        async function saveProfile() {
            var fullName = document.getElementById('settingsFullName').value.trim();
            var email = document.getElementById('settingsEmail').value.trim();

            if (!fullName) {
                showSettingsAlert('error', 'Namn krävs');
                return;
            }
            if (!email || email.indexOf('@') < 0) {
                showSettingsAlert('error', 'Ange en giltig e-postadress');
                return;
            }

            try {
                var resp = await fetch(CTX + '/api/user/settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'updateProfile', fullName: fullName, email: email })
                });
                var data = await resp.json();
                if (!resp.ok) throw new Error(data.error || 'Kunde inte spara');

                // Update local display
                document.getElementById('settingsDisplayName').textContent = fullName;
                document.getElementById('settingsAvatar').textContent = fullName.substring(0, 1).toUpperCase();

                showSettingsAlert('success', 'Profil uppdaterad');

                // Notify parent dashboard to update topbar
                if (window.parent !== window) {
                    window.parent.postMessage({
                        type: 'REFRESH_USER',
                        fullName: fullName
                    }, window.location.origin);
                }
            } catch (e) {
                showSettingsAlert('error', e.message);
            }
        }

        async function changePassword() {
            var currentPassword = document.getElementById('currentPassword').value;
            var newPassword = document.getElementById('newPassword').value;
            var confirmPassword = document.getElementById('confirmPassword').value;

            if (!currentPassword || !newPassword || !confirmPassword) {
                showSettingsAlert('error', 'Alla lösenordsfält måste fyllas i');
                return;
            }
            if (newPassword.length < 6) {
                showSettingsAlert('error', 'Nytt lösenord måste vara minst 6 tecken');
                return;
            }
            if (newPassword !== confirmPassword) {
                showSettingsAlert('error', 'Lösenorden matchar inte');
                return;
            }

            try {
                var resp = await fetch(CTX + '/api/user/settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        action: 'changePassword',
                        currentPassword: currentPassword,
                        newPassword: newPassword,
                        confirmPassword: confirmPassword
                    })
                });
                var data = await resp.json();
                if (!resp.ok) throw new Error(data.error || 'Kunde inte byta lösenord');

                document.getElementById('currentPassword').value = '';
                document.getElementById('newPassword').value = '';
                document.getElementById('confirmPassword').value = '';

                showSettingsAlert('success', 'Lösenord ändrat');
            } catch (e) {
                showSettingsAlert('error', e.message);
            }
        }

        // --- Avatar Upload ---
        async function uploadAvatar(input) {
            if (!input.files || !input.files[0]) return;
            var file = input.files[0];
            if (file.size > 2 * 1024 * 1024) {
                showSettingsAlert('error', 'Bilden f\u00e5r vara max 2 MB');
                input.value = '';
                return;
            }

            var formData = new FormData();
            formData.append('avatar', file);

            try {
                // Global fetch wrapper auto-injects X-CSRF-Token header
                var resp = await fetch(CTX + '/api/user/avatar', {
                    method: 'POST',
                    body: formData
                });
                var data = await resp.json();
                if (!resp.ok) throw new Error(data.error || 'Upload misslyckades');

                // Update avatar display
                var avatarEl = document.getElementById('settingsAvatar');
                var existingImg = document.getElementById('avatarImg');
                if (existingImg) {
                    existingImg.src = CTX + '/' + data.url;
                } else {
                    var initial = document.getElementById('avatarInitial');
                    if (initial) initial.remove();
                    var img = document.createElement('img');
                    img.id = 'avatarImg';
                    img.alt = 'Profilbild';
                    img.src = CTX + '/' + data.url;
                    avatarEl.appendChild(img);
                }
                document.getElementById('removeAvatarBtn').style.display = '';
                showSettingsAlert('success', 'Profilbild uppdaterad');

                // Notify dashboard
                if (window.parent !== window) {
                    window.parent.postMessage({ type: 'REFRESH_USER', avatarUrl: data.url }, window.location.origin);
                }
            } catch (e) {
                showSettingsAlert('error', e.message);
            }
            input.value = '';
        }

        async function removeAvatar() {
            try {
                var resp = await fetch(CTX + '/api/user/avatar', { method: 'DELETE' });
                var data = await resp.json();
                if (!resp.ok) throw new Error(data.error || 'Kunde inte ta bort');

                // Restore initial
                var avatarEl = document.getElementById('settingsAvatar');
                var img = document.getElementById('avatarImg');
                if (img) img.remove();
                var nameEl = document.getElementById('settingsFullName');
                var initial = nameEl && nameEl.value ? nameEl.value.charAt(0).toUpperCase() : '?';
                var span = document.createElement('span');
                span.id = 'avatarInitial';
                span.textContent = initial;
                avatarEl.appendChild(span);
                document.getElementById('removeAvatarBtn').style.display = 'none';
                showSettingsAlert('success', 'Profilbild borttagen');

                if (window.parent !== window) {
                    window.parent.postMessage({ type: 'REFRESH_USER', avatarUrl: '' }, window.location.origin);
                }
            } catch (e) {
                showSettingsAlert('error', e.message);
            }
        }

        // --- Jira Settings ---
        async function loadJiraSettings() {
            try {
                var resp = await fetch(CTX + '/api/preferences?key=jira.domain');
                if (resp.ok) { var d = await resp.json(); document.getElementById('jiraDomain').value = d.value || ''; }
            } catch (e) { /* ignore */ }
            try {
                var resp2 = await fetch(CTX + '/api/preferences?key=jira.email');
                if (resp2.ok) { var d2 = await resp2.json(); document.getElementById('jiraEmail').value = d2.value || ''; }
            } catch (e) { /* ignore */ }
            // Don't load API token for security
        }

        async function saveJiraSettings() {
            var domain = document.getElementById('jiraDomain').value.trim();
            var email = document.getElementById('jiraEmail').value.trim();
            var token = document.getElementById('jiraApiToken').value.trim();

            if (!domain || !email || !token) {
                showSettingsAlert('error', 'Alla Jira-f\u00e4lt m\u00e5ste fyllas i');
                return;
            }
            // Clean domain
            domain = domain.replace(/^https?:\/\//, '').replace(/\/$/, '');

            try {
                var resp = await fetch(CTX + '/api/preferences', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ preferences: [
                        { key: 'jira.domain', value: domain },
                        { key: 'jira.email', value: email },
                        { key: 'jira.apiToken', value: token }
                    ]})
                });
                if (!resp.ok) throw new Error('Kunde inte spara');
                showSettingsAlert('success', 'Jira-inst\u00e4llningar sparade');
                document.getElementById('jiraApiToken').value = '';
                document.getElementById('jiraDomain').value = domain;
            } catch (e) {
                showSettingsAlert('error', e.message);
            }
        }

        async function testJiraConnection() {
            var rd = document.getElementById('jiraTestResult');
            rd.style.display = 'block';
            rd.style.background = '#f3f4f6';
            rd.style.color = 'var(--ic-text-muted)';
            rd.textContent = 'Testar anslutning...';

            try {
                var resp = await fetch(CTX + '/api/jira/status');
                var data = await resp.json();
                if (data.configured === false) {
                    rd.style.background = '#fee2e2'; rd.style.color = '#991b1b';
                    rd.textContent = '\u274c ' + data.error;
                } else if (data.reachable) {
                    rd.style.background = '#d1fae5'; rd.style.color = '#065f46';
                    rd.textContent = '\u2705 Anslutning lyckades! Inloggad som: ' + (data.displayName || 'ok');
                } else {
                    rd.style.background = '#fee2e2'; rd.style.color = '#991b1b';
                    rd.textContent = '\u274c ' + (data.error || 'Kunde inte ansluta');
                }
            } catch (e) {
                rd.style.background = '#fee2e2'; rd.style.color = '#991b1b';
                rd.textContent = '\u274c Fel: ' + e.message;
            }
        }

        // ========== API TOKENS ==========

        async function loadTokens() {
            try {
                var resp = await fetch(CTX + '/api/tokens');
                if (resp.status === 403) {
                    // User doesn't have API access — hide section
                    document.getElementById('apiTokenSection').style.display = 'none';
                    return;
                }
                if (!resp.ok) throw new Error('HTTP ' + resp.status);

                // Show section
                document.getElementById('apiTokenSection').style.display = '';

                var tokens = await resp.json();
                renderTokens(tokens);
            } catch (e) {
                document.getElementById('tokenList').innerHTML =
                    '<div style="color:#ef4444;font-size:0.85em">Kunde inte ladda tokens: ' + e.message + '</div>';
            }
        }

        function renderTokens(tokens) {
            var container = document.getElementById('tokenList');

            if (!tokens || tokens.length === 0) {
                container.innerHTML = '<div style="color:var(--ic-text-muted);font-size:0.85em;padding:12px 0">Inga tokens skapade \u00e4nnu.</div>';
                return;
            }

            var html = '<table style="width:100%;border-collapse:collapse;font-size:0.85em">';
            html += '<thead><tr style="background:#f8f9fa">';
            html += '<th style="padding:8px 10px;text-align:left;font-weight:600;color:var(--ic-text-muted)">Prefix</th>';
            html += '<th style="padding:8px 10px;text-align:left;font-weight:600;color:var(--ic-text-muted)">Namn</th>';
            html += '<th style="padding:8px 10px;text-align:left;font-weight:600;color:var(--ic-text-muted)">Status</th>';
            html += '<th style="padding:8px 10px;text-align:left;font-weight:600;color:var(--ic-text-muted)">Utg\u00e5r</th>';
            html += '<th style="padding:8px 10px;text-align:left;font-weight:600;color:var(--ic-text-muted)">Senast anv\u00e4nd</th>';
            html += '<th style="padding:8px 10px;text-align:right;font-weight:600;color:var(--ic-text-muted)"></th>';
            html += '</tr></thead><tbody>';

            for (var i = 0; i < tokens.length; i++) {
                var t = tokens[i];
                var statusBadge = '';
                if (t.status === 'active') statusBadge = '<span style="display:inline-block;padding:2px 8px;border-radius:8px;font-size:0.8em;font-weight:600;background:#d1fae5;color:#065f46">Aktiv</span>';
                else if (t.status === 'expired') statusBadge = '<span style="display:inline-block;padding:2px 8px;border-radius:8px;font-size:0.8em;font-weight:600;background:#fee2e2;color:#991b1b">Utg\u00e5ngen</span>';
                else statusBadge = '<span style="display:inline-block;padding:2px 8px;border-radius:8px;font-size:0.8em;font-weight:600;background:#f3f4f6;color:#374151">\u00c5terkallad</span>';

                var lastUsed = t.lastUsed ? new Date(t.lastUsed).toLocaleDateString('sv-SE') : 'Aldrig';
                var expiresAt = t.expiresAt ? new Date(t.expiresAt).toLocaleDateString('sv-SE') : '-';

                html += '<tr style="border-bottom:1px solid #f0f0f0">';
                html += '<td style="padding:8px 10px;font-family:monospace">' + esc(t.tokenPrefix) + '...</td>';
                html += '<td style="padding:8px 10px">' + esc(t.name || '(Namnl\u00f6s)') + '</td>';
                html += '<td style="padding:8px 10px">' + statusBadge + '</td>';
                html += '<td style="padding:8px 10px">' + expiresAt + '</td>';
                html += '<td style="padding:8px 10px">' + lastUsed + '</td>';
                html += '<td style="padding:8px 10px;text-align:right">';
                if (t.status === 'active') {
                    html += '<button onclick="revokeToken(' + t.id + ')" style="background:none;border:none;color:#ef4444;cursor:pointer;font-size:0.85em;padding:4px 8px;border-radius:4px" title="\u00c5terkalla">\u274c \u00c5terkalla</button>';
                }
                html += '</td>';
                html += '</tr>';
            }

            html += '</tbody></table>';
            container.innerHTML = html;
        }

        async function generateToken() {
            var name = document.getElementById('newTokenName').value.trim();

            try {
                var body = {};
                if (name) body.name = name;

                var resp = await fetch(CTX + '/api/tokens', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });
                var data = await resp.json();
                if (!resp.ok) throw new Error(data.error || 'Kunde inte skapa token');

                // Show the token (only time it's visible)
                document.getElementById('newTokenValue').value = data.token;
                document.getElementById('newTokenResult').style.display = '';
                document.getElementById('createTokenForm').style.display = 'none';
                document.getElementById('newTokenName').value = '';

                showSettingsAlert('success', 'Token skapad! Kopiera den innan du lämnar sidan.');

                // Reload token list
                loadTokens();
            } catch (e) {
                showSettingsAlert('error', e.message);
            }
        }

        async function revokeToken(tokenId) {
            if (!confirm('\u00c4r du s\u00e4ker? Token \u00e5terkallas permanent och kan inte \u00e5terskapas.')) return;

            try {
                var resp = await fetch(CTX + '/api/tokens?id=' + tokenId, {
                    method: 'DELETE'
                });
                var data = await resp.json();
                if (!resp.ok) throw new Error(data.error || 'Kunde inte \u00e5terkalla');

                showSettingsAlert('success', 'Token \u00e5terkallad');
                loadTokens();
            } catch (e) {
                showSettingsAlert('error', e.message);
            }
        }

        function copyToken() {
            var input = document.getElementById('newTokenValue');
            var text = input.value;
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).then(function() {
                    showSettingsAlert('success', 'Token kopierad till urklipp!');
                }).catch(function() {
                    input.select();
                    document.execCommand('copy');
                    showSettingsAlert('success', 'Token kopierad till urklipp!');
                });
            } else {
                input.select();
                document.execCommand('copy');
                showSettingsAlert('success', 'Token kopierad till urklipp!');
            }
        }

        function esc(s) {
            if (!s) return '';
            var d = document.createElement('div');
            d.textContent = s;
            return d.innerHTML;
        }

        // Load tokens on page load
        loadTokens();

        // Load Jira settings on page load
        if (document.getElementById('jiraDomain')) { loadJiraSettings(); }

        // Hide page-header when loaded in iframe
        if (window !== window.top) {
            document.addEventListener('DOMContentLoaded', function() {
                var header = document.querySelector('.page-header');
                if (header) header.style.display = 'none';
            });
        }
    </script>
</body>
</html>
