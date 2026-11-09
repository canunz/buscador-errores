-- Solo staging local 127.0.0.1:3307, dima_bug_staging. No ejecutar en producción.
-- Aditivo: no cambia soluciones, archivos existentes, procedimientos ni ejecuciones.
USE dima_bug_staging;
CREATE TABLE IF NOT EXISTS co_material_solucion (
 material_id INT NOT NULL AUTO_INCREMENT,
 solucion_id INT NOT NULL,
 nombre VARCHAR(150) NOT NULL,
 tipo VARCHAR(20) NOT NULL,
 url VARCHAR(500) NOT NULL,
 publicado BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(material_id),
 KEY ix_material_solucion(solucion_id),
 CONSTRAINT fk_material_solucion_padre FOREIGN KEY(solucion_id) REFERENCES co_solucion(solucion_id)
) ENGINE=InnoDB;
