-- Snapshot per incident; existing users/roles/catalogs untouched. Apply only after backup and approval.
-- No new table and no FK to mutable catalogs, matching existing cross-module id references.
ALTER TABLE ticket ADD COLUMN sol_nombre_completo VARCHAR(150) NULL;
ALTER TABLE ticket ADD COLUMN sol_sucursal_id INT NULL;
ALTER TABLE ticket ADD COLUMN sol_sucursal_nombre VARCHAR(100) NULL;
ALTER TABLE ticket ADD COLUMN sol_departamento_id INT NULL;
ALTER TABLE ticket ADD COLUMN sol_departamento_nombre VARCHAR(100) NULL;
ALTER TABLE ticket ADD COLUMN sol_contacto VARCHAR(50) NULL;
ALTER TABLE ticket ADD COLUMN sol_equipo VARCHAR(100) NULL;
ALTER TABLE ticket ADD COLUMN sol_codigo_usuario VARCHAR(80) NULL;
ALTER TABLE ticket ADD CONSTRAINT ck_ticket_sol_datos CHECK (
    (sol_nombre_completo IS NULL AND sol_sucursal_id IS NULL AND sol_sucursal_nombre IS NULL
     AND sol_departamento_id IS NULL AND sol_departamento_nombre IS NULL AND sol_contacto IS NULL
     AND sol_equipo IS NULL AND sol_codigo_usuario IS NULL)
    OR (sol_nombre_completo IS NOT NULL AND CHAR_LENGTH(TRIM(sol_nombre_completo)) > 0
        AND sol_sucursal_id IS NOT NULL AND sol_sucursal_id > 0 AND sol_sucursal_nombre IS NOT NULL
        AND sol_departamento_id IS NOT NULL AND sol_departamento_id > 0 AND sol_departamento_nombre IS NOT NULL
        AND sol_contacto IS NOT NULL AND CHAR_LENGTH(TRIM(sol_contacto)) > 0)
);
