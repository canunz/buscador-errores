-- PROPUESTO, NO EJECUTADO. Ejecutar solo tras autorizacion y gate externo de identidad QA.
-- Bootstrap por socket dentro del contenedor QA. Nunca mysql --force.
SELECT @@version AS mysql_version, @@server_uuid AS server_uuid,
       @@port AS internal_port, @@hostname AS container_hostname,
       @@global.partial_revokes AS partial_revokes,
       @@global.event_scheduler AS event_scheduler,
       @@global.local_infile AS local_infile,
       @@global.general_log AS general_log,
       @@global.log_bin AS binary_log;
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'dima_bug_qa_mvp';
-- Exigir ausencia para una creacion nueva; si existe, detener y revisar, no reutilizar.
SELECT User, Host, account_locked, plugin
FROM mysql.user WHERE User IN ('dimabug_qa_schema', 'dimabug_qa_app');
-- No seleccionar authentication_string ni tablas con tokens o hashes.
-- Cuenta previa con cualquiera de estos nombres/origenes => detener, no ALTER/REPLACE automatico.
