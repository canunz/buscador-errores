# Estado verificado — 2026-11-12

## Fase posterior autorizada: prueba TLS y privilegios completada

Resultado satisfactorio: 4 positivos DML/lectura sinteticos y 17 negativos denegados. TLS1.3 con CA y pin de certificado verificados en la misma conexion antes de autenticacion; no se declara validacion de hostname para el certificado sin SAN. App desbloqueada solo temporalmente, admin siempre bloqueado. Final: ambas cuentas bloqueadas, cero sesiones app, baseline de filas/estructura/grants intacto, marcador ausente y ninguna cuenta/tabla nueva. Ver ../tls/RESULTADO_PRUEBA_TLS.md y la evidencia sanitizada enlazada alli. No repetir automaticamente --execute. Los apartados siguientes conservan el historial de preparacion.

## Fase posterior autorizada: grants de aplicacion aplicados

Aplicadas las 44 sentencias de 40_app_grants.sql.template a dimabug_qa_app@172.21.0.3 tras reconfirmar la IP interna del proxy. Verificadas las 116 concesiones efectivas frente a privileges.json: coincidencia exacta de cuenta/origen/base/tabla/privilegio e IS_GRANTABLE=NO. Sin DDL ni permisos globales/de esquema/de columna/de rutinas, roles, PROXY o GRANT OPTION. Las cinco tablas gm_* permanecen solo SELECT para app; no se habilito OAuth ni correo.

Ambas cuentas continúan ACCOUNT LOCK; app mantiene REQUIRE SSL y limite de cuatro conexiones. Permisos de administrador sin cambios (352 concesiones por tabla). Esquema de 44 tablas contrastado antes de grants: unica fila control(1); no se modificaron tablas/datos en esta fase. No se leyeron contrasenas de la custodia, generaron credenciales, ejecutaron desbloqueos ni iniciaron aplicaciones.

Herramienta: apply_app_grants.py, sin --apply solo preflight de lectura; exige app sin grants, por tanto NO repetir --apply. Ante concesiones parciales revisar estado, no repetir ni ampliar automaticamente.

Pendientes de autorizacion: prueba TLS/autenticacion con desbloqueo temporal y cierre, pruebas positivas/negativas de permisos, fixtures sinteticos, Hibernate/funcional y ensayo de restauracion. Los registros siguientes son historicos de fases anteriores.

## Fase posterior autorizada: esquema consolidado aplicado

Aplicados con dimabug_qa_schema@localhost los 44 CREATE TABLE de 20_schema_final.sql y la unica fila u_organizacion_control(1) de 25_structural_rows.sql. Administrador desbloqueado temporalmente y bloqueado otra vez al finalizar; ambas cuentas ACCOUNT LOCK. App sigue sin permisos funcionales. No se generaron/rotaron credenciales, no se ejecutaron los ALTER/DROP historicos ni se insertaron usuarios, roles, contactos, mensajes o tokens.

Verificacion real frente al DDL propuesto: inventario exacto de 44 tablas, 307 columnas (tipos/nullability/autoincremento), 34 pares de columnas FK, claves primarias/unicas, InnoDB/utf8mb4_unicode_ci y FULLTEXT(contenido_busqueda). Conteos de todas las tablas: una sola fila total, control_id=1. Esto NO certifica compatibilidad con Hibernate ni funcionalidad de la aplicacion: no se inicio Spring/Angular ni se ejecutaron pruebas de negocio. Los defaults y reglas completas de FK requieren la revision adicional pertinente; no confundir la comparacion de columnas FK con una prueba de todos sus comportamientos.

Antes del DDL se guardo checkpoint de la base vacia y del script de grants en qa/runtime/backups/pre-schema-* con ACL usuario actual/SYSTEM y exclusion Git. Complementa el respaldo fisico anterior y la custodia existente; no equivale a un ensayo de restauracion.

Primer intento: fallo 1045 al autenticar admin antes de crear tablas, cierre confirmo ambas cuentas bloqueadas. Diagnostico sintetico demostro que CRLF agregaba CR a la contrasena transmitida por stdin. Se fijo LF explicitamente, se comprobo longitud exacta con datos publicos y se revalidaron identidad/base vacia/permisos antes de continuar. No se modificaron contrasenas ni grants para resolverlo. Segundo intento: 44 tablas aplicadas, verificacion satisfactoria y relock confirmado.

Herramientas: Apply-QaMysqlSchema.ps1 (sin -Apply solo preflight) y verify_schema_runtime.py (lectura). NO repetir -Apply sobre este esquema: exige base vacia. No borrar tablas para repetir pruebas ni ejecutar bootstrap.

Pendiente de autorizacion: asignar permisos a app, desbloqueo y prueba TLS autenticada, fixtures sinteticos, validacion Hibernate/funcional y ensayo de backup/restauracion. Los registros siguientes son historicos y describen fases anteriores.

## Fase posterior autorizada: grants de administrador aplicados

Aplicadas las 44 sentencias exactas de 18_admin_grants.sql a dimabug_qa_schema@localhost. Comparadas las 352 concesiones efectivas (8 privilegios por cada una de 44 tablas) con la allowlist: coincidencia exacta. Sin permisos globales, de esquema, de columna, de rutinas, roles, PROXY, DROP ni GRANT OPTION. La aplicacion sigue sin permisos funcionales. Ambas cuentas conservan ACCOUNT LOCK y la base sigue sin tablas. No se ejecutaron desbloqueos, DDL de tablas ni migraciones.

Herramienta: apply_admin_grants.py; sin --apply solo preflight de lectura. No repetir --apply sobre estos grants. La ausencia de permisos es una precondicion deliberada; ante un estado parcial revisar, nunca ampliar ni revertir automaticamente.

Pendiente de autorizacion: desbloqueo del administrador, aplicacion del esquema consolidado y fila de control, grants/desbloqueo de app y pruebas autenticadas. Los siguientes parrafos conservan el registro de la fase anterior a la asignacion de grants.

Aplicadas exclusivamente las autorizaciones de preflight, creacion de base vacia y creacion de dos cuentas bloqueadas.

- Instancia QA MySQL 8.0.46, contexto local desktop-linux y volumen QA verificados.
- Base dima_bug_qa_mvp existente, utf8mb4/utf8mb4_unicode_ci, cero tablas.
- dimabug_qa_schema@localhost: caching_sha2_password, ACCOUNT LOCK, max_user_connections=2.
- dimabug_qa_app@172.21.0.3: caching_sha2_password, ACCOUNT LOCK, REQUIRE SSL, max_user_connections=4.
- Ambas contrasenas provienen de la custodia DPAPI QA preexistente; no se generaron ni rotaron. Plantilla renderizada exclusivamente en memoria, enviada por stdin al cliente MySQL via socket local. No se guardo SQL con secretos.
- Verificados cero permisos funcionales globales/de esquema/de tabla/de columna, cero roles asignados y cero privilegios PROXY. mandatory_roles vacio. No se ejecutaron GRANT ni desbloqueos.
- General log, binlog y slow query log apagados al crear cuentas. Sin plugins activos de auditoria adicionales; sha2_cache_cleaner y mysqlx_cache_cleaner son auxiliares internos de cache, no registradores de consultas. No se modifico ninguna configuracion de logging ni plugins.
- No se ejecutaron DDL de tablas, migraciones, fixtures ni arranque de Angular/Spring. No se modificaron redes, volumenes, configuracion ni datos de staging/produccion.

Herramienta utilizada: Create-QaMysqlAccounts.ps1; sin -Create solo SELECT/lecturas. Con -Create exige cuentas ausentes y base vacia; NO volver a ejecutarlo sobre estas cuentas. Si falla una fase, consultar estado por lectura, nunca borrar o regenerar automaticamente.

Pendientes, no autorizados por la creacion de cuentas: 18_admin_grants.sql, desbloqueos, esquema de 44 tablas, fila de control, permisos de app y pruebas autenticadas. Mantener ambas cuentas bloqueadas hasta la aprobacion de la fase correspondiente.

Referencias para la revision del control de plugins:
- https://dev.mysql.com/doc/refman/8.0/en/caching-sha2-pluggable-authentication.html
- https://dev.mysql.com/doc/refman/8.0/en/x-plugin-sha2-cache-plugin.html
