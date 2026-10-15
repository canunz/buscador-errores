-- QA-012: archivo revisable, NO ejecutar sin aprobación y preflight de esquema/MySQL/InnoDB.
-- Comprobar que las columnas/tabla no existen. No altera datos ni FK existentes.
-- INSTANT falla si no es compatible; no sustituir automáticamente por COPY/INPLACE.
SET @qa012_lock_wait_previo = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 5;
ALTER TABLE u_departamento ADD COLUMN departamento_version BIGINT NOT NULL DEFAULT 0, ALGORITHM=INSTANT;
ALTER TABLE u_departamento_contacto ADD COLUMN contacto_version BIGINT NOT NULL DEFAULT 0, ALGORITHM=INSTANT;
CREATE TABLE u_organizacion_control (control_id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO u_organizacion_control (control_id) VALUES (1);
SET SESSION lock_wait_timeout = @qa012_lock_wait_previo;
-- Sin índice UNIQUE sobre tablas existentes: requeriría preflight de duplicados/collation y aprobación separada.
