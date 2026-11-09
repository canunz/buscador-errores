-- Solo staging autorizado: 127.0.0.1:3307 / dima_bug_staging.
-- Asociación opcional con el catálogo existente. No elimina ni actualiza datos históricos.
USE dima_bug_staging;
ALTER TABLE pr_procedimiento
  ADD COLUMN hardware_id INT NULL,
  ADD CONSTRAINT fk_procedimiento_hardware
    FOREIGN KEY (hardware_id) REFERENCES cl_hardware(hardware_id);
SHOW COLUMNS FROM pr_procedimiento LIKE 'hardware_id';
