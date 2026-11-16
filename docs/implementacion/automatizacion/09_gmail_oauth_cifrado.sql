-- Solo staging autorizado: 127.0.0.1:3307 / dima_bug_staging. No ejecutar en producción.
-- Crea almacenamiento OAuth nuevo; no modifica procedimientos ni comunicaciones históricas.
USE dima_bug_staging;
CREATE TABLE IF NOT EXISTS gm_cuenta_oauth (
  id INT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  estado VARCHAR(20) NOT NULL,
  access_token_cifrado TEXT NULL,
  refresh_token_cifrado TEXT NULL,
  revocacion_cifrada TEXT NULL,
  expira_en DATETIME(6) NULL,
  autorizado_por INT NULL
);
INSERT IGNORE INTO gm_cuenta_oauth(id,version,estado) VALUES(1,0,'DESCONECTADA');
CREATE TABLE IF NOT EXISTS gm_estado_oauth (
  estado_hash VARCHAR(64) PRIMARY KEY,
  navegador_hash VARCHAR(64) NOT NULL,
  pkce_cifrado TEXT NOT NULL,
  actor_id INT NOT NULL,
  expira_en DATETIME(6) NOT NULL,
  version_cuenta BIGINT NOT NULL,
  consumido BIT NOT NULL DEFAULT 0,
  INDEX ix_gm_estado_actor_expira(actor_id,expira_en)
);
CREATE TABLE IF NOT EXISTS gm_auditoria_oauth (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id INT NULL,
  tipo VARCHAR(40) NOT NULL,
  fecha DATETIME(6) NOT NULL
);
