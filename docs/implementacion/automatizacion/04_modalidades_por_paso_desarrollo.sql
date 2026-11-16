-- SOLO staging local autorizado: 127.0.0.1:3307, dima_bug_staging.
-- Cambios aditivos, sin UPDATE de procedimientos o ejecuciones existentes.
-- Ejecutar una sola vez con cuenta administradora, tras revisar las columnas presentes.
USE dima_bug_staging;
ALTER TABLE pr_paso ADD COLUMN paso_configuracion_json TEXT NULL, ADD COLUMN paso_config_historial TEXT NULL;
ALTER TABLE se_ejecucion_paso ADD COLUMN contrato_configuracion_json TEXT NULL;
ALTER TABLE u_sucursal ADD COLUMN destinatarios_json TEXT NULL;
ALTER TABLE se_solicitud_evidencia ADD COLUMN destinatarios_json TEXT NULL, ADD COLUMN configuracion_json TEXT NULL;
-- Permite auditar pasos manuales/informativos sin inventar una solicitud de correo.
ALTER TABLE se_evento_ejecucion MODIFY COLUMN solicitud_id VARCHAR(36) NULL;
-- Los modos continúan en VARCHAR(30); MANUAL y VALIDACION_EXTERNA conservan compatibilidad.
SHOW COLUMNS FROM pr_paso;
SHOW COLUMNS FROM se_ejecucion_paso;
