-- Migration 003: Email broadcast tables
-- These tables are auto-created by EmailApiServlet.init() on startup.
-- This file is kept as reference documentation.

-- Email templates (reusable email content)
CREATE TABLE IF NOT EXISTS email_templates (
  id INT AUTO_INCREMENT PRIMARY KEY,
  owner_user_id INT NOT NULL,
  name VARCHAR(255) NOT NULL,
  subject VARCHAR(500) NOT NULL,
  body_html LONGTEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Email send records (one per broadcast)
CREATE TABLE IF NOT EXISTS email_sends (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sender_user_id INT NOT NULL,
  template_id INT NULL,
  subject VARCHAR(500) NOT NULL,
  body_html LONGTEXT NOT NULL,
  recipient_count INT DEFAULT 0,
  sent_count INT DEFAULT 0,
  failed_count INT DEFAULT 0,
  status ENUM('pending','sending','completed','failed') DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  FOREIGN KEY (sender_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (template_id) REFERENCES email_templates(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Individual recipients per send (tracking per-email status)
CREATE TABLE IF NOT EXISTS email_recipients (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  send_id INT NOT NULL,
  email VARCHAR(255) NOT NULL,
  status ENUM('pending','sent','failed') DEFAULT 'pending',
  error_message TEXT NULL,
  operation_id VARCHAR(255) NULL,
  sent_at TIMESTAMP NULL,
  FOREIGN KEY (send_id) REFERENCES email_sends(id) ON DELETE CASCADE,
  INDEX idx_send_id (send_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Register the module
INSERT INTO modules (owner_user_id, module_type, name, icon, description, category, entry_file, directory_name)
VALUES (NULL, 'system', 'Utskick', '📬', 'Skapa och skicka e-postutskick via Azure Communication Services', 'tools', 'index.html', 'utskick');
