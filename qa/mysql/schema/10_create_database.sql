-- PROPUESTA NO EJECUTADA. Bootstrap root local solo tras 00_preflight y gate QA.
-- Comprobacion previa visible: debe ser 0. El ejecutor futuro debe abortar si no lo es.
SELECT COUNT(*) AS schema_already_exists
FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'dima_bug_qa_mvp';
-- Sin IF NOT EXISTS: un esquema preexistente causa error en vez de reutilizarlo.
-- Ejecutar este archivo solo, sin --force; no concatenar archivos tras errores.
CREATE DATABASE `dima_bug_qa_mvp` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
