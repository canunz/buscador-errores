-- PROPUESTO NO EJECUTADO. Con cuenta QA y BD explicitamente seleccionada.
SELECT DATABASE() AS selected_schema, CURRENT_USER() AS effective_account, USER() AS connection_origin;
SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES
WHERE TABLE_SCHEMA='dima_bug_qa_mvp' ORDER BY TABLE_NAME;
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dima_bug_qa_mvp'
ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT TABLE_NAME,INDEX_NAME,INDEX_TYPE,COLUMN_NAME,SEQ_IN_INDEX
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dima_bug_qa_mvp'
ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;
-- Exigir FULLTEXT(contenido_busqueda), 44 tablas y contrato de columnas/constraints revisado.
SHOW SESSION STATUS LIKE 'Ssl_cipher';
-- Exigir cipher no vacio para app atraves del proxy. Admin usa socket local, no proxy.
