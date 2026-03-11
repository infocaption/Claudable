<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.util.Esc" %>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registrera - InfoCaption Dashboard</title>
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

        .register-card {
            background: var(--ic-bg-white);
            border-radius: var(--ic-radius-xl);
            box-shadow: var(--ic-shadow-xl);
            padding: 40px;
            width: 100%;
            max-width: 460px;
            text-align: center;
        }

        .register-logo {
            width: 60px;
            height: 60px;
            margin-bottom: 16px;
            filter: drop-shadow(0 8px 24px rgba(84, 88, 242, 0.3));
        }

        .register-title {
            font-size: 1.4em;
            font-weight: 700;
            color: var(--ic-text-dark);
            margin-bottom: 6px;
        }

        .register-subtitle {
            font-size: 0.9em;
            color: var(--ic-text-muted);
            margin-bottom: 28px;
        }

        .form-group {
            margin-bottom: 16px;
            text-align: left;
        }

        .register-card .ic-input {
            padding: 12px 14px;
        }

        .register-btn {
            width: 100%;
            padding: 14px;
            margin-top: 8px;
        }

        .register-footer {
            margin-top: 24px;
            font-size: 0.85em;
            color: var(--ic-text-muted);
        }

        .register-footer a {
            color: var(--ic-primary);
            text-decoration: none;
            font-weight: 600;
        }

        .register-footer a:hover {
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

        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
        }

        @media (max-width: 500px) {
            .form-row {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
    <div class="register-card">
        <img src="${pageContext.request.contextPath}/assets/logga.png" alt="InfoCaption" class="register-logo">
        <h1 class="register-title">Skapa konto</h1>
        <p class="register-subtitle">Fyll i formuläret nedan för att skapa ett konto</p>

        <% if (request.getAttribute("error") != null) { %>
            <div class="error-message"><%= Esc.h((String) request.getAttribute("error")) %></div>
        <% } %>

        <form method="post" action="${pageContext.request.contextPath}/register">
            <div class="form-group">
                <label class="ic-label" for="fullName">Fullständigt namn</label>
                <input type="text" id="fullName" name="fullName" class="ic-input"
                       placeholder="Förnamn Efternamn" required
                       value="<%= Esc.h((String) request.getAttribute("fullName")) %>">
            </div>

            <div class="form-row">
                <div class="form-group">
                    <label class="ic-label" for="username">Användarnamn</label>
                    <input type="text" id="username" name="username" class="ic-input"
                           placeholder="Minst 3 tecken" required minlength="3" maxlength="50"
                           value="<%= Esc.h((String) request.getAttribute("username")) %>">
                </div>
                <div class="form-group">
                    <label class="ic-label" for="email">E-post</label>
                    <input type="email" id="email" name="email" class="ic-input"
                           placeholder="namn@exempel.se" required
                           value="<%= Esc.h((String) request.getAttribute("email")) %>">
                </div>
            </div>

            <div class="form-row">
                <div class="form-group">
                    <label class="ic-label" for="password">Lösenord</label>
                    <input type="password" id="password" name="password" class="ic-input"
                           placeholder="Minst 6 tecken" required minlength="6">
                </div>
                <div class="form-group">
                    <label class="ic-label" for="confirmPassword">Bekräfta lösenord</label>
                    <input type="password" id="confirmPassword" name="confirmPassword" class="ic-input"
                           placeholder="Upprepa lösenord" required minlength="6">
                </div>
            </div>

            <button type="submit" class="ic-btn ic-btn-primary ic-btn-lg register-btn">
                Skapa konto
            </button>
        </form>

        <div class="register-footer">
            Har du redan ett konto? <a href="${pageContext.request.contextPath}/login">Logga in</a>
        </div>
    </div>
</body>
</html>
