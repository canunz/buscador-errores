-- Destino verificado el 7/11/2026: instancia LOCAL 127.0.0.1:3307.
-- Ejecutar con una cuenta administradora que tenga ALTER sobre esta tabla.
-- La cuenta de la aplicación recibió error 1142 y NO pudo aplicar el cambio.
-- Se verificó que las siete columnas faltaban. No repetir después de aplicarlo.
-- No contiene contraseñas. No elimina ni actualiza registros.
USE dima_bug_staging;

ALTER TABLE se_ejecucion_paso
    ADD COLUMN contrato_version INT NULL,
    ADD COLUMN contrato_orden INT NULL,
    ADD COLUMN contrato_instruccion TEXT NULL,
    ADD COLUMN contrato_critico BOOLEAN NULL,
    ADD COLUMN contrato_modo VARCHAR(30) NULL,
    ADD COLUMN estado_detallado VARCHAR(30) NULL,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

-- Deben aparecer siete filas. En esta instancia había 25 pasos antes del cambio.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'dima_bug_staging'
  AND TABLE_NAME = 'se_ejecucion_paso'
  AND COLUMN_NAME IN ('contrato_version','contrato_orden','contrato_instruccion',
                     'contrato_critico','contrato_modo','estado_detallado','revision')
ORDER BY COLUMN_NAME;

SELECT COUNT(*) AS pasos_conservados FROM se_ejecucion_paso;
SELECT contrato_version,contrato_orden,contrato_instruccion,contrato_critico,
       contrato_modo,estado_detallado,revision
FROM se_ejecucion_paso LIMIT 0;
