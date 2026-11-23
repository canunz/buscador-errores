-- PROPUESTA NO EJECUTADA. Admin QA solo tras esquema nuevo y vacio aprobado.
USE `dima_bug_qa_mvp`;
-- Fila de coordinacion requerida por el bloqueo organizacional, no un usuario/contacto real.
INSERT INTO u_organizacion_control(control_id) VALUES(1);
-- No insertar cuenta OAuth activa, tokens, correos, usuarios o datos reales.
-- Roles/usuarios y catalogos sinteticos: fase de fixtures independiente.
