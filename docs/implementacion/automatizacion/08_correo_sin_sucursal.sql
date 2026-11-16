-- Solo staging autorizado: 127.0.0.1:3307 / dima_bug_staging.
-- Conserva registros y relaciones. Permite comunicaciones sin una sucursal ficticia.
USE dima_bug_staging;
ALTER TABLE se_solicitud_evidencia
  MODIFY COLUMN sucursal_id INT NULL,
  MODIFY COLUMN sucursal_codigo VARCHAR(30) NULL,
  MODIFY COLUMN sucursal_nombre VARCHAR(100) NULL,
  MODIFY COLUMN responsable_nombre VARCHAR(100) NULL;
-- Correos y evidencias admiten destinatarios directos. No es necesario volver a aplicar
-- esta migración si sus cuatro columnas ya aceptan NULL.
