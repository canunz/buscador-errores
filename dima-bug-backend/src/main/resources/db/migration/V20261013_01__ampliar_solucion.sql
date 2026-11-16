-- QA-009. Aplicación manual autorizada por separado; comprobar columnas y soporte INSTANT antes.
-- No ejecutar automáticamente. Las columnas se agregan al final y conservan datos/FK.
ALTER TABLE co_solucion
    ADD COLUMN solucion_titulo VARCHAR(150) NULL,
    ADD COLUMN solucion_estado TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN solucion_autoservicio TINYINT(1) NOT NULL DEFAULT 0,
    ALGORITHM=INSTANT;
