-- SOLO DESARROLLO/STAGING LOCAL. No ejecutar sobre producción.
-- Destino del piloto verificado: 127.0.0.1:3307 / dima_bug_staging.
-- Ejecutar con cuenta administradora. No elimina ni modifica registros existentes.
-- Requiere 01_contrato_paso_mysql.sql (ya aplicado en la instancia local).
USE dima_bug_staging;

CREATE TABLE IF NOT EXISTS u_sucursal (
 sucursal_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 sucursal_codigo VARCHAR(30) NOT NULL,
 sucursal_nombre VARCHAR(100) NOT NULL,
 responsable_nombre VARCHAR(100) NOT NULL,
 responsable_email VARCHAR(120) NOT NULL,
 sucursal_activa BOOLEAN NOT NULL DEFAULT TRUE,
 revision BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_sucursal_codigo UNIQUE(sucursal_codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Este ALTER se ejecuta una sola vez. Si alguna columna existe, añadir solo la faltante.
ALTER TABLE pr_paso
 ADD COLUMN paso_modo_ejecucion VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
 ADD COLUMN paso_mensaje_evidencia VARCHAR(2000) NULL;

CREATE TABLE IF NOT EXISTS se_solicitud_evidencia (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 ejecucion_id INT NOT NULL,
 ejecucion_paso_id INT NOT NULL,
 ronda INT NOT NULL,
 sucursal_id INT NOT NULL,
 sucursal_codigo VARCHAR(30) NOT NULL,
 sucursal_nombre VARCHAR(100) NOT NULL,
 responsable_nombre VARCHAR(100) NOT NULL,
 responsable_email VARCHAR(120) NOT NULL,
 actor_id INT NOT NULL,
 procedimiento_id INT NOT NULL,
 clave_inicio VARCHAR(36) NULL,
 mensaje VARCHAR(2000) NOT NULL,
 recordatorio_1_horas INT NOT NULL,
 recordatorio_2_horas INT NOT NULL,
 escalamiento_horas INT NOT NULL,
 estado VARCHAR(30) NOT NULL,
 fecha_envio DATETIME(6) NULL,
 mensaje_enviado_id VARCHAR(100) NULL,
 regla_aplicada INT NOT NULL DEFAULT 0,
 proximo_seguimiento DATETIME(6) NULL,
 mensaje_recibido_id VARCHAR(36) NULL,
 huella_recibida VARCHAR(64) NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_solicitud_ronda UNIQUE(ejecucion_paso_id,ronda),
 CONSTRAINT uk_solicitud_inicio UNIQUE(actor_id,clave_inicio),
 INDEX ix_solicitud_seguimiento(estado,proximo_seguimiento),
 CONSTRAINT fk_solicitud_ejecucion FOREIGN KEY(ejecucion_id) REFERENCES se_ejecucion(ejecucion_id),
 CONSTRAINT fk_solicitud_paso FOREIGN KEY(ejecucion_paso_id) REFERENCES se_ejecucion_paso(ejecucion_paso_id),
 CONSTRAINT fk_solicitud_sucursal FOREIGN KEY(sucursal_id) REFERENCES u_sucursal(sucursal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS se_evidencia (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 solicitud_id VARCHAR(36) NOT NULL,
 categoria VARCHAR(20) NOT NULL,
 sha256 VARCHAR(64) NOT NULL,
 datos MEDIUMBLOB NOT NULL,
 recibida DATETIME(6) NOT NULL,
 CONSTRAINT uk_evidencia_categoria UNIQUE(solicitud_id,categoria),
 CONSTRAINT fk_evidencia_solicitud FOREIGN KEY(solicitud_id) REFERENCES se_solicitud_evidencia(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS se_evento_ejecucion (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ejecucion_id INT NOT NULL,
 solicitud_id VARCHAR(36) NOT NULL,
 tipo VARCHAR(60) NOT NULL,
 actor_id INT NULL,
 detalle VARCHAR(1000) NOT NULL,
 fecha DATETIME(6) NOT NULL,
 INDEX ix_evento_ejecucion(ejecucion_id,id),
 CONSTRAINT fk_evento_ejecucion FOREIGN KEY(ejecucion_id) REFERENCES se_ejecucion(ejecucion_id),
 CONSTRAINT fk_evento_solicitud FOREIGN KEY(solicitud_id) REFERENCES se_solicitud_evidencia(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Verificación: no contiene contactos reales, usuarios, passwords o un procedimiento publicado modificado.
SHOW COLUMNS FROM pr_paso;
SHOW TABLES LIKE 'se_%';
