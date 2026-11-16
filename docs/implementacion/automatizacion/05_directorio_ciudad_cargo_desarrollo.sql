-- Exclusivamente staging local autorizado: 127.0.0.1:3307 / dima_bug_staging.
-- Aditivo: no modifica responsables, procedimientos ni ejecuciones existentes.
USE dima_bug_staging;
ALTER TABLE u_sucursal ADD COLUMN ciudad VARCHAR(100) NULL, ADD COLUMN responsable_cargo VARCHAR(100) NULL;
SHOW COLUMNS FROM u_sucursal;
