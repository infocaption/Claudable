-- 032_knowledge_base.sql
-- Knowledge Base: MD documents as MCP endpoints
-- Run: mysql -u icdashboarduser -p icdashboard --default-character-set=utf8mb4 < sql/032_knowledge_base.sql

-- Documents (MD files and other documentation)
CREATE TABLE IF NOT EXISTS kb_documents (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    slug            VARCHAR(255) NOT NULL COMMENT 'URL-friendly identifier',
    title           VARCHAR(500) NOT NULL,
    content         MEDIUMTEXT NULL COMMENT 'Full document content (Markdown)',
    file_type       VARCHAR(50) NOT NULL DEFAULT 'markdown' COMMENT 'markdown, text, etc.',
    tags            VARCHAR(1000) NULL COMMENT 'JSON array of tags',
    created_by      INT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_kb_doc_slug (slug),
    INDEX idx_kb_doc_title (title(100)),
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Collections (each collection = one MCP endpoint with its own tool_prefix)
CREATE TABLE IF NOT EXISTS kb_collections (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT NULL,
    tool_prefix     VARCHAR(100) NOT NULL COMMENT 'MCP tool namespace, e.g. support_docs',
    is_active       TINYINT(1) NOT NULL DEFAULT 1,
    created_by      INT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_kb_coll_prefix (tool_prefix),
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- N:N mapping: documents <-> collections (with sort_order for display ordering)
CREATE TABLE IF NOT EXISTS kb_collection_documents (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    collection_id   INT NOT NULL,
    document_id     INT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    added_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_kb_cd_unique (collection_id, document_id),
    FOREIGN KEY (collection_id) REFERENCES kb_collections(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES kb_documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AppConfig keys
INSERT IGNORE INTO app_config (config_key, config_value, description) VALUES
    ('kb.enabled', 'true', 'Aktivera kunskapsbas-MCP-tools'),
    ('kb.maxDocumentSize', '1048576', 'Max dokumentstorlek i bytes (standard 1 MB)');
