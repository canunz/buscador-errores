-- SOLO LECTURA. Ejecutar en la misma conexión/esquema que utiliza el backend.
-- No muestra contraseñas ni modifica datos o estructura.
SELECT DATABASE() AS esquema_actual, VERSION() AS version_mysql;

SELECT esperado.columna,
       CASE WHEN real.COLUMN_NAME IS NULL THEN 'FALTA' ELSE 'PRESENTE' END AS estado,
       real.COLUMN_TYPE, real.IS_NULLABLE, real.COLUMN_DEFAULT
FROM (
    SELECT 'contrato_version' AS columna
    UNION ALL SELECT 'contrato_orden'
    UNION ALL SELECT 'contrato_instruccion'
    UNION ALL SELECT 'contrato_critico'
    UNION ALL SELECT 'contrato_modo'
    UNION ALL SELECT 'estado_detallado'
    UNION ALL SELECT 'revision'
) AS esperado
LEFT JOIN INFORMATION_SCHEMA.COLUMNS AS real
    ON real.TABLE_SCHEMA = DATABASE()
   AND real.TABLE_NAME = 'se_ejecucion_paso'
   AND real.COLUMN_NAME = esperado.columna
ORDER BY esperado.columna;

SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('se_ejecucion_paso', 'u_sucursal');

-- Si u_sucursal existe, revisar también sus columnas e índice antes de crearla.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'u_sucursal'
ORDER BY ORDINAL_POSITION;

SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME, SEQ_IN_INDEX
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'u_sucursal'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 01_contrato_paso_mysql.sql añade SIETE columnas (contrato tiene cinco campos).
-- No ejecutar el ALTER completo si alguna columna ya existe: adaptar solo las faltantes.
-- 02_sucursales_mysql.sql crea u_sucursal: no ejecutarlo si esa tabla ya existe.
