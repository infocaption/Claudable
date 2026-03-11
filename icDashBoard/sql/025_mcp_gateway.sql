-- 025_mcp_gateway.sql
-- MCP Gateway tables and config keys
-- Run with: mysql -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < 025_mcp_gateway.sql

CREATE TABLE IF NOT EXISTS mcp_servers (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    transport_type  ENUM('http','stdio') NOT NULL DEFAULT 'http',
    endpoint_url    VARCHAR(1000) NULL,
    command         VARCHAR(500) NULL,
    command_args    TEXT NULL,
    auth_type       ENUM('none','bearer','api_key','basic') NOT NULL DEFAULT 'none',
    auth_config     TEXT NULL,
    tool_prefix     VARCHAR(50) NOT NULL UNIQUE,
    cached_tools    TEXT NULL,
    is_active       TINYINT(1) NOT NULL DEFAULT 1,
    last_connected  TIMESTAMP NULL,
    last_error      TEXT NULL,
    created_by      INT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mcp_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NULL,
    server_id       INT NULL,
    method          VARCHAR(50) NOT NULL,
    tool_name       VARCHAR(200) NULL,
    request_summary TEXT NULL,
    response_status ENUM('success','error','timeout') NOT NULL,
    error_message   TEXT NULL,
    duration_ms     INT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id) ON DELETE SET NULL,
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MCP config keys
INSERT IGNORE INTO app_config (config_key, config_value, description, is_secret) VALUES
('mcp.enabled', 'true', 'Global MCP gateway on/off', 0),
('mcp.rateLimit.requestsPerMinute', '60', 'Per-user MCP rate limit (requests/minute)', 0),
('mcp.httpTimeout', '30', 'HTTP upstream MCP timeout (seconds)', 0),
('mcp.stdioTimeout', '30', 'Stdio MCP response timeout (seconds)', 0),
('mcp.stdio.allowedCommands', 'npx,node,python,uv,docker', 'Whitelist of allowed stdio commands', 0),
('mcp.maxResponseSize', '5242880', 'Max MCP response size in bytes (5MB)', 0),
('mcp.auditRetentionDays', '30', 'Auto-purge MCP audit logs older than N days', 0),
('mcp.toolCallMaxArgs', '102400', 'Max tool call arguments size in bytes (100KB)', 0);
