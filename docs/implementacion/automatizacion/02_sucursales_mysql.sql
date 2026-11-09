-- BORRADOR ADITIVO. NO EJECUTADO. Revisar el esquema y ensayar en MySQL aislado.
-- Catálogo nuevo autorizado por el usuario; no sustituye departamentos ni responsables históricos.
CREATE TABLE u_sucursal (
    sucursal_id INT NOT NULL AUTO_INCREMENT,
    sucursal_codigo VARCHAR(30) NOT NULL,
    sucursal_nombre VARCHAR(100) NOT NULL,
    responsable_nombre VARCHAR(100) NOT NULL,
    responsable_email VARCHAR(120) NOT NULL,
    sucursal_activa BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (sucursal_id),
    CONSTRAINT uk_sucursal_codigo UNIQUE (sucursal_codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- No se incluyen correos, sucursales o credenciales reales.
