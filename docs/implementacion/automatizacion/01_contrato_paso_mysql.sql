-- BORRADOR DE MIGRACIÓN ADITIVA. NO EJECUTADO. NO CARGAR EN PRODUCCIÓN.
-- Confirmar versión de MySQL, esquema, respaldo y columnas preexistentes antes de aplicar.
-- No está en db/migration: requiere revisión y ensayo en MySQL aislado.
-- Esta fase no crea datos de sucursales, solicitudes, OAuth o evidencias.
ALTER TABLE se_ejecucion_paso
    ADD COLUMN contrato_version INT NULL,
    ADD COLUMN contrato_orden INT NULL,
    ADD COLUMN contrato_instruccion TEXT NULL,
    ADD COLUMN contrato_critico BOOLEAN NULL,
    ADD COLUMN contrato_modo VARCHAR(30) NULL,
    ADD COLUMN estado_detallado VARCHAR(30) NULL,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

-- Los NULL históricos se conservan: no reconstruir snapshots ni evidencias retroactivas.
-- Verificación posterior (solo consulta):
-- SELECT contrato_version, estado_detallado, COUNT(*) FROM se_ejecucion_paso
-- GROUP BY contrato_version, estado_detallado;
-- No revertir eliminando columnas/filas. Evaluar compatibilidad con la aplicación anterior.
