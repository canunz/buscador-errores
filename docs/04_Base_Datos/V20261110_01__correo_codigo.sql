-- EXPANDIR, NO EJECUTAR AUTOMÁTICAMENTE. Revisar/aplicar mediante cambio DBA autorizado.
-- No modifica usuarios, códigos, cuenta OAuth ni tokens existentes.
CREATE TABLE u_correo_codigo (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    codigo_id BIGINT NOT NULL,
    contenido_cifrado LONGTEXT NULL,
    estado VARCHAR(24) NOT NULL,
    intentos INT NOT NULL DEFAULT 0,
    expira DATETIME(6) NOT NULL,
    proximo_intento DATETIME(6) NOT NULL,
    actualizado_en DATETIME(6) NOT NULL,
    referencia VARCHAR(80) NULL,
    CONSTRAINT uq_correo_codigo UNIQUE (codigo_id),
    INDEX idx_correo_codigo_pendiente (estado, proximo_intento)
);
