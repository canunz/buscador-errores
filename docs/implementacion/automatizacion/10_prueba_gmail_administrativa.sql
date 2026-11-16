-- Solo staging autorizado: 127.0.0.1:3307 / dima_bug_staging. No ejecutar en producción.
-- Tablas independientes: no modifica OAuth, tokens, procedimientos ni ejecuciones.
USE dima_bug_staging;
CREATE TABLE IF NOT EXISTS gm_prueba_correo (
 id VARCHAR(36) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 actor_id INT NOT NULL, remitente VARCHAR(120) NOT NULL, destinatario VARCHAR(120) NOT NULL,
 estado VARCHAR(20) NOT NULL, creada DATETIME(6) NOT NULL, intento DATETIME(6) NULL,
 finalizada DATETIME(6) NULL, mensaje_id VARCHAR(100) NULL, resultado VARCHAR(40) NULL,
 INDEX ix_gm_prueba_destino_fecha(destinatario,creada)
);
CREATE TABLE IF NOT EXISTS gm_prueba_evento (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, prueba_id VARCHAR(36) NOT NULL,
 actor_id INT NOT NULL, destinatario VARCHAR(120) NOT NULL, estado VARCHAR(20) NOT NULL,
 fecha DATETIME(6) NOT NULL, mensaje_id VARCHAR(100) NULL, resultado VARCHAR(40) NULL,
 INDEX ix_gm_prueba_evento_prueba(prueba_id)
);
