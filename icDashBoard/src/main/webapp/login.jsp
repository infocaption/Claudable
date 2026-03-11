<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.util.AppConfig" %>
<%@ page import="com.infocaption.dashboard.util.AppVersion" %>
<%@ page import="com.infocaption.dashboard.util.Esc" %>
<%
    // Check login method configuration: "both", "password", "sso"
    String loginMethods = "both";
    try {
        loginMethods = AppConfig.get("auth.loginMethods", "both");
    } catch (Exception e) {
        // Fallback to both if AppConfig unavailable
    }

    // If only SSO is enabled, auto-redirect to SAML login
    if ("sso".equals(loginMethods) && request.getParameter("error") == null && request.getAttribute("error") == null) {
        response.sendRedirect(request.getContextPath() + "/saml/login");
        return;
    }

    boolean showPassword = "both".equals(loginMethods) || "password".equals(loginMethods);
    boolean showSso = "both".equals(loginMethods) || "sso".equals(loginMethods);
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Logga in - InfoCaption Dashboard</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/shared/ic-styles.css">
    <style>
        body {
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            background: linear-gradient(135deg, var(--ic-bg-light) 0%, var(--ic-secondary) 100%);
            padding: 20px;
        }

        .login-card {
            background: var(--ic-bg-white);
            border-radius: var(--ic-radius-xl);
            box-shadow: var(--ic-shadow-xl);
            padding: 40px;
            width: 100%;
            max-width: 420px;
            text-align: center;
        }

        .login-logo {
            width: 70px;
            height: 70px;
            margin-bottom: 20px;
            filter: drop-shadow(0 8px 24px rgba(84, 88, 242, 0.3));
        }

        .login-title {
            font-size: 1.5em;
            font-weight: 700;
            color: var(--ic-text-dark);
            margin-bottom: 6px;
        }

        .login-subtitle {
            font-size: 0.9em;
            color: var(--ic-text-muted);
            margin-bottom: 30px;
        }

        .form-group {
            margin-bottom: 18px;
            text-align: left;
        }

        .login-card .ic-input {
            padding: 12px 14px;
        }

        .login-btn {
            width: 100%;
            padding: 14px;
            margin-top: 8px;
        }

        .login-footer {
            margin-top: 24px;
            font-size: 0.85em;
            color: var(--ic-text-muted);
        }

        .login-footer a {
            color: var(--ic-primary);
            text-decoration: none;
            font-weight: 600;
        }

        .login-footer a:hover {
            text-decoration: underline;
        }

        .error-message {
            background: var(--ic-danger-light);
            color: var(--ic-danger-dark);
            padding: 12px 16px;
            border-radius: var(--ic-radius-md);
            border-left: 4px solid var(--ic-danger);
            margin-bottom: 20px;
            font-size: 0.85em;
            text-align: left;
        }

        .success-message {
            background: var(--ic-success-light);
            color: var(--ic-success-dark);
            padding: 12px 16px;
            border-radius: var(--ic-radius-md);
            border-left: 4px solid var(--ic-success);
            margin-bottom: 20px;
            font-size: 0.85em;
            text-align: left;
        }

        .sso-divider {
            display: flex;
            align-items: center;
            margin: 24px 0 20px;
            gap: 12px;
        }

        .sso-divider::before,
        .sso-divider::after {
            content: '';
            flex: 1;
            height: 1px;
            background: #e0e0e0;
        }

        .sso-divider span {
            font-size: 0.8em;
            color: var(--ic-text-muted);
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .sso-btn {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
            width: 100%;
            padding: 12px 14px;
            border: 1.5px solid #e0e0e0;
            border-radius: var(--ic-radius-md);
            background: var(--ic-bg-white);
            color: var(--ic-text-dark);
            font-size: 0.95em;
            font-weight: 500;
            text-decoration: none;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .sso-btn:hover {
            border-color: #0078d4;
            background: #f5f9ff;
            box-shadow: 0 2px 8px rgba(0, 120, 212, 0.12);
        }

        .sso-btn svg {
            width: 20px;
            height: 20px;
            flex-shrink: 0;
        }
    </style>
</head>
<body>
    <div class="login-card">
        <img src="${pageContext.request.contextPath}/assets/logga.png" alt="InfoCaption" class="login-logo">
        <h1 class="login-title">InfoCaption Dashboard</h1>
        <p class="login-subtitle">Logga in för att komma åt dashboarden</p>

        <% if (request.getParameter("registered") != null) { %>
            <div class="success-message">Kontot har skapats! Du kan nu logga in.</div>
        <% } %>

        <% if ("sso".equals(request.getParameter("error"))) { %>
            <div class="error-message">SSO-inloggning misslyckades. Försök igen eller logga in med lösenord.</div>
        <% } %>

        <% if (request.getAttribute("error") != null) { %>
            <div class="error-message"><%= Esc.h((String) request.getAttribute("error")) %></div>
        <% } %>

        <% if (showPassword) { %>
        <form method="post" action="${pageContext.request.contextPath}/login">
            <div class="form-group">
                <label class="ic-label" for="username">Användarnamn</label>
                <input type="text" id="username" name="username" class="ic-input"
                       placeholder="Ange ditt användarnamn" required autofocus
                       value="<%= Esc.h((String) request.getAttribute("username")) %>">
            </div>
            <div class="form-group">
                <label class="ic-label" for="password">Lösenord</label>
                <input type="password" id="password" name="password" class="ic-input"
                       placeholder="Ange ditt lösenord" required>
            </div>
            <button type="submit" class="ic-btn ic-btn-primary ic-btn-lg login-btn">
                Logga in
            </button>
        </form>
        <% } %>

        <% if (showPassword && showSso) { %>
        <div class="sso-divider"><span>eller</span></div>
        <% } %>

        <% if (showSso) { %>
        <a href="${pageContext.request.contextPath}/saml/login" class="sso-btn">
            <svg viewBox="0 0 21 21" xmlns="http://www.w3.org/2000/svg">
                <rect x="1" y="1" width="9" height="9" fill="#f25022"/>
                <rect x="11" y="1" width="9" height="9" fill="#7fba00"/>
                <rect x="1" y="11" width="9" height="9" fill="#00a4ef"/>
                <rect x="11" y="11" width="9" height="9" fill="#ffb900"/>
            </svg>
            Logga in med Microsoft
        </a>
        <% } %>

        <% if (showPassword) { %>
        <div class="login-footer">
            Har du inget konto? <a href="${pageContext.request.contextPath}/register">Registrera dig</a>
        </div>
        <% } %>
    </div>
    <div style="text-align:center;margin-top:16px;font-size:0.75em;color:#aaa;">v<%= AppVersion.VERSION %></div>
</body>
</html>
