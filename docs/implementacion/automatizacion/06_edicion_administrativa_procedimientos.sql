-- Solo staging autorizado mediante 127.0.0.1:3307 / dima_bug_staging.
-- Aditivo: conserva procedimientos, pasos, materiales, ejecuciones y evidencias.
USE dima_bug_staging;
ALTER TABLE pr_procedimiento ADD COLUMN datos_editoriales_json TEXT NULL;
CREATE TABLE pr_auditoria_procedimiento (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  procedimiento_id INT NOT NULL,
  actor_id INT NOT NULL,
  fecha TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  accion VARCHAR(40) NOT NULL,
  antes_json LONGTEXT NULL,
  despues_json LONGTEXT NULL,
  INDEX ix_auditoria_procedimiento_fecha (procedimiento_id, fecha),
  CONSTRAINT fk_auditoria_procedimiento FOREIGN KEY (procedimiento_id) REFERENCES pr_procedimiento(procedimiento_id),
  CONSTRAINT fk_auditoria_procedimiento_actor FOREIGN KEY (actor_id) REFERENCES u_usuario(usuario_id)
);
SHOW COLUMNS FROM pr_procedimiento LIKE 'datos_editoriales_json';
SHOW CREATE TABLE pr_auditoria_procedimiento;
