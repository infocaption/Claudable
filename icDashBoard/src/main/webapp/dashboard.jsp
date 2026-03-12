<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.infocaption.dashboard.model.User" %>
<%@ page import="com.infocaption.dashboard.util.AppVersion" %>
<%
    User user = (User) session.getAttribute("user");
    String ctxPath = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="<%= session.getAttribute("csrfToken") != null ? session.getAttribute("csrfToken") : "" %>">
    <title>InfoCaption Dashboard</title>
    <script nonce="<%= request.getAttribute("cspNonce") %>">
    (function(){var t=document.querySelector('meta[name="csrf-token"]'),v=t?t.content:'',o=window.fetch;
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
    <style>
        :root {
            /* InfoCaption Brand Colors */
            --ic-primary: #5458F2;
            --ic-primary-hover: #4347d9;
            --ic-primary-light: #5e61f3;
            --ic-secondary: #D4D5FC;
            --ic-tertiary: #BEC0F9;
            --ic-accent: #FFE496;

            /* Text colors */
            --ic-text-dark: #323232;
            --ic-text-light: #ffffff;
            --ic-text-muted: #6c757d;

            /* Backgrounds */
            --ic-bg-white: #ffffff;
            --ic-bg-light: #f8f9fc;
            --ic-bg-sidebar: #1e1e3f;

            /* Status colors */
            --ic-success: #10b981;
            --ic-success-light: #d1fae5;
            --ic-danger: #ef4444;
            --ic-danger-light: #fee2e2;
            --ic-warning: #f59e0b;
            --ic-warning-light: #fef3c7;

            /* Spacing */
            --ic-radius-sm: 6px;
            --ic-radius-md: 10px;
            --ic-radius-lg: 16px;
            --ic-radius-xl: 24px;
        }

        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--ic-bg-light);
            min-height: 100vh;
            color: var(--ic-text-dark);
            font-size: 14px;
        }

        /* Main Dashboard Layout */
        .dashboard-container {
            display: flex;
            min-height: 100vh;
        }

        /* Sidebar Navigation */
        .sidebar {
            width: 260px;
            background: var(--ic-bg-white);
            color: var(--ic-text-dark);
            display: flex;
            flex-direction: column;
            flex-shrink: 0;
            position: fixed;
            top: 0;
            left: 0;
            height: 100vh;
            z-index: 100;
            transition: transform 0.3s ease;
            border-right: 1px solid #e5e7eb;
            box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);
        }

        .sidebar.collapsed {
            transform: translateX(-260px);
        }

        .sidebar-header {
            padding: 18px 20px;
            background: var(--ic-bg-white);
            border-bottom: 1px solid #e5e7eb;
        }

        .sidebar-logo {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .logo-img {
            width: 38px;
            height: 38px;
            border-radius: var(--ic-radius-sm);
        }

        .logo-text {
            font-size: 1.15em;
            font-weight: 700;
            letter-spacing: -0.5px;
            color: var(--ic-primary);
        }

        .logo-subtitle {
            font-size: 0.7em;
            color: var(--ic-text-muted);
            font-weight: 400;
        }

        /* Navigation */
        .sidebar-nav {
            flex: 1;
            overflow-y: auto;
            padding: 15px 0;
        }

        .nav-section {
            margin-bottom: 20px;
        }

        .nav-section-title {
            font-size: 0.65em;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1.2px;
            color: var(--ic-text-muted);
            padding: 0 20px;
            margin-bottom: 8px;
        }

        .nav-item {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 11px 20px;
            color: var(--ic-text-dark);
            text-decoration: none;
            cursor: pointer;
            transition: all 0.2s ease;
            border-left: 3px solid transparent;
            font-size: 0.9em;
        }

        .nav-item:hover {
            background: var(--ic-secondary);
            color: var(--ic-primary);
        }

        .nav-item.active {
            background: var(--ic-secondary);
            color: var(--ic-primary);
            border-left-color: var(--ic-primary);
            font-weight: 600;
        }

        .nav-icon {
            font-size: 1.15em;
            width: 24px;
            text-align: center;
        }

        .nav-text {
            font-weight: 500;
        }

        .nav-badge {
            margin-left: auto;
            background: var(--ic-secondary);
            color: var(--ic-primary);
            font-size: 0.7em;
            padding: 3px 8px;
            border-radius: 10px;
            font-weight: 600;
        }

        .nav-badge.new {
            background: var(--ic-accent);
            color: var(--ic-text-dark);
        }

        /* Sidebar footer */
        .sidebar-footer {
            padding: 15px 20px;
            border-top: 1px solid #e5e7eb;
        }

        .sidebar-footer-text {
            font-size: 0.7em;
            color: var(--ic-text-muted);
            text-align: center;
            line-height: 1.5;
        }

        /* Main Content Area */
        .main-content {
            flex: 1;
            margin-left: 260px;
            transition: margin-left 0.3s ease;
            display: flex;
            flex-direction: column;
            min-height: 100vh;
        }

        .main-content.expanded {
            margin-left: 0;
        }

        /* Top Bar */
        .topbar {
            background: var(--ic-bg-white);
            padding: 12px 25px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
            position: sticky;
            top: 0;
            z-index: 50;
            border-bottom: 1px solid #eee;
        }

        .topbar-left {
            display: flex;
            align-items: center;
            gap: 15px;
        }

        .menu-toggle {
            background: none;
            border: none;
            cursor: pointer;
            padding: 8px;
            border-radius: var(--ic-radius-sm);
            font-size: 1.2em;
            color: var(--ic-text-muted);
            transition: all 0.2s;
        }

        .menu-toggle:hover {
            background: var(--ic-secondary);
            color: var(--ic-primary);
        }

        /* Date and Week display */
        .date-display {
            display: flex;
            flex-direction: column;
            line-height: 1.3;
        }

        .date-display .date {
            font-weight: 600;
            color: var(--ic-text-dark);
            font-size: 0.95em;
        }

        .date-display .week {
            font-size: 0.8em;
            color: var(--ic-text-muted);
        }

        .breadcrumb {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 0.9em;
            color: var(--ic-text-muted);
            margin-left: 20px;
            padding-left: 20px;
            border-left: 1px solid #ddd;
        }

        .breadcrumb-item {
            color: var(--ic-primary);
            font-weight: 600;
        }

        .topbar-right {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .user-info {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 0.85em;
        }

        .user-avatar {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background: var(--ic-secondary);
            color: var(--ic-primary);
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            font-size: 0.85em;
        }

        .user-name {
            font-weight: 600;
            color: var(--ic-text-dark);
        }

        .logout-btn {
            background: none;
            border: 1px solid #e5e7eb;
            padding: 6px 14px;
            border-radius: var(--ic-radius-sm);
            font-size: 0.8em;
            color: var(--ic-text-muted);
            cursor: pointer;
            transition: all 0.2s;
            font-family: inherit;
            text-decoration: none;
        }

        .logout-btn:hover {
            background: var(--ic-danger-light);
            color: var(--ic-danger);
            border-color: var(--ic-danger);
        }

        /* User Dropdown */
        .user-dropdown-wrapper {
            position: relative;
        }

        .user-dropdown-trigger {
            display: flex;
            align-items: center;
            gap: 10px;
            cursor: pointer;
            padding: 4px 8px;
            border-radius: var(--ic-radius-sm);
            transition: background 0.2s;
            user-select: none;
        }

        .user-dropdown-trigger:hover {
            background: var(--ic-bg-light);
        }

        .user-dropdown-arrow {
            font-size: 0.55em;
            color: var(--ic-text-muted);
            transition: transform 0.2s;
        }

        .user-dropdown-trigger.open .user-dropdown-arrow {
            transform: rotate(180deg);
        }

        .user-dropdown {
            position: absolute;
            top: calc(100% + 6px);
            right: 0;
            background: var(--ic-bg-white);
            border: 1px solid #e5e7eb;
            border-radius: var(--ic-radius-md);
            box-shadow: 0 8px 30px rgba(0,0,0,0.12);
            min-width: 220px;
            z-index: 200;
            display: none;
            overflow: hidden;
        }

        .user-dropdown.open {
            display: block;
        }

        .user-dropdown-header {
            padding: 14px 16px;
            border-bottom: 1px solid #e5e7eb;
        }

        .user-dropdown-name {
            font-weight: 600;
            font-size: 0.9em;
            color: var(--ic-text-dark);
        }

        .user-dropdown-email {
            font-size: 0.78em;
            color: var(--ic-text-muted);
            margin-top: 2px;
        }

        .user-dropdown-item {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 10px 16px;
            color: var(--ic-text-dark);
            text-decoration: none;
            cursor: pointer;
            transition: background 0.15s;
            font-size: 0.85em;
            border: none;
            background: none;
            width: 100%;
            font-family: inherit;
        }

        .user-dropdown-item:hover {
            background: var(--ic-bg-light);
            color: var(--ic-primary);
        }

        .user-dropdown-item.danger:hover {
            background: var(--ic-danger-light);
            color: var(--ic-danger);
        }

        .user-dropdown-divider {
            height: 1px;
            background: #e5e7eb;
            margin: 0;
        }

        /* ═══════════ Widget Bar ═══════════ */
        .widget-bar {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 6px 16px;
            background: #f0f1f5;
            border-bottom: 1px solid #e5e7eb;
            min-height: 44px;
            overflow-x: auto;
            flex-shrink: 0;
            position: sticky;
            top: 55px;
            z-index: 49;
        }

        .widget-bar.bottom {
            border-bottom: none;
            border-top: 1px solid #e5e7eb;
            position: fixed;
            top: auto;
            bottom: 0;
            left: 260px;
            right: 0;
            z-index: 50;
            background: #f0f1f5;
        }

        .sidebar.collapsed ~ .main-content .widget-bar.bottom {
            left: 0;
        }

        .widget-bar-content {
            display: flex;
            align-items: center;
            gap: 8px;
            flex: 1;
            overflow-x: auto;
        }

        .widget-bar-content::-webkit-scrollbar {
            height: 3px;
        }

        .widget-bar-content::-webkit-scrollbar-thumb {
            background: #ccc;
            border-radius: 3px;
        }

        .widget-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 5px 14px;
            background: var(--ic-bg-white);
            border-radius: 8px;
            font-size: 12.5px;
            font-weight: 500;
            color: var(--ic-text-dark);
            white-space: nowrap;
            box-shadow: 0 1px 3px rgba(0,0,0,0.06);
            border: 1px solid #eee;
            max-width: 240px;
            height: 32px;
            transition: box-shadow 0.15s;
            cursor: default;
        }

        .widget-chip:hover {
            box-shadow: 0 2px 6px rgba(0,0,0,0.1);
        }

        .widget-chip .widget-icon {
            font-size: 14px;
            flex-shrink: 0;
        }

        .widget-chip .widget-value {
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .widget-chip.widget-ok { border-left: 3px solid var(--ic-success); }
        .widget-chip.widget-warn { border-left: 3px solid var(--ic-warning); }
        .widget-chip.widget-danger { border-left: 3px solid var(--ic-danger); }

        .widget-bar-settings {
            background: none;
            border: 1px solid #ddd;
            border-radius: 6px;
            padding: 4px 8px;
            font-size: 14px;
            cursor: pointer;
            color: var(--ic-text-muted);
            transition: all 0.2s;
            flex-shrink: 0;
            height: 30px;
            display: flex;
            align-items: center;
        }

        .widget-bar-settings:hover {
            background: var(--ic-secondary);
            color: var(--ic-primary);
            border-color: var(--ic-primary);
        }

        /* Widget Settings Modal */
        .widget-settings-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.4);
            z-index: 3000;
            display: none;
            align-items: center;
            justify-content: center;
        }

        .widget-settings-overlay.show {
            display: flex;
        }

        .widget-settings-modal {
            background: var(--ic-bg-white);
            border-radius: var(--ic-radius-lg);
            padding: 28px;
            width: 420px;
            max-width: 90vw;
            max-height: 85vh;
            overflow-y: auto;
            box-shadow: 0 20px 60px rgba(0,0,0,0.2);
        }

        .widget-settings-modal h3 {
            font-size: 1.15em;
            color: var(--ic-text-dark);
            margin-bottom: 6px;
        }

        .widget-settings-modal .modal-subtitle {
            font-size: 0.82em;
            color: var(--ic-text-muted);
            margin-bottom: 20px;
        }

        .widget-option {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 10px 12px;
            border-radius: var(--ic-radius-sm);
            transition: background 0.15s;
            cursor: pointer;
        }

        .widget-option:hover {
            background: var(--ic-bg-light);
        }

        .widget-option input[type="checkbox"] {
            width: 18px;
            height: 18px;
            accent-color: var(--ic-primary);
            cursor: pointer;
        }

        .widget-option-icon {
            font-size: 1.2em;
            width: 28px;
            text-align: center;
        }

        .widget-option-info {
            flex: 1;
        }

        .widget-option-name {
            font-weight: 600;
            font-size: 0.9em;
            color: var(--ic-text-dark);
        }

        .widget-option-desc {
            font-size: 0.75em;
            color: var(--ic-text-muted);
            margin-top: 2px;
        }

        .widget-option .drag-handle {
            cursor: grab;
            color: #ccc;
            font-size: 1.1em;
            padding: 0 4px;
            user-select: none;
        }
        .widget-option .drag-handle:hover { color: #999; }
        .widget-option.dragging { opacity: 0.4; background: var(--ic-secondary); }
        .widget-option.drag-over { border-top: 2px solid var(--ic-primary); }

        .nav-item.dragging { opacity: 0.4; }
        .nav-item.drag-over { border-top: 2px solid var(--ic-primary); }

        .widget-settings-section {
            margin-top: 20px;
            padding-top: 16px;
            border-top: 1px solid #eee;
        }

        .widget-settings-section label {
            font-weight: 600;
            font-size: 0.85em;
            color: var(--ic-text-dark);
            display: block;
            margin-bottom: 10px;
        }

        .widget-position-options {
            display: flex;
            gap: 10px;
        }

        .widget-position-btn {
            flex: 1;
            padding: 10px;
            border: 2px solid #e5e7eb;
            border-radius: var(--ic-radius-sm);
            background: var(--ic-bg-white);
            cursor: pointer;
            text-align: center;
            font-size: 0.85em;
            font-weight: 500;
            color: var(--ic-text-muted);
            transition: all 0.2s;
            font-family: inherit;
        }

        .widget-position-btn.active {
            border-color: var(--ic-primary);
            background: var(--ic-secondary);
            color: var(--ic-primary);
        }

        .widget-position-btn:hover {
            border-color: var(--ic-primary);
        }

        .widget-settings-actions {
            display: flex;
            gap: 10px;
            margin-top: 22px;
            justify-content: flex-end;
        }

        .widget-settings-actions button {
            padding: 8px 20px;
            border-radius: var(--ic-radius-sm);
            font-size: 0.85em;
            font-weight: 600;
            cursor: pointer;
            font-family: inherit;
            border: 1px solid #e5e7eb;
            transition: all 0.2s;
        }

        .widget-btn-cancel {
            background: var(--ic-bg-white);
            color: var(--ic-text-muted);
        }

        .widget-btn-cancel:hover {
            background: var(--ic-bg-light);
        }

        .widget-btn-save {
            background: var(--ic-primary);
            color: white;
            border-color: var(--ic-primary) !important;
        }

        .widget-btn-save:hover {
            background: var(--ic-primary-hover);
        }

        /* Quick links widget extras */
        .widget-chip a {
            color: var(--ic-primary);
            text-decoration: none;
            font-weight: 500;
        }

        .widget-chip a:hover {
            text-decoration: underline;
        }

        /* Bottom bar adjustments for module container */
        .module-container.has-bottom-bar {
            padding-bottom: 46px;
        }

        /* ═══════════ Module Doc Popup ═══════════ */
        .module-doc-btn {
            position: fixed;
            bottom: 20px;
            right: 20px;
            width: 42px;
            height: 42px;
            border-radius: 50%;
            background: var(--ic-primary);
            color: white;
            border: none;
            font-size: 18px;
            cursor: pointer;
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 500;
            box-shadow: 0 4px 14px rgba(84, 88, 242, 0.35);
            transition: all 0.25s ease;
            opacity: 0.7;
        }

        .module-doc-btn:hover {
            opacity: 1;
            transform: scale(1.1);
            box-shadow: 0 6px 20px rgba(84, 88, 242, 0.45);
        }

        .module-doc-btn.visible {
            display: flex;
        }

        .module-doc-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.4);
            z-index: 3500;
            display: none;
            align-items: flex-start;
            justify-content: flex-end;
            padding: 20px;
        }

        .module-doc-overlay.show {
            display: flex;
        }

        .module-doc-panel {
            background: var(--ic-bg-white);
            border-radius: var(--ic-radius-lg);
            width: 500px;
            max-width: 90vw;
            max-height: 85vh;
            overflow: hidden;
            box-shadow: 0 20px 60px rgba(0,0,0,0.2);
            display: flex;
            flex-direction: column;
            animation: docPanelSlide 0.25s ease;
        }

        @keyframes docPanelSlide {
            from { opacity: 0; transform: translateX(30px); }
            to { opacity: 1; transform: translateX(0); }
        }

        .module-doc-header {
            padding: 20px 24px;
            background: linear-gradient(135deg, var(--ic-primary) 0%, #764ba2 100%);
            color: white;
            display: flex;
            align-items: center;
            gap: 14px;
            flex-shrink: 0;
        }

        .module-doc-header-icon {
            width: 42px;
            height: 42px;
            background: rgba(255,255,255,0.2);
            border-radius: var(--ic-radius-md);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.4em;
            flex-shrink: 0;
        }

        .module-doc-header-text {
            flex: 1;
            min-width: 0;
        }

        .module-doc-header-title {
            font-size: 1.15em;
            font-weight: 700;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .module-doc-header-subtitle {
            font-size: 0.78em;
            opacity: 0.85;
            margin-top: 2px;
        }

        .module-doc-close {
            background: rgba(255,255,255,0.2);
            border: none;
            color: white;
            width: 32px;
            height: 32px;
            border-radius: 50%;
            cursor: pointer;
            font-size: 1.1em;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: background 0.2s;
            flex-shrink: 0;
        }

        .module-doc-close:hover {
            background: rgba(255,255,255,0.35);
        }

        .module-doc-body {
            padding: 24px;
            overflow-y: auto;
            flex: 1;
        }

        .module-doc-section {
            margin-bottom: 20px;
        }

        .module-doc-section:last-child {
            margin-bottom: 0;
        }

        .module-doc-section-title {
            font-size: 0.72em;
            font-weight: 700;
            color: var(--ic-text-muted);
            text-transform: uppercase;
            letter-spacing: 0.8px;
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .module-doc-description {
            font-size: 0.92em;
            color: var(--ic-text-dark);
            line-height: 1.7;
        }

        .module-doc-spec {
            font-size: 0.85em;
            color: var(--ic-text-dark);
            line-height: 1.7;
            white-space: pre-wrap;
            background: var(--ic-bg-light);
            padding: 16px;
            border-radius: var(--ic-radius-md);
            border: 1px solid #e5e7eb;
            max-height: 400px;
            overflow-y: auto;
        }

        .module-doc-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }

        .module-doc-meta-item {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            padding: 4px 10px;
            background: var(--ic-bg-light);
            border-radius: 20px;
            font-size: 0.78em;
            color: var(--ic-text-muted);
            font-weight: 500;
        }

        .module-doc-no-spec {
            font-size: 0.85em;
            color: var(--ic-text-muted);
            font-style: italic;
            padding: 12px;
            text-align: center;
            background: var(--ic-bg-light);
            border-radius: var(--ic-radius-md);
        }

        .module-doc-footer {
            padding: 14px 24px;
            border-top: 1px solid #e5e7eb;
            background: var(--ic-bg-light);
            display: flex;
            gap: 10px;
            justify-content: flex-end;
            flex-shrink: 0;
        }

        .module-doc-footer button {
            padding: 7px 16px;
            border-radius: var(--ic-radius-sm);
            font-size: 0.82em;
            font-weight: 600;
            cursor: pointer;
            font-family: inherit;
            border: 1px solid #e5e7eb;
            transition: all 0.2s;
        }

        .doc-btn-close {
            background: var(--ic-bg-white);
            color: var(--ic-text-muted);
        }

        .doc-btn-close:hover {
            background: #eee;
        }

        @media (max-width: 900px) {
            .module-doc-panel {
                width: 100%;
                max-width: 100%;
                max-height: 90vh;
            }

            .module-doc-overlay {
                align-items: flex-end;
                padding: 0;
            }

            .module-doc-panel {
                border-radius: var(--ic-radius-lg) var(--ic-radius-lg) 0 0;
            }

            .module-doc-btn {
                bottom: 14px;
                right: 14px;
                width: 38px;
                height: 38px;
                font-size: 16px;
            }
        }

        /* Module Frame Container */
        .module-container {
            flex: 1;
            padding: 0;
            display: flex;
            flex-direction: column;
        }

        .module-frame {
            flex: 1;
            border: none;
            width: 100%;
            min-height: calc(100vh - 55px - 44px);
            background: var(--ic-bg-white);
        }

        .module-frame.no-widget-bar {
            min-height: calc(100vh - 55px);
        }

        /* Welcome Screen */
        .welcome-screen {
            flex: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 40px;
            text-align: center;
            background: linear-gradient(180deg, var(--ic-bg-white) 0%, var(--ic-bg-light) 100%);
        }

        .welcome-logo-img {
            width: 80px;
            height: 80px;
            margin-bottom: 25px;
            filter: drop-shadow(0 10px 30px rgba(84, 88, 242, 0.3));
        }

        .welcome-title {
            font-size: 1.8em;
            font-weight: 700;
            color: var(--ic-text-dark);
            margin-bottom: 12px;
            letter-spacing: -0.5px;
        }

        .welcome-subtitle {
            font-size: 1em;
            color: var(--ic-text-muted);
            max-width: 450px;
            margin-bottom: 40px;
            line-height: 1.6;
        }

        .module-cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
            gap: 20px;
            max-width: 900px;
            width: 100%;
        }

        .module-card {
            background: var(--ic-bg-white);
            border-radius: var(--ic-radius-lg);
            padding: 22px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
            cursor: pointer;
            transition: all 0.25s ease;
            border: 2px solid transparent;
            text-align: left;
            position: relative;
            overflow: hidden;
        }

        .module-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 4px;
            background: var(--ic-primary);
            transform: scaleX(0);
            transition: transform 0.25s ease;
        }

        .module-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 12px 24px rgba(84, 88, 242, 0.15);
            border-color: var(--ic-secondary);
        }

        .module-card:hover::before {
            transform: scaleX(1);
        }

        .module-card-icon {
            width: 46px;
            height: 46px;
            background: var(--ic-secondary);
            border-radius: var(--ic-radius-md);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.4em;
            margin-bottom: 14px;
        }

        .module-card-title {
            font-size: 1.05em;
            font-weight: 600;
            color: var(--ic-text-dark);
            margin-bottom: 6px;
        }

        .module-card-description {
            font-size: 0.82em;
            color: var(--ic-text-muted);
            line-height: 1.5;
        }

        .module-card-badge {
            display: inline-block;
            margin-top: 10px;
            font-size: 0.68em;
            padding: 3px 8px;
            background: var(--ic-accent);
            color: var(--ic-text-dark);
            border-radius: 20px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        /* Loading State */
        .loading-overlay {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(255,255,255,0.95);
            display: none;
            align-items: center;
            justify-content: center;
            flex-direction: column;
            z-index: 200;
        }

        .loading-overlay.show {
            display: flex;
        }

        .loading-spinner {
            width: 45px;
            height: 45px;
            border: 3px solid var(--ic-secondary);
            border-top: 3px solid var(--ic-primary);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        .loading-text {
            margin-top: 15px;
            color: var(--ic-text-muted);
            font-size: 0.9em;
            font-weight: 500;
        }

        /* Toast notification */
        .toast {
            position: fixed;
            top: 25px;
            right: 25px;
            background: var(--ic-primary);
            color: white;
            padding: 14px 22px;
            border-radius: var(--ic-radius-md);
            box-shadow: 0 8px 25px rgba(84, 88, 242, 0.35);
            display: flex;
            align-items: center;
            gap: 12px;
            font-size: 0.9em;
            font-weight: 500;
            z-index: 2000;
            transform: translateX(400px);
            opacity: 0;
            transition: transform 0.35s ease, opacity 0.35s ease;
        }

        .toast.show {
            transform: translateX(0);
            opacity: 1;
        }

        .toast.success {
            background: var(--ic-success);
        }

        .toast.error {
            background: var(--ic-danger);
        }

        .toast-icon {
            font-size: 1.2em;
        }

        /* Responsive */
        @media (max-width: 900px) {
            .sidebar {
                transform: translateX(-260px);
            }

            .sidebar.open {
                transform: translateX(0);
            }

            .main-content {
                margin-left: 0;
            }

            .module-cards {
                grid-template-columns: 1fr;
            }

            .welcome-title {
                font-size: 1.5em;
            }

            .breadcrumb {
                display: none;
            }

            .user-name {
                display: none;
            }

            .widget-bar.bottom {
                left: 0;
            }
        }

        /* Sidebar mobile overlay */
        .sidebar-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0,0,0,0.5);
            z-index: 99;
            opacity: 0;
            visibility: hidden;
            transition: all 0.3s;
        }

        .sidebar-overlay.show {
            opacity: 1;
            visibility: visible;
        }
    </style>
</head>
<body>
    <div class="dashboard-container">
        <!-- Mobile overlay -->
        <div class="sidebar-overlay" id="sidebarOverlay" onclick="toggleSidebar()"></div>

        <!-- Sidebar Navigation -->
        <aside class="sidebar" id="sidebar">
            <div class="sidebar-header">
                <div class="sidebar-logo">
                    <img src="<%= ctxPath %>/assets/logga.png" alt="InfoCaption" class="logo-img">
                    <div>
                        <div class="logo-text">InfoCaption</div>
                        <div class="logo-subtitle">Dashboard</div>
                    </div>
                </div>
            </div>

            <nav class="sidebar-nav">
                <div class="nav-section">
                    <div class="nav-section-title">Översikt</div>
                    <div class="nav-item active" data-module="home" onclick="loadModule('home')">
                        <span class="nav-icon">&#127968;</span>
                        <span class="nav-text">Startsida</span>
                    </div>
                </div>

                <div class="nav-section">
                    <div class="nav-section-title">Moduler</div>
                    <div id="moduleNav">
                        <!-- Modules will be loaded here dynamically -->
                    </div>
                </div>

                <div class="nav-section">
                    <div class="nav-section-title">Hantera</div>
                    <div class="nav-item" data-manage="create-module" onclick="loadManagePage('create-module')">
                        <span class="nav-icon">&#10133;</span>
                        <span class="nav-text">Skapa modul</span>
                    </div>
                    <div class="nav-item" data-manage="manage-modules" onclick="loadManagePage('manage-modules')">
                        <span class="nav-icon">&#9881;</span>
                        <span class="nav-text">Mina moduler</span>
                    </div>
                    <div class="nav-item" data-manage="manage-groups" onclick="loadManagePage('manage-groups')">
                        <span class="nav-icon">&#128101;</span>
                        <span class="nav-text">Grupper</span>
                    </div>
                    <div class="nav-item" data-manage="manage-users" onclick="loadManagePage('manage-users')">
                        <span class="nav-icon">&#128100;</span>
                        <span class="nav-text">Användare</span>
                    </div>
                    <% if (user.isAdmin()) { %>
                    <div class="nav-item" data-manage="admin" onclick="loadManagePage('admin')">
                        <span class="nav-icon">&#128272;</span>
                        <span class="nav-text">Admin</span>
                        <span class="nav-badge" style="background:#fee2e2;color:#ef4444;font-size:0.65em">Admin</span>
                    </div>
                    <% } %>
                </div>

                <div class="nav-section">
                    <div class="nav-section-title">Hjälp</div>
                    <div id="docsNav">
                        <!-- Docs module loaded dynamically -->
                    </div>
                    <a class="nav-item" href="https://vips.infocaption.com" target="_blank">
                        <span class="nav-icon">&#127760;</span>
                        <span class="nav-text">VIPS Intranät</span>
                        <span class="nav-badge">&#8599;</span>
                    </a>
                </div>
            </nav>

            <div class="sidebar-footer">
                <div class="sidebar-footer-text">v<%= AppVersion.VERSION %></div>
            </div>
        </aside>

        <!-- Main Content -->
        <main class="main-content" id="mainContent">
            <!-- Top Bar -->
            <div class="topbar">
                <div class="topbar-left">
                    <button class="menu-toggle" onclick="toggleSidebar()" title="Visa/dölj meny">&#9776;</button>
                    <div class="date-display">
                        <span class="date" id="currentDate"></span>
                        <span class="week" id="currentWeek"></span>
                    </div>
                    <div class="breadcrumb">
                        <span>Dashboard</span>
                        <span>/</span>
                        <span class="breadcrumb-item" id="currentModule">Startsida</span>
                    </div>
                </div>
                <div class="topbar-right">
                    <div class="user-dropdown-wrapper">
                        <div class="user-dropdown-trigger" id="userDropdownTrigger" onclick="toggleUserDropdown()">
                            <div class="user-avatar" id="topbarAvatar"><%
                                if (user.getProfilePictureUrl() != null && !user.getProfilePictureUrl().isEmpty()) {
                            %><img src="<%= request.getContextPath() + "/" + user.getProfilePictureUrl() %>" alt="" style="width:100%;height:100%;object-fit:cover;border-radius:50%;" id="topbarAvatarImg"><%
                                } else {
                            %><span id="topbarAvatarInitial"><%= user.getFullName().substring(0, 1).toUpperCase() %></span><%
                                }
                            %></div>
                            <span class="user-name" id="topbarUserName"><%= user.getFullName() %></span>
                            <span class="user-dropdown-arrow">&#9660;</span>
                        </div>
                        <div class="user-dropdown" id="userDropdown">
                            <div class="user-dropdown-header">
                                <div class="user-dropdown-name" id="dropdownUserName"><%= user.getFullName() %></div>
                                <div class="user-dropdown-email"><%= user.getEmail() %></div>
                            </div>
                            <div class="user-dropdown-item" onclick="loadManagePage('settings'); closeUserDropdown();">
                                &#9881; Inställningar
                            </div>
                            <div class="user-dropdown-divider"></div>
                            <a href="<%= ctxPath %>/logout" class="user-dropdown-item danger">
                                &#128682; Logga ut
                            </a>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Widget Bar -->
            <div class="widget-bar" id="widgetBar" style="display:none;">
                <div class="widget-bar-content" id="widgetBarContent">
                    <!-- Widgets rendered by JS -->
                </div>
                <button class="widget-bar-settings" onclick="openWidgetSettings()" title="Widgetinställningar">&#9881;</button>
            </div>

            <!-- Widget Settings Modal -->
            <div class="widget-settings-overlay" id="widgetSettingsOverlay" onclick="if(event.target===this)closeWidgetSettings();">
                <div class="widget-settings-modal">
                    <h3>&#9881; Widgetinställningar</h3>
                    <p class="modal-subtitle">Välj vilka widgets som visas i widgetbaren.</p>
                    <div id="widgetOptionsList"></div>
                    <div class="widget-settings-section">
                        <label>Placering</label>
                        <div class="widget-position-options">
                            <button class="widget-position-btn active" data-pos="top" onclick="selectWidgetPosition('top')">&#11014;&#65039; Topp</button>
                            <button class="widget-position-btn" data-pos="bottom" onclick="selectWidgetPosition('bottom')">&#11015;&#65039; Bott</button>
                        </div>
                    </div>
                    <div class="widget-settings-actions">
                        <button class="widget-btn-cancel" onclick="closeWidgetSettings()">Avbryt</button>
                        <button class="widget-btn-save" onclick="saveWidgetPrefs()">Spara</button>
                    </div>
                </div>
            </div>

            <!-- Module Container -->
            <div class="module-container" id="moduleContainer">
                <!-- Welcome screen or loaded module -->
                <div class="welcome-screen" id="welcomeScreen">
                    <img src="<%= ctxPath %>/assets/logga.png" alt="InfoCaption" class="welcome-logo-img">
                    <h1 class="welcome-title">Välkommen till InfoCaption Dashboard</h1>
                    <p class="welcome-subtitle">
                        Välj en modul från menyn för att komma igång, eller klicka på ett av korten nedan.
                    </p>
                    <div class="module-cards" id="moduleCards">
                        <!-- Module cards will be loaded here -->
                    </div>
                </div>

                <!-- Module iframe (hidden initially) -->
                <iframe class="module-frame" id="moduleFrame" style="display: none;"></iframe>
            </div>

            <!-- Loading overlay -->
            <div class="loading-overlay" id="loadingOverlay">
                <div class="loading-spinner"></div>
                <div class="loading-text">Laddar modul...</div>
            </div>
        </main>
    </div>

    <!-- Toast notification -->
    <div class="toast" id="toast">
        <span class="toast-icon">&#9989;</span>
        <span id="toastMessage">Åtgärd slutförd!</span>
    </div>

    <!-- Module Doc Floating Button -->
    <button class="module-doc-btn" id="moduleDocBtn" onclick="openModuleDoc()" title="Modulinformation">&#10068;</button>

    <!-- Module Doc Popup -->
    <div class="module-doc-overlay" id="moduleDocOverlay" onclick="if(event.target===this)closeModuleDoc();">
        <div class="module-doc-panel">
            <div class="module-doc-header">
                <div class="module-doc-header-icon" id="docHeaderIcon"></div>
                <div class="module-doc-header-text">
                    <div class="module-doc-header-title" id="docHeaderTitle"></div>
                    <div class="module-doc-header-subtitle" id="docHeaderSubtitle"></div>
                </div>
                <button class="module-doc-close" onclick="closeModuleDoc()" title="Stäng">&#10005;</button>
            </div>
            <div class="module-doc-body" id="docBody">
                <!-- Populated by JS -->
            </div>
            <div class="module-doc-footer">
                <button class="doc-btn-close" onclick="closeModuleDoc()">Stäng</button>
            </div>
        </div>
    </div>

    <script nonce="<%= request.getAttribute("cspNonce") %>">
        // Context path for URL resolution
        var CTX = '<%= ctxPath %>';

        // Module Registry - loaded from API
        const moduleRegistry = {
            modules: [],

            async load() {
                try {
                    const resp = await fetch(CTX + '/api/modules');
                    if (!resp.ok) throw new Error('API error: ' + resp.status);
                    this.modules = await resp.json();
                } catch (e) {
                    console.error('Failed to load modules:', e);
                    showToast('Kunde inte ladda moduler', 'error');
                    this.modules = [];
                }
            },

            getAll() {
                return this.modules;
            },

            getById(id) {
                return this.modules.find(function(m) { return m.id === id; });
            },

            getByCategory(category) {
                return this.modules.filter(function(m) { return m.category === category; });
            }
        };

        let currentModuleId = null;

        // ═══════════ Widget System ═══════════
        let availableWidgets = [];
        let activeWidgetIntervals = [];
        let tempWidgetPosition = 'top';

        const widgetRenderers = {
            date_week: {
                render(container) {
                    const now = new Date();
                    const opts = { day: 'numeric', month: 'short', year: 'numeric' };
                    const dateStr = now.toLocaleDateString('sv-SE', opts);
                    const startOfYear = new Date(now.getFullYear(), 0, 1);
                    const days = Math.floor((now - startOfYear) / 86400000);
                    const weekNum = Math.ceil((days + startOfYear.getDay() + 1) / 7);
                    container.innerHTML = '<span class="widget-value">' + dateStr + ' \u00b7 v.' + weekNum + '</span>';
                },
                interval: 60000
            },
            clock: {
                render(container) {
                    const now = new Date();
                    const h = String(now.getHours()).padStart(2, '0');
                    const m = String(now.getMinutes()).padStart(2, '0');
                    const s = String(now.getSeconds()).padStart(2, '0');
                    container.innerHTML = '<span class="widget-value">' + h + ':' + m + ':' + s + '</span>';
                },
                interval: 1000
            },
            server_status: {
                render(container) {
                    container.innerHTML = '<span class="widget-value">Laddar\u2026</span>';
                    fetch(CTX + '/api/servers/health')
                        .then(function(r) { return r.json(); })
                        .then(function(data) {
                            var ok = 0, warn = 0, down = 0;
                            data.forEach(function(s) {
                                if (s.severity === 'ok') ok++;
                                else if (s.severity === 'low' || s.severity === 'medium') warn++;
                                else down++;
                            });
                            var cls = down > 0 ? 'widget-danger' : (warn > 0 ? 'widget-warn' : 'widget-ok');
                            container.parentElement.className = 'widget-chip ' + cls;
                            container.innerHTML = '<span class="widget-value">' + ok + ' \u2713 \u00b7 ' + warn + ' \u26a0 \u00b7 ' + down + ' \u2717</span>';
                        })
                        .catch(function() {
                            container.innerHTML = '<span class="widget-value">Fel</span>';
                            container.parentElement.className = 'widget-chip widget-danger';
                        });
                },
                interval: 300000
            },
            cert_expiry: {
                render(container) {
                    container.innerHTML = '<span class="widget-value">Laddar\u2026</span>';
                    fetch(CTX + '/api/certificates')
                        .then(function(r) { return r.json(); })
                        .then(function(data) {
                            if (data.error) { container.innerHTML = '<span class="widget-value">-</span>'; return; }
                            var certs = data.certificates || data;
                            if (!Array.isArray(certs)) { container.innerHTML = '<span class="widget-value">-</span>'; return; }
                            var expired = 0, critical = 0;
                            certs.forEach(function(c) {
                                var d = c.daysLeft !== undefined ? c.daysLeft : c.days_left;
                                if (d <= 0) expired++;
                                else if (d <= 30) critical++;
                            });
                            if (expired === 0 && critical === 0) {
                                container.parentElement.className = 'widget-chip widget-ok';
                                container.innerHTML = '<span class="widget-value">Alla OK \u2713</span>';
                            } else {
                                var parts = [];
                                if (expired > 0) parts.push(expired + ' utg\u00e5ngna');
                                if (critical > 0) parts.push(critical + ' kritiska');
                                container.parentElement.className = 'widget-chip ' + (expired > 0 ? 'widget-danger' : 'widget-warn');
                                container.innerHTML = '<span class="widget-value">' + parts.join(' \u00b7 ') + '</span>';
                            }
                        })
                        .catch(function() {
                            container.innerHTML = '<span class="widget-value">Fel</span>';
                        });
                },
                interval: 600000
            },
            customer_count: {
                render(container) {
                    container.innerHTML = '<span class="widget-value">Laddar\u2026</span>';
                    fetch(CTX + '/api/servers')
                        .then(function(r) { return r.json(); })
                        .then(function(data) {
                            var servers = Array.isArray(data) ? data : (data.servers || []);
                            var companies = new Set();
                            servers.forEach(function(s) {
                                if (s.companyName) companies.add(s.companyName);
                            });
                            container.innerHTML = '<span class="widget-value">' + servers.length + ' servrar \u00b7 ' + companies.size + ' kunder</span>';
                            container.parentElement.className = 'widget-chip widget-ok';
                        })
                        .catch(function() {
                            container.innerHTML = '<span class="widget-value">Fel</span>';
                        });
                },
                interval: 600000
            },
            quick_links: {
                render(container) {
                    var links = JSON.parse(localStorage.getItem('dashboard_quick_links') || '[{"name":"VIPS","url":"https://vips.infocaption.com"},{"name":"Jira","url":"https://infocaption.atlassian.net"}]');
                    if (links.length === 0) {
                        container.innerHTML = '<span class="widget-value" style="color:var(--ic-text-muted)">Inga l\u00e4nkar</span>';
                        return;
                    }
                    // Build links safely using DOM to prevent XSS from localStorage data
                    container.innerHTML = '';
                    links.forEach(function(l, idx) {
                        if (idx > 0) {
                            var sep = document.createElement('span');
                            sep.style.color = '#ccc';
                            sep.textContent = ' \u00b7 ';
                            container.appendChild(sep);
                        }
                        var a = document.createElement('a');
                        a.href = l.url;
                        a.target = '_blank';
                        a.title = l.url;
                        a.textContent = l.name;
                        container.appendChild(a);
                    });
                },
                interval: 0
            },
            team_online: {
                render(container) {
                    container.innerHTML = '<span class="widget-value">Laddar\u2026</span>';
                    fetch(CTX + '/api/groups')
                        .then(function(r) { return r.json(); })
                        .then(function(groups) {
                            var myGroups = groups.filter(function(g) { return g.isMember && g.name !== 'Alla'; });
                            if (myGroups.length === 0) {
                                container.innerHTML = '<span class="widget-value">Inga grupper</span>';
                                return;
                            }
                            var parts = myGroups.slice(0, 3).map(function(g) {
                                return g.name + ' (' + (g.memberCount || '?') + ')';
                            });
                            container.innerHTML = '<span class="widget-value">' + parts.join(' \u00b7 ') + '</span>';
                        })
                        .catch(function() {
                            container.innerHTML = '<span class="widget-value">Fel</span>';
                        });
                },
                interval: 600000
            },

            jira_issues: {
                render: async function(container) {
                    try {
                        var resp = await fetch(CTX + '/api/jira/issues');
                        if (!resp.ok) {
                            var errData = await resp.json();
                            if (errData.error && errData.error.indexOf('konfigurerad') >= 0) {
                                container.innerHTML = '<span class="widget-icon">\uD83D\uDCCB</span><span class="widget-value">Ej konfigurerad</span>';
                                container.className = 'widget-chip';
                                return;
                            }
                            throw new Error(errData.error || 'HTTP ' + resp.status);
                        }
                        var data = await resp.json();
                        var count = data.total || 0;
                        var cls = 'widget-chip widget-ok';
                        if (count > 15) cls = 'widget-chip widget-danger';
                        else if (count > 5) cls = 'widget-chip widget-warn';
                        var label = count === 0 ? 'Inga \u00e4renden' : count === 1 ? '1 \u00e4rende' : count + ' \u00e4renden';
                        container.className = cls;
                        container.style.cursor = 'pointer';
                        container.innerHTML = '<span class="widget-icon">\uD83D\uDCCB</span><span class="widget-value">' + label + '</span>';
                        container.onclick = function() { loadModule('jira'); };
                    } catch (e) {
                        container.className = 'widget-chip';
                        container.innerHTML = '<span class="widget-icon">\uD83D\uDCCB</span><span class="widget-value">Fel</span>';
                        container.title = e.message;
                    }
                },
                interval: 300000
            }
        };

        var _cachedWidgetPrefs = null;

        function getWidgetPrefs() {
            if (_cachedWidgetPrefs) return _cachedWidgetPrefs;
            try {
                var stored = localStorage.getItem('dashboard_widgets');
                if (stored) return JSON.parse(stored);
            } catch(e) {}
            return { enabled: ['date_week', 'clock'], position: 'top' };
        }

        function setWidgetPrefs(prefs) {
            _cachedWidgetPrefs = prefs;
            localStorage.setItem('dashboard_widgets', JSON.stringify(prefs));
            // Persist to server
            fetch(CTX + '/api/preferences', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key: 'dashboard_widgets', value: JSON.stringify(prefs) })
            }).catch(function() {});
        }

        async function loadServerPrefs() {
            try {
                var resp = await fetch(CTX + '/api/preferences?key=dashboard_widgets');
                if (resp.ok) {
                    var data = await resp.json();
                    if (data.value) {
                        var prefs = JSON.parse(data.value);
                        _cachedWidgetPrefs = prefs;
                        localStorage.setItem('dashboard_widgets', JSON.stringify(prefs));
                        return prefs;
                    }
                }
            } catch(e) {}
            return null;
        }

        async function initWidgets() {
            try {
                var resp = await fetch(CTX + '/api/widgets');
                if (!resp.ok) return;
                availableWidgets = await resp.json();
            } catch(e) {
                console.error('Failed to load widgets:', e);
                return;
            }

            if (availableWidgets.length === 0) return;

            // Try loading from server first, fall back to localStorage
            var serverPrefs = await loadServerPrefs();
            var prefs = serverPrefs || getWidgetPrefs();
            renderWidgetBar(prefs);
        }

        function renderWidgetBar(prefs) {
            // Clear existing intervals
            activeWidgetIntervals.forEach(function(id) { clearInterval(id); });
            activeWidgetIntervals = [];

            var bar = document.getElementById('widgetBar');
            var content = document.getElementById('widgetBarContent');
            var moduleContainer = document.getElementById('moduleContainer');
            content.innerHTML = '';

            // Filter enabled widgets from available, sorted by user order
            var enabledWidgets = availableWidgets.filter(function(w) {
                return prefs.enabled.indexOf(w.renderKey) !== -1;
            });
            if (prefs.order && prefs.order.length > 0) {
                enabledWidgets.sort(function(a, b) {
                    var ia = prefs.order.indexOf(a.renderKey);
                    var ib = prefs.order.indexOf(b.renderKey);
                    if (ia === -1) ia = 999;
                    if (ib === -1) ib = 999;
                    return ia - ib;
                });
            }

            if (enabledWidgets.length === 0) {
                bar.style.display = 'none';
                moduleContainer.classList.remove('has-bottom-bar');
                // Hide topbar date only if date_week widget is active
                toggleTopbarDate(true);
                return;
            }

            bar.style.display = 'flex';

            // Position
            if (prefs.position === 'bottom') {
                bar.classList.add('bottom');
                moduleContainer.classList.add('has-bottom-bar');
            } else {
                bar.classList.remove('bottom');
                moduleContainer.classList.remove('has-bottom-bar');
            }

            // Render each widget chip
            enabledWidgets.forEach(function(w) {
                var chip = document.createElement('div');
                chip.className = 'widget-chip';
                chip.setAttribute('data-widget', w.renderKey);

                var iconSpan = document.createElement('span');
                iconSpan.className = 'widget-icon';
                iconSpan.textContent = w.icon;
                chip.appendChild(iconSpan);

                var valueSpan = document.createElement('span');
                valueSpan.className = 'widget-value';
                valueSpan.textContent = 'Laddar\u2026';
                chip.appendChild(valueSpan);

                content.appendChild(chip);

                // Render widget content
                var renderer = widgetRenderers[w.renderKey];
                var renderTarget = document.createElement('span');
                renderTarget.style.display = 'contents';
                chip.innerHTML = '';
                chip.appendChild(iconSpan);
                chip.appendChild(renderTarget);

                if (renderer) {
                    renderer.render(renderTarget);

                    if (renderer.interval > 0) {
                        var intervalId = setInterval(function() {
                            renderer.render(renderTarget);
                        }, renderer.interval);
                        activeWidgetIntervals.push(intervalId);
                    }
                } else if (w.customHtml || w.customJs) {
                    // Custom widget: render HTML then execute JS
                    renderCustomWidget(renderTarget, w);
                    if (w.refreshSeconds > 0) {
                        var intervalId = setInterval(function() {
                            renderCustomWidget(renderTarget, w);
                        }, w.refreshSeconds * 1000);
                        activeWidgetIntervals.push(intervalId);
                    }
                }
            });

            // Toggle topbar date display
            var dateWidgetActive = prefs.enabled.indexOf('date_week') !== -1;
            toggleTopbarDate(!dateWidgetActive);
        }

        function renderCustomWidget(container, widgetDef) {
            // Render custom widgets inside a sandboxed iframe for XSS protection
            container.innerHTML = '';
            var iframe = document.createElement('iframe');
            iframe.setAttribute('sandbox', 'allow-scripts');
            iframe.style.cssText = 'border:none;width:100%;height:100%;overflow:hidden;background:transparent;';
            iframe.setAttribute('title', 'Widget: ' + (widgetDef.name || widgetDef.renderKey));

            var htmlContent = '<!DOCTYPE html><html><head><style>'
                + 'body{margin:0;padding:0;font-family:Inter,-apple-system,sans-serif;font-size:13px;'
                + 'color:#323232;overflow:hidden;background:transparent;display:flex;align-items:center;'
                + 'justify-content:center;min-height:100%;}'
                + '.widget-value{font-size:1.1em;font-weight:600;}'
                + '.widget-label{font-size:0.75em;color:#6c757d;}'
                + '</style></head><body>'
                + (widgetDef.customHtml || '')
                + (widgetDef.customJs ? '<script>try{' + widgetDef.customJs + '}catch(e){document.body.innerHTML="<span class=widget-value style=color:#ef4444>JS-fel</span>";}<\/script>' : '')
                + '</body></html>';

            iframe.srcdoc = htmlContent;
            container.appendChild(iframe);
        }

        function toggleTopbarDate(show) {
            var dateDisplay = document.querySelector('.date-display');
            if (dateDisplay) {
                dateDisplay.style.display = show ? '' : 'none';
            }
        }

        function openWidgetSettings() {
            var prefs = getWidgetPrefs();
            tempWidgetPosition = prefs.position || 'top';

            // Sort widgets by user order if available
            var ordered = availableWidgets.slice();
            if (prefs.order && prefs.order.length > 0) {
                ordered.sort(function(a, b) {
                    var ia = prefs.order.indexOf(a.renderKey);
                    var ib = prefs.order.indexOf(b.renderKey);
                    if (ia === -1) ia = 999;
                    if (ib === -1) ib = 999;
                    return ia - ib;
                });
            }

            var list = document.getElementById('widgetOptionsList');
            list.innerHTML = ordered.map(function(w) {
                var checked = prefs.enabled.indexOf(w.renderKey) !== -1 ? 'checked' : '';
                return '<label class="widget-option" draggable="true" data-key="' + w.renderKey + '">' +
                    '<span class="drag-handle" title="Dra f\u00f6r att \u00e4ndra ordning">\u2630</span>' +
                    '<input type="checkbox" value="' + w.renderKey + '" ' + checked + '>' +
                    '<span class="widget-option-icon">' + w.icon + '</span>' +
                    '<div class="widget-option-info">' +
                        '<div class="widget-option-name">' + w.name + '</div>' +
                        '<div class="widget-option-desc">' + (w.description || '') + '</div>' +
                    '</div>' +
                '</label>';
            }).join('');

            // Setup drag-n-drop for reordering
            setupWidgetDragDrop();

            // Position buttons
            document.querySelectorAll('.widget-position-btn').forEach(function(btn) {
                btn.classList.toggle('active', btn.getAttribute('data-pos') === tempWidgetPosition);
            });

            document.getElementById('widgetSettingsOverlay').classList.add('show');
        }

        function closeWidgetSettings() {
            document.getElementById('widgetSettingsOverlay').classList.remove('show');
        }

        function selectWidgetPosition(pos) {
            tempWidgetPosition = pos;
            document.querySelectorAll('.widget-position-btn').forEach(function(btn) {
                btn.classList.toggle('active', btn.getAttribute('data-pos') === pos);
            });
        }

        function saveWidgetPrefs() {
            var enabled = [];
            document.querySelectorAll('#widgetOptionsList input[type="checkbox"]:checked').forEach(function(cb) {
                enabled.push(cb.value);
            });

            // Capture order from current DOM order
            var order = [];
            document.querySelectorAll('#widgetOptionsList .widget-option').forEach(function(el) {
                order.push(el.getAttribute('data-key'));
            });

            var prefs = { enabled: enabled, position: tempWidgetPosition, order: order };
            setWidgetPrefs(prefs);
            renderWidgetBar(prefs);
            closeWidgetSettings();
            showToast('Widgetinst\u00e4llningar sparade');
        }

        // --- Widget drag-n-drop reorder ---
        function setupWidgetDragDrop() {
            var list = document.getElementById('widgetOptionsList');
            var dragItem = null;

            list.querySelectorAll('.widget-option').forEach(function(item) {
                item.addEventListener('dragstart', function(e) {
                    dragItem = this;
                    this.classList.add('dragging');
                    e.dataTransfer.effectAllowed = 'move';
                });
                item.addEventListener('dragend', function() {
                    this.classList.remove('dragging');
                    list.querySelectorAll('.widget-option').forEach(function(el) { el.classList.remove('drag-over'); });
                    dragItem = null;
                });
                item.addEventListener('dragover', function(e) {
                    e.preventDefault();
                    e.dataTransfer.dropEffect = 'move';
                    if (this !== dragItem) {
                        list.querySelectorAll('.widget-option').forEach(function(el) { el.classList.remove('drag-over'); });
                        this.classList.add('drag-over');
                    }
                });
                item.addEventListener('drop', function(e) {
                    e.preventDefault();
                    if (dragItem && this !== dragItem) {
                        // Insert dragItem before this element
                        list.insertBefore(dragItem, this);
                    }
                    list.querySelectorAll('.widget-option').forEach(function(el) { el.classList.remove('drag-over'); });
                });
            });
        }

        // ═══════════ Manage Page Loading ═══════════
        var managePages = {
            'create-module':  { name: 'Skapa modul',  icon: '\u2795',     url: CTX + '/module/create' },
            'manage-modules': { name: 'Mina moduler', icon: '\u2699',     url: CTX + '/module/manage' },
            'manage-groups':  { name: 'Grupper',      icon: '\uD83D\uDC65', url: CTX + '/group/manage' },
            'manage-users':   { name: 'Anv\u00e4ndare',    icon: '\uD83D\uDC64', url: CTX + '/manage-users' },
            'admin':          { name: 'Admin',        icon: '\uD83D\uDD10', url: CTX + '/admin.jsp' },
            'settings':       { name: 'Inställningar', icon: '\u2699',     url: CTX + '/settings.jsp' }
        };

        function loadManagePage(pageId) {
            var page = managePages[pageId];
            if (!page) return;

            showLoading(true);

            // Clear active state from all nav-items, activate the manage item
            document.querySelectorAll('.nav-item').forEach(function(item) {
                item.classList.remove('active');
                if (item.dataset.manage === pageId) {
                    item.classList.add('active');
                }
            });

            document.getElementById('currentModule').textContent = page.name;
            document.getElementById('welcomeScreen').style.display = 'none';
            var iframe = document.getElementById('moduleFrame');
            iframe.style.display = 'block';

            if (window.innerWidth <= 900) {
                closeSidebar();
            }

            iframe.onload = function() {
                showLoading(false);
                currentModuleId = null;
                updateDocButton();
                var url = new URL(window.location);
                url.searchParams.set('manage', pageId);
                url.searchParams.delete('module');
                window.history.pushState({}, '', url);
            };

            iframe.onerror = function() {
                showLoading(false);
                showToast('Kunde inte ladda sidan', 'error');
            };

            iframe.src = page.url;
        }

        // ═══════════ postMessage listener (iframe → parent) ═══════════
        window.addEventListener('message', function(event) {
            // Only accept messages from same origin to prevent cross-origin injection
            if (event.origin !== window.location.origin) return;

            var data = event.data;
            if (!data || !data.type) return;

            switch (data.type) {
                case 'SHOW_TOAST':
                    showToast(data.message || '', data.toastType || 'success');
                    break;
                case 'SHOW_MODULE_DOC':
                    openModuleDoc();
                    break;
                case 'LOAD_MODULE':
                    if (data.moduleId) loadModule(data.moduleId);
                    break;
                case 'LOAD_MANAGE_PAGE':
                    if (data.pageId && managePages[data.pageId]) loadManagePage(data.pageId);
                    break;
                case 'REFRESH_MODULES':
                    moduleRegistry.load().then(function() { renderModuleNav(); renderModuleCards(); });
                    break;
                case 'GET_CSRF_TOKEN':
                    // Send CSRF token to requesting iframe module
                    var csrfMeta = document.querySelector('meta[name="csrf-token"]');
                    var csrfVal = csrfMeta ? csrfMeta.content : '';
                    if (event.source) {
                        event.source.postMessage({ type: 'CSRF_TOKEN', token: csrfVal }, event.origin);
                    }
                    break;
                case 'UPDATE_URL':
                    // Module requests parent URL update with its params
                    if (data.moduleParams) {
                        var parentUrl = new URL(window.location);
                        // Preserve 'module' param, replace everything else
                        var keep = parentUrl.searchParams.get('module');
                        // Clear old module params
                        ['from','to','tab','site'].forEach(function(k) { parentUrl.searchParams.delete(k); });
                        if (keep) parentUrl.searchParams.set('module', keep);
                        Object.keys(data.moduleParams).forEach(function(k) {
                            parentUrl.searchParams.set(k, data.moduleParams[k]);
                        });
                        history.replaceState(null, '', parentUrl);
                    }
                    break;
                case 'REFRESH_USER':
                    if (data.fullName) {
                        var nameEl = document.getElementById('topbarUserName');
                        var dropNameEl = document.getElementById('dropdownUserName');
                        if (nameEl) nameEl.textContent = data.fullName;
                        if (dropNameEl) dropNameEl.textContent = data.fullName;
                    }
                    // Handle avatar update (with URL scheme validation)
                    if (data.avatarUrl !== undefined) {
                        var avatarEl = document.getElementById('topbarAvatar');
                        if (avatarEl) {
                            if (data.avatarUrl && !/^(javascript|data|vbscript):/i.test(data.avatarUrl.trim())) {
                                var safeUrl = CTX + '/' + data.avatarUrl.replace(/["<>]/g, '');
                                var avatarImg = document.createElement('img');
                                avatarImg.src = safeUrl;
                                avatarImg.alt = '';
                                avatarImg.id = 'topbarAvatarImg';
                                avatarImg.style.cssText = 'width:100%;height:100%;object-fit:cover;border-radius:50%;';
                                avatarEl.innerHTML = '';
                                avatarEl.appendChild(avatarImg);
                            } else if (!data.avatarUrl) {
                                var initial = data.fullName ? data.fullName.charAt(0).toUpperCase() :
                                    (document.getElementById('topbarUserName') ? document.getElementById('topbarUserName').textContent.charAt(0).toUpperCase() : '?');
                                avatarEl.innerHTML = '';
                                var span = document.createElement('span');
                                span.id = 'topbarAvatarInitial';
                                span.textContent = initial;
                                avatarEl.appendChild(span);
                            }
                        }
                    } else if (data.fullName && !document.getElementById('topbarAvatarImg')) {
                        var avatarEl2 = document.getElementById('topbarAvatarInitial');
                        if (avatarEl2) avatarEl2.textContent = data.fullName.charAt(0).toUpperCase();
                    }
                    break;
            }
        });

        // ═══════════ Module Doc Popup ═══════════
        function openModuleDoc() {
            if (!currentModuleId) return;
            var module = moduleRegistry.getById(currentModuleId);
            if (!module) return;

            // Header
            document.getElementById('docHeaderIcon').textContent = module.icon || '\uD83D\uDCC4';
            document.getElementById('docHeaderTitle').textContent = module.name;
            document.getElementById('docHeaderSubtitle').textContent = module.category === 'analytics' ? 'Analys & Rapporter' : module.category === 'tools' ? 'Verktyg' : module.category === 'admin' ? 'Administration' : (module.category || '');

            // Build body content
            var html = '';

            // Description section
            if (module.description) {
                html += '<div class="module-doc-section">';
                html += '<div class="module-doc-section-title">\uD83D\uDCCB Beskrivning</div>';
                html += '<div class="module-doc-description">' + escapeHtml(module.description) + '</div>';
                html += '</div>';
            }

            // AI Spec section (detailed documentation)
            if (module.aiSpecText) {
                html += '<div class="module-doc-section">';
                html += '<div class="module-doc-section-title">\uD83D\uDCD6 Dokumentation</div>';
                html += '<div class="module-doc-spec">' + escapeHtml(module.aiSpecText) + '</div>';
                html += '</div>';
            } else if (!module.description) {
                html += '<div class="module-doc-no-spec">Ingen dokumentation tillgänglig för denna modul.</div>';
            }

            // Meta info section
            html += '<div class="module-doc-section">';
            html += '<div class="module-doc-section-title">\u2139\uFE0F Information</div>';
            html += '<div class="module-doc-meta">';
            html += '<span class="module-doc-meta-item">\uD83D\uDCC1 ' + escapeHtml(module.id) + '</span>';
            if (module.moduleType) {
                var typeLabel = module.moduleType === 'system' ? 'System' : module.moduleType === 'private' ? 'Privat' : 'Delad';
                html += '<span class="module-doc-meta-item">\uD83D\uDD12 ' + typeLabel + '</span>';
            }
            if (module.version) {
                html += '<span class="module-doc-meta-item">\uD83C\uDFF7\uFE0F v' + escapeHtml(module.version) + '</span>';
            }
            if (module.groups && module.groups.length > 0) {
                html += '<span class="module-doc-meta-item">\uD83D\uDC65 ' + module.groups.map(escapeHtml).join(', ') + '</span>';
            }
            html += '</div>';
            html += '</div>';

            document.getElementById('docBody').innerHTML = html;
            document.getElementById('moduleDocOverlay').classList.add('show');
        }

        function closeModuleDoc() {
            document.getElementById('moduleDocOverlay').classList.remove('show');
        }

        function updateDocButton() {
            var btn = document.getElementById('moduleDocBtn');
            if (currentModuleId) {
                btn.classList.add('visible');
            } else {
                btn.classList.remove('visible');
            }
        }

        function escapeHtml(text) {
            if (!text) return '';
            var div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        // Initialize
        async function init() {
            updateDateTime();
            setInterval(updateDateTime, 60000);

            await moduleRegistry.load();
            await loadModuleOrder();
            renderModuleNav();
            renderModuleCards();

            // Load widgets
            await initWidgets();

            const urlParams = new URLSearchParams(window.location.search);
            const moduleParam = urlParams.get('module');
            const manageParam = urlParams.get('manage');
            if (moduleParam) {
                loadModule(moduleParam);
            } else if (manageParam && managePages[manageParam]) {
                loadManagePage(manageParam);
            }
        }

        // Update date and week number
        function updateDateTime() {
            const now = new Date();
            const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
            const dateStr = now.toLocaleDateString('sv-SE', options);
            document.getElementById('currentDate').textContent = dateStr.charAt(0).toUpperCase() + dateStr.slice(1);

            const startOfYear = new Date(now.getFullYear(), 0, 1);
            const days = Math.floor((now - startOfYear) / (24 * 60 * 60 * 1000));
            const weekNum = Math.ceil((days + startOfYear.getDay() + 1) / 7);
            document.getElementById('currentWeek').textContent = 'Vecka ' + weekNum;
        }

        // Render navigation
        var _moduleOrder = null; // loaded from prefs

        async function loadModuleOrder() {
            try {
                var resp = await fetch(CTX + '/api/preferences?key=dashboard.moduleOrder');
                if (resp.ok) {
                    var data = await resp.json();
                    if (data.value) _moduleOrder = JSON.parse(data.value);
                }
            } catch(e) { /* ignore */ }
        }

        function saveModuleOrder(order) {
            _moduleOrder = order;
            fetch(CTX + '/api/preferences', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ key: 'dashboard.moduleOrder', value: JSON.stringify(order) })
            }).catch(function() {});
        }

        function renderModuleNav() {
            var container = document.getElementById('moduleNav');
            var docsContainer = document.getElementById('docsNav');

            // Separate docs from other modules
            var navModules = moduleRegistry.getAll().filter(function(m) { return m.id !== 'docs'; });
            var docsModule = moduleRegistry.getById('docs');

            // Apply user order
            if (_moduleOrder && _moduleOrder.length > 0) {
                navModules.sort(function(a, b) {
                    var ia = _moduleOrder.indexOf(a.id);
                    var ib = _moduleOrder.indexOf(b.id);
                    if (ia === -1) ia = 999;
                    if (ib === -1) ib = 999;
                    return ia - ib;
                });
            }

            container.innerHTML = navModules.map(function(module) {
                var badgeClass = module.badge === 'Nytt' ? 'new' : '';
                var typeBadge = '';
                if (module.moduleType === 'private') {
                    typeBadge = '<span class="nav-badge">\uD83D\uDD12</span>';
                } else if (module.moduleType === 'shared' && module.groups && module.groups.length > 0) {
                    var isRestricted = !(module.groups.length === 1 && module.groups[0] === 'Alla');
                    if (isRestricted) {
                        typeBadge = '<span class="nav-badge" title="' + module.groups.join(', ') + '">' + module.groups[0] + '</span>';
                    } else if (!module.isOwner) {
                        typeBadge = '<span class="nav-badge">\uD83D\uDC65</span>';
                    }
                } else if (module.moduleType === 'shared' && !module.isOwner) {
                    typeBadge = '<span class="nav-badge">\uD83D\uDC65</span>';
                }
                return '<div class="nav-item" data-module="' + module.id + '" draggable="true" onclick="loadModule(\'' + module.id + '\')">' +
                    '<span class="nav-icon">' + module.icon + '</span>' +
                    '<span class="nav-text">' + module.name + '</span>' +
                    (module.badge ? '<span class="nav-badge ' + badgeClass + '">' + module.badge + '</span>' : typeBadge) +
                '</div>';
            }).join('');

            // Setup drag-n-drop for module reordering
            setupModuleNavDragDrop(container);

            // Docs nav item
            if (docsModule && docsContainer) {
                docsContainer.innerHTML =
                    '<div class="nav-item" data-module="docs" onclick="loadModule(\'docs\')">' +
                        '<span class="nav-icon">' + docsModule.icon + '</span>' +
                        '<span class="nav-text">' + docsModule.name + '</span>' +
                    '</div>';
            }
        }

        function setupModuleNavDragDrop(container) {
            var dragItem = null;
            container.querySelectorAll('.nav-item[draggable]').forEach(function(item) {
                item.addEventListener('dragstart', function(e) {
                    dragItem = this;
                    this.classList.add('dragging');
                    e.dataTransfer.effectAllowed = 'move';
                });
                item.addEventListener('dragend', function() {
                    this.classList.remove('dragging');
                    container.querySelectorAll('.nav-item').forEach(function(el) { el.classList.remove('drag-over'); });
                    // Save new order
                    var order = [];
                    container.querySelectorAll('.nav-item').forEach(function(el) {
                        order.push(el.getAttribute('data-module'));
                    });
                    saveModuleOrder(order);
                    dragItem = null;
                });
                item.addEventListener('dragover', function(e) {
                    e.preventDefault();
                    e.dataTransfer.dropEffect = 'move';
                    if (this !== dragItem) {
                        container.querySelectorAll('.nav-item').forEach(function(el) { el.classList.remove('drag-over'); });
                        this.classList.add('drag-over');
                    }
                });
                item.addEventListener('drop', function(e) {
                    e.preventDefault();
                    if (dragItem && this !== dragItem) {
                        container.insertBefore(dragItem, this);
                    }
                    container.querySelectorAll('.nav-item').forEach(function(el) { el.classList.remove('drag-over'); });
                });
            });
        }

        // Render module cards
        function renderModuleCards() {
            var container = document.getElementById('moduleCards');
            var cardModules = moduleRegistry.getAll().filter(function(m) { return m.id !== 'docs'; });
            // Apply user order
            if (_moduleOrder && _moduleOrder.length > 0) {
                cardModules.sort(function(a, b) {
                    var ia = _moduleOrder.indexOf(a.id);
                    var ib = _moduleOrder.indexOf(b.id);
                    if (ia === -1) ia = 999;
                    if (ib === -1) ib = 999;
                    return ia - ib;
                });
            }
            const moduleCards = cardModules.map(function(module) {
                return '<div class="module-card" onclick="loadModule(\'' + module.id + '\')">' +
                    '<div class="module-card-icon">' + module.icon + '</div>' +
                    '<div class="module-card-title">' + module.name + '</div>' +
                    '<div class="module-card-description">' + (module.description || '') + '</div>' +
                    (module.badge ? '<span class="module-card-badge">' + module.badge + '</span>' : '') +
                '</div>';
            }).join('');

            const vipsCard =
                '<a class="module-card" href="https://vips.infocaption.com" target="_blank" style="text-decoration: none; color: inherit;">' +
                    '<div class="module-card-icon">\uD83C\uDF10</div>' +
                    '<div class="module-card-title">VIPS Intranät</div>' +
                    '<div class="module-card-description">Intern dokumentation, guider och resurser för InfoCaption.</div>' +
                    '<span class="module-card-badge">\u2197 Extern</span>' +
                '</a>';

            container.innerHTML = moduleCards + vipsCard;
        }

        // Load a module
        function loadModule(moduleId) {
            if (moduleId === 'home') {
                showWelcomeScreen();
                return;
            }

            const module = moduleRegistry.getById(moduleId);
            if (!module) {
                showToast('Modulen hittades inte', 'error');
                return;
            }

            loadModuleFrame(moduleId, module);
        }

        function loadModuleFrame(moduleId, module) {
            showLoading(true);

            document.querySelectorAll('.nav-item').forEach(item => {
                item.classList.remove('active');
                if (item.dataset.module === moduleId) {
                    item.classList.add('active');
                }
            });

            document.getElementById('currentModule').textContent = module.name;
            document.getElementById('welcomeScreen').style.display = 'none';
            const iframe = document.getElementById('moduleFrame');
            iframe.style.display = 'block';

            if (window.innerWidth <= 900) {
                closeSidebar();
            }

            iframe.onload = function() {
                showLoading(false);
                currentModuleId = moduleId;
                updateDocButton();
                const url = new URL(window.location);
                url.searchParams.set('module', moduleId);
                url.searchParams.delete('manage');
                window.history.pushState({}, '', url);
            };

            iframe.onerror = function() {
                showLoading(false);
                showToast('Kunde inte ladda modulen', 'error');
            };

            // Pass parent URL params (from, to, tab, site) to module iframe
            var moduleUrl = new URL(module.path, window.location.origin);
            var parentParams = new URLSearchParams(window.location.search);
            ['from','to','tab','site'].forEach(function(k) {
                if (parentParams.get(k)) moduleUrl.searchParams.set(k, parentParams.get(k));
            });
            iframe.src = moduleUrl.pathname + moduleUrl.search;
        }

        // Show welcome screen
        function showWelcomeScreen() {
            document.getElementById('welcomeScreen').style.display = 'flex';
            document.getElementById('moduleFrame').style.display = 'none';
            document.getElementById('currentModule').textContent = 'Startsida';
            currentModuleId = null;
            updateDocButton();

            document.querySelectorAll('.nav-item').forEach(item => {
                item.classList.remove('active');
                if (item.dataset.module === 'home') {
                    item.classList.add('active');
                }
            });

            const url = new URL(window.location);
            url.searchParams.delete('module');
            url.searchParams.delete('manage');
            window.history.pushState({}, '', url);
        }

        // User dropdown
        function toggleUserDropdown() {
            document.getElementById('userDropdown').classList.toggle('open');
            document.getElementById('userDropdownTrigger').classList.toggle('open');
        }

        function closeUserDropdown() {
            document.getElementById('userDropdown').classList.remove('open');
            document.getElementById('userDropdownTrigger').classList.remove('open');
        }

        document.addEventListener('click', function(e) {
            var wrapper = document.querySelector('.user-dropdown-wrapper');
            if (wrapper && !wrapper.contains(e.target)) {
                closeUserDropdown();
            }
        });

        // Toggle sidebar
        function toggleSidebar() {
            const sidebar = document.getElementById('sidebar');
            const main = document.getElementById('mainContent');
            const overlay = document.getElementById('sidebarOverlay');

            if (window.innerWidth <= 900) {
                sidebar.classList.toggle('open');
                overlay.classList.toggle('show');
            } else {
                sidebar.classList.toggle('collapsed');
                main.classList.toggle('expanded');
            }
        }

        function closeSidebar() {
            document.getElementById('sidebar').classList.remove('open');
            document.getElementById('sidebarOverlay').classList.remove('show');
        }

        function showLoading(show) {
            const overlay = document.getElementById('loadingOverlay');
            overlay.classList.toggle('show', show);
        }

        function showToast(message, type) {
            type = type || 'success';
            const toast = document.getElementById('toast');
            document.getElementById('toastMessage').textContent = message;
            document.querySelector('.toast-icon').textContent = type === 'success' ? '\u2705' : '\u274C';
            toast.className = 'toast ' + type + ' show';
            setTimeout(function() { toast.classList.remove('show'); }, 3000);
        }

        window.addEventListener('popstate', function() {
            var params = new URLSearchParams(window.location.search);
            var moduleParam = params.get('module');
            var manageParam = params.get('manage');
            if (moduleParam) {
                loadModule(moduleParam);
            } else if (manageParam && managePages[manageParam]) {
                loadManagePage(manageParam);
            } else {
                showWelcomeScreen();
            }
        });

        init().catch(function(e) { console.error('Init failed:', e); });
    </script>
</body>
</html>
