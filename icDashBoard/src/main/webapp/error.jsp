<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Fel - InfoCaption Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Inter', -apple-system, sans-serif; background: #f8f9fc; display: flex; align-items: center; justify-content: center; min-height: 100vh; }
        .error-card { background: #fff; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); padding: 48px; text-align: center; max-width: 480px; }
        .error-icon { font-size: 3em; margin-bottom: 16px; }
        .error-code { font-size: 2em; font-weight: 700; color: #323232; margin-bottom: 8px; }
        .error-msg { color: #6c757d; font-size: 0.95em; margin-bottom: 24px; line-height: 1.5; }
        .error-btn { display: inline-block; background: #5458F2; color: #fff; text-decoration: none; padding: 10px 24px; border-radius: 8px; font-weight: 600; font-size: 0.9em; }
        .error-btn:hover { background: #4347d9; }
    </style>
</head>
<body>
    <div class="error-card">
        <%
            int code = 500;
            try { code = response.getStatus(); } catch (Exception ignored) {}
            if (code == 0) code = 500;
            String icon = "&#9888;&#65039;";
            String title = "Serverfel";
            String msg = "Ett ov&#228;ntat fel intr&#228;ffade. F&#246;rs&#246;k igen senare.";
            if (code == 404) { icon = "&#128269;"; title = "Sidan hittades inte"; msg = "Sidan du s&#246;ker finns inte eller har flyttats."; }
            else if (code == 403) { icon = "&#128274;"; title = "&#197;tkomst nekad"; msg = "Du har inte beh&#246;righet att visa denna sida."; }
            else if (code == 401) { icon = "&#128274;"; title = "Ej inloggad"; msg = "Du m&#229;ste logga in f&#246;r att se denna sida."; }
        %>
        <div class="error-icon"><%= icon %></div>
        <div class="error-code"><%= code %> — <%= title %></div>
        <div class="error-msg"><%= msg %></div>
        <a href="<%= request.getContextPath() %>/dashboard.jsp" class="error-btn">&#8592; Till Dashboard</a>
    </div>
</body>
</html>
