-- Schema ban đầu của database `network` (dữ liệu dùng chung toàn network).
-- Chỉ chạy 1 lần khi khởi tạo. Thay đổi về sau sẽ do network-core quản lý bằng migration.
USE `network`;

-- Hồ sơ người chơi (Java, Bedrock qua Floodgate, Java có modpack; bản quyền hoặc crack)
CREATE TABLE IF NOT EXISTS players (
  uuid          CHAR(36)     NOT NULL PRIMARY KEY,
  name          VARCHAR(32)  NOT NULL,           -- người chơi Bedrock có tiền tố Floodgate, ví dụ ".TenNguoiChoi"
  platform      ENUM('java','bedrock','java_modded') NOT NULL DEFAULT 'java',
  auth          ENUM('premium','cracked','floodgate') NOT NULL DEFAULT 'cracked',  -- cách đăng nhập (LibreLogin / Floodgate)
  first_seen_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_players_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Danh sách chế độ (đồng bộ từ modes/<id>/mode.yml)
CREATE TABLE IF NOT EXISTS modes (
  id                VARCHAR(32)  NOT NULL PRIMARY KEY,
  display_name      VARCHAR(64)  NOT NULL,
  status            ENUM('open','beta','maintenance','closed') NOT NULL DEFAULT 'beta',
  platform          ENUM('paper','fabric') NOT NULL,
  minecraft_version VARCHAR(16)  NOT NULL,
  clients           JSON         NOT NULL,       -- ví dụ ["java","bedrock"]
  config            JSON         NULL,           -- toàn bộ mode.yml dạng JSON
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Các server con thuộc từng chế độ (tên trùng tên service trong Docker Compose)
CREATE TABLE IF NOT EXISTS servers (
  name       VARCHAR(32)  NOT NULL PRIMARY KEY,
  mode_id    VARCHAR(32)  NULL,                  -- NULL = lobby
  address    VARCHAR(128) NOT NULL,              -- ví dụ "banghoi-1:25565"
  enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_servers_mode FOREIGN KEY (mode_id) REFERENCES modes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dữ liệu khởi tạo, khớp với modes/*/mode.yml hiện có
INSERT INTO modes (id, display_name, status, platform, minecraft_version, clients) VALUES
  ('banghoi', 'Bang Hội Chiến', 'beta',   'paper',  'LATEST', JSON_ARRAY('java','bedrock')),
  ('pokemon', 'Pokémon',        'closed', 'fabric', '1.21.1', JSON_ARRAY('java_modded'))
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

INSERT INTO servers (name, mode_id, address) VALUES
  ('lobby',     NULL,      'lobby:25565'),
  ('banghoi-1', 'banghoi', 'banghoi-1:25565'),
  ('pokemon-1', 'pokemon', 'pokemon-1:25565')
ON DUPLICATE KEY UPDATE address = VALUES(address);
