# Esquema y privilegios QA: propuesta para aprobacion, NO ejecutada

## Evidencia y limites

Version de trabajo: rama integracion-final-uat, fuentes locales y manifiesto del candidato; no implica equivalencia con staging. Se identifican 41 tablas @Entity, dos tablas de colecciones (cl_hardware_sistema, ticket_ticket_etiqueta) y una proyeccion JDBC (co_conocimiento_busqueda): **44 tablas**. `schema_inventory.json` enlaza cada entidad con su ruta y SHA-256 y registra las fuentes DDL reutilizadas. No se consulta MySQL para descubrir tablas: la existencia actual del esquema y de cuentas queda pendiente del preflight SQL autorizado.

El repositorio NO contiene un DDL inicial completo ni una cadena unica Flyway/Liquibase. `20_schema_final.sql` es una reconstruccion propuesta exclusivamente para una base QA NUEVA y VACIA, combinando anotaciones actuales, consultas nativas y DDL historico. No es un dump, ni una reproduccion certificada de staging. No se reutilizaron datos ni configuraciones remotas.

Grupos sin solapamientos: identidad/organizacion/codigos y cola (9 tablas), catalogos (5), conocimiento/busqueda (10), procedimientos (3), ejecuciones/evidencias/resultados (6), OAuth/prueba Gmail (5) y tickets (6): total 44.

Requisitos adicionales al mapeo JPA: usuario_ultimo_ingreso (UPDATE nativo), FULLTEXT(contenido_busqueda) para MATCH AGAINST, campos @Embedded de ContratoPaso y tablas de colecciones. Fila u_organizacion_control(1) necesaria para bloqueo organizacional. No se precargan OAuth ACTIVA, tokens, usuarios, contrasenas, correos ni filas reales.

## Orden de migraciones historicas y estrategia

Ruta recomendada para QA nueva: crear el esquema consolidado final UNA vez; no ejecutar encima las migraciones ya incorporadas. Conservar trazabilidad, sin inventar un historial Flyway aplicado.

Si en el futuro se ensaya una actualizacion incremental, requiere una linea base anterior real/autorizada y preflight columna por columna. Orden de dependencias (no cronologia inventada para los archivos sin fecha):

1. Base: u_rol -> u_usuario; catalogos antes de co_conocimiento; conocimiento/pruebas antes de asociaciones; usuario antes de procedimientos/ejecuciones. Se reconstruyen en el bloque CORE del generador; no existe migracion original para toda esta base.
2. `V20260925_01`: enum ELIMINADO, requiere co_conocimiento. `V20261013_01`: ampliacion co_solucion, requiere tabla previa. `V20261014_01`: versiones de departamento/contacto y control(1), requiere organizacion base. Independientes entre si salvo disponibilidad de tablas.
3. Automatizacion `01_contrato_paso_mysql` requiere se_ejecucion_paso; `02_sucursales_mysql` crea u_sucursal. `03_flujo_evidencias_desarrollo` requiere base, contrato y sucursales. Su CREATE IF NOT EXISTS u_sucursal se solapa con 02: elegir una sola creacion revisada. `04_modalidades` despues de 03/01; `05_directorio` despues de sucursal; `06_edicion` despues de pr_procedimiento/usuario; `07_hardware` despues de procedimiento/hardware; `08_correo_sin_sucursal` despues de 03. `09_gmail` crea almacenamiento desconectado; `10_prueba_gmail` crea pruebas; `11_materiales` requiere co_solucion. Las ramas 05/06/07/09/10/11 no tienen que ejecutarse una tras otra si sus dependencias ya existen.
4. `V20261017_01` tickets antes de `V20261017_02` adjuntos/nivel. Este ultimo contiene DROP TABLE ticket_adjunto: NO ejecutarlo sin adaptar/revisar. La consolidacion incorpora solo su CREATE final y nivel, sin DROP.
5. `V20261031_01` codigos requiere u_usuario; `V20261101_01` ultimo ingreso requiere u_usuario; `docs/04_Base_Datos/V20261110_01__correo_codigo.sql` requiere funcionalmente codigo_acceso. Se consolida su estructura, sin generar mensajes.
6. Proyeccion JDBC de busqueda e indice FULLTEXT: DDL propuesto nuevo; no hay migracion historica completa que pueda certificarse desde el repositorio.

`00_verificar_esquema_mysql.sql` es diagnostico, no migracion. `APLICAR_REPARACION_STAGING.sql` queda excluido. Los scripts con USE dima_bug_staging no son ejecutables QA: revisar/adaptar antes de cualquier ensayo incremental. No deshabilitar foreign_key_checks para forzar un orden.

## Archivos y secuencia futura (cada ejecucion SQL requiere autorizacion)

1. Gate externo de identidad: contexto desktop-linux local, contenedor/imagen/UUID/volumen QA coincidentes con respaldo, proxy esperado y secreto root con ACL. Confirmar respaldo recuperable. Capturar solo metadatos, no variables ni logs completos. No ejecutar backend/frontend.
2. `00_preflight.sql`, root por socket: MySQL 8.0.46; esquema ausente; usuarios dimabug_qa_schema/app ausentes en TODOS los hosts; general_log y binlog apagados, revisar cualquier auditoria de consultas antes de manejar secretos; controles de automatizacion siguen deshabilitados. Estas condiciones todavia NO se comprobaron mediante SQL. No leer authentication_string ni secretos.
3. `10_create_database.sql`, root local. El ejecutor debera exigir COUNT=0 y abortar antes de CREATE si no lo es. CREATE sin IF NOT EXISTS protege contra reutilizacion concurrente. Cliente sin --force, comprobar salida de CADA archivo antes del siguiente; no concatenar archivos tras un error. No hay runner SQL implementado en esta preparacion.
4. `15_create_users.sql.template`, root local. Sustituir placeholders en memoria solo tras aprobacion; ambos usuarios nacen bloqueados. `18_admin_grants.sql` otorga privilegios por tabla (incluido CREATE para preparar tablas todavia ausentes). Desbloquear SOLO administrador con primera sentencia de `45_unlock.sql.template`.
5. Administrador QA por socket: `20_schema_final.sql`, luego `25_structural_rows.sql`. DDL MySQL realiza commits implicitos: no afirmar que una transaccion revierte 44 CREATE. Cualquier error detiene la fase; nunca reparar automaticamente con DROP o IF NOT EXISTS.
6. Root: `40_app_grants.sql.template` DESPUES de las 44 tablas. `50_verify_privileges.sql.template` y comparacion exacta con privileges.json, incluidos roles obligatorios, PROXY, grants por columnas/rutinas y ausencia de concesiones extra. Desbloquear app solo despues de gates de TLS, origen y modelo; bloquear admin fuera de mantenimiento. Desbloqueos no se automatizan ahora.
7. `60_verify_model.sql`: validar inventario completo, tipos/nullability, indices/FK/enum/colaciones; comparar con entidades, no solo contar. Posterior Hibernate validate y casos MySQL requieren autorizacion de ejecucion distinta. Ninguna cuenta QA arranca Spring ni envia correos por existir.

## Cuentas y privilegios

- Bootstrap root ya existente: exclusivamente socket local, custodia previa, fuera del backend. Crea BD/cuentas/grants; no habilitar root remoto ni cuenta root via proxy.
- `dimabug_qa_schema`@localhost: SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES solo sobre las 44 tablas explicitas de dima_bug_qa_mvp. Sin DROP, CREATE USER, roles, grant option, FILE, PROCESS, SUPER, eventos, triggers, rutinas ni permisos globales. Nuevas tablas o una migracion que necesite DROP exigen una ampliacion puntual revisada, no un GRANT ALL. Cuenta de mantenimiento, no credencial del backend.
- `dimabug_qa_app`@IP-interna-exacta-del-proxy: S/I/U en tablas operativas, DELETE solamente donde hay borrados explicitos o reemplazo de colecciones Hibernate, S/I para auditorias/evidencia/historial/resultados, solo SELECT para rol/control y gm_*. Detalle autoritativo por tabla en privileges.json y 40_app_grants. La allowlist sigue siendo una propuesta pendiente de pruebas positivas de todas las funciones; no ampliar automaticamente ante errores.
- gm_* queda sin escritura en el perfil QA que mantiene OAuth/correo deshabilitados. Ensayar OAuth simulado con persistencia puede requerir extension posterior S/I/U/D por tabla segun GmailTokenStoreJpa, sin habilitar transporte real. No concederlo silenciosamente por fallar una prueba.
- Concesiones por tabla exacta evitan los comodines `%`/`_` de GRANT a nivel de BD; no se usa dima_bug_qa_mvp.* ni patrones de hosts. No FLUSH PRIVILEGES: CREATE USER/GRANT tienen efecto inmediato.

Credenciales: ya existe custodia QA independiente verificada. NO se leyo ni se genero ninguna en esta etapa. Tras autorizacion se usarian sus entradas schema_password/app_password; no se guardara una plantilla renderizada con valores. El runner futuro debera validar nombres/IP y escapar literales (o usar API soportada) y pasar SQL por canal privado sin logs/argv/historial; no usar sustitucion manual ni contrasena escrita en un comando.

## Origen de conexion y TLS

Observado por metadatos Docker y por el rechazo inicial MySQL 1130 sin autenticacion/SQL: proxy interno **172.21.0.3**, red interna 172.21.0.0/16. No autorizar 127.0.0.1 (es el cliente Windows, no el origen visto por MySQL), ni 172.22.0.2 (interfaz access), ni todo el /16. Administracion usa socket, sin apertura adicional.

La IP del proxy es dinamica. Antes de renderizar CREATE/GRANT/desbloqueo, releer y comparar su identidad/red/origen efectivo. Si cambia tras recreacion, detener el acceso y revisar la cuenta anterior, sin ampliar a %. IP fija es una posible mejora separada que requeriria cambiar configuracion de red; NO se aplica aqui. No mantener cuentas antiguas activas sin revisar.

App propuesta REQUIRE SSL con caching_sha2_password, no mysql_native_password. HAProxy transmite TLS sin terminarlo. Verificar Ssl_cipher no vacio y una prueba negativa de cliente sin TLS. El perfil JDBC actual no configura VERIFY_IDENTITY: cifrado TLS no demuestra por si solo autenticidad del servidor. Definir confianza en la CA QA existente/publica y nombre verificado antes de esa prueba, sin copiar claves privadas ni activar allowPublicKeyRetrieval como atajo. No se modifico configuracion TLS, Java ni credenciales en esta preparacion.

## Pruebas positivas y negativas de privilegios (propuestas, NO ejecutadas)

- Root local: comparar SHOW GRANTS y tablas de privilegios con allowlist; usuarios exactos, sin cuentas con mismo nombre y otro host, roles/globales/columnas/rutinas inesperados ni PROXY. No mostrar hashes ni authentication_string. Metadatos information_schema limitados y algunas vistas de performance_schema pueden ser visibles: no prometer ausencia absoluta de metadatos del servidor.
- Cada cuenta: SELECT DATABASE(),CURRENT_USER(),USER(); admin efectivo @localhost, app @IP del proxy. Comprobar TLS app; no confundir login fallido con prueba de privilegios.
- Root crea SOLO bajo autorizacion un esquema canario local con nombre parecido (p.ej. dimaXbugXqaXmvp) y una tabla vacia. Ese nombre detecta tambien un GRANT accidental con `_` comodin. Admin/app deben obtener 1044/1142 al USE/SELECT/INSERT/CREATE contra ese canario; los intentos negativos pueden escribir si el control falla: usar exclusivamente canario, nunca tablas del sistema ni datos ajenos.
- mysql.user SELECT debe denegarse; no SELECT de secretos para la evidencia. No ejecutar pruebas contra staging/produccion. Su inaccesibilidad se sustenta en aislamiento, URL fija y no reutilizacion, no en intentar estas contrasenas contra DigitalOcean.
- App: consultas y DML permitidos con fixtures sinteticos dentro de transaccion reversible; CREATE/ALTER/DROP/CREATE USER/FILE/GRANT deben estar ausentes en grants. Ensayo activo de DDL solo sobre objeto canario expendible con autorizacion separada: DDL no es reversible por ROLLBACK.
- App no debe UPDATE/DELETE auditorias ni escribir gm_*; admin no debe CREATE TABLE fuera de las 44 ni conceder permisos. Contar como fallo cualquier permiso inesperado, no solo un error HTTP.

## Backup y restauracion QA

No ejecutar estos comandos ahora. Desarrollar posteriormente un ejecutor que tome la credencial en memoria por canal privado y cifre salida; los siguientes son argumentos, SIN contrasenas:

`mysqldump --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --skip-triggers --hex-blob --default-character-set=utf8mb4 dima_bug_qa_mvp`

- Cuenta schema tiene SELECT por tabla; al no haber vistas/triggers/rutinas/eventos no necesita SHOW VIEW/TRIGGER/EVENT. Antes de usar --skip-triggers exigir que no existen tales objetos; si aparecen, abortar y revisar backup/privilegios, no omitirlos silenciosamente. No otorgar PROCESS, RELOAD o privilegios globales por comodidad. --set-gtid-purged=OFF evita la rama de FLUSH con GTID de mysqldump.
- Para solo estructura agregar --no-data. Un dump con datos debe incluir solo fixtures QA. No usar --all-databases, --databases, --routines ni --events; no respaldar mysql.user ni hashes de cuentas como parte del dump de aplicacion. Cuentas se reconstruyen desde plantillas y custodia.
- Congelar cambios DDL durante el dump; single-transaction no protege frente a ALTER/DROP concurrentes. Guardar cifrado fuera de Git, hash de integridad del archivo cifrado y manifiesto de version/44 tablas/conteos sin datos sensibles. Custodia y respaldo portable de claves permanecen separados. No mostrar el dump ni su stderr bruto.
- Restauracion ensayada en instancia/volumen QA temporal independiente, MySQL 8.0.46 misma colacion/charset; mantener el nombre dima_bug_qa_mvp en esa instancia para no ampliar grants actuales. Crear esquema vacio y usuario administrador equivalente en el destino SOLO con autorizacion. Revisar el dump (sin USE/CREATE DATABASE ajenos, DEFINER, mysql, usuarios ni rutas externas), descifrar en memoria e importar por stdin privado. Dumps comunes pueden incluir DROP TABLE: permitirlos solamente en el destino temporal aprobado, nunca importarlos a ciegas en la instancia actual.
- Comparar DDL, FK/indices/FULLTEXT, conteos y checksums de fixtures y ejecutar pruebas funcionales tras autorizacion. Un hash del dump no prueba restaurabilidad. El respaldo fisico pre-proxy existente conserva integridad, pero todavia no fue restaurado en otro servidor.
- Reversion de aplicacion DDL parcial: bloquear acceso QA, conservar evidencia y respaldo, no borrar tablas automaticamente. Restaurar en destino limpio aprobado y validar antes de sustituir. Mantener intactos volumen actual y custodia hasta aceptar recuperacion.

## Riesgos pendientes de aprobar/verificar

Tipos/longitudes de base reconstruidos de anotaciones (defaults JPA VARCHAR255), ENUM nativo para enums base, BOOLEAN frente a BIT/JDBC, DATETIME(6)/TIMESTAMP(6)/Instant y zonas horarias, FK/unique y defaults no disponibles en DDL historico completo, y semantica/tokenizacion FULLTEXT. La consolidacion mantiene FK explicitas en relaciones JPA y DDL fuente; NO inventa cascadas para cada campo entero que parece referencia. Se requiere revisar estas decisiones antes de aplicar y validar tipos en MySQL/Hibernate despues. El plan no altera funcionalidades existentes ni el esquema de staging.

Fuentes: https://dev.mysql.com/doc/refman/8.0/en/grant.html ; https://dev.mysql.com/doc/refman/8.0/en/account-names.html ; https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html ; https://dev.mysql.com/doc/mysql-secure-deployment-guide/8.0/en/secure-deployment-configure-authentication.html
