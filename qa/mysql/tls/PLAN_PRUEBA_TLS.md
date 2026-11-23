# Prueba TLS y privilegios QA — propuesta, NO ejecutada

Actualizacion posterior: procedimiento aprobado y ejecutado el 2026-11-12. Resultado y limites en RESULTADO_PRUEBA_TLS.md. El texto siguiente conserva el plan previo; no es una instruccion para repetir automaticamente la prueba.

## Comprobaciones efectuadas sin SQL ni autenticacion

El 12 de noviembre de 2026 se leyeron solo metadatos Docker, configuracion HAProxy y certificados PUBLICOS ca.pem/server-cert.pem. No se leyeron claves privadas ni credenciales de aplicacion. Se negociaron conexiones TLS desde Windows a 127.0.0.1:13318 usando el saludo MySQL y SSLRequest, sin enviar el paquete de autenticacion ni comandos SQL.

- Proxy en mode tcp, sin terminacion TLS: la negociacion llega a MySQL.
- TLSv1.3, TLS_AES_256_GCM_SHA384.
- Cadena y vigencia verificadas contra la CA publica de esta instancia.
- Certificado servido coincide con server-cert.pem obtenido por el canal Docker local.
- Vigencia: 2026-11-12 13:06:58 UTC hasta 2036-11-09 13:06:58 UTC.
- Sin subjectAltName. La validacion estandar de identidad para 127.0.0.1 falla (error X509 64). No se declara VERIFY_IDENTITY satisfactorio.
- Huella SHA-256 de la hoja: afee3b9f5f0b8ad162198bfe96b3b5f5be45499afa7f69acb1d89a9a812d7b9e. Es metadato publico, no una clave.

No se comprobo hoy account_locked ni grants mediante SQL porque no estan autorizadas consultas en esta preparacion. El ultimo estado documentado es ambas cuentas bloqueadas, admin con 352 concesiones por tabla y app con 116; revalidarlo sera obligatorio antes de cualquier desbloqueo.

## Decision TLS propuesta para esta prueba local

Recomendacion sin cambiar certificados, claves o contenedores: CA exacta de esta instancia MAS pin de certificado hoja exacto. El pin se comprueba sobre LA MISMA conexion TLS que posteriormente autentica, antes de enviar usuario/respuesta de contrasena. No basta un sondeo previo seguido de otra conexion VERIFY_CA; eso deja una ventana entre comprobacion y autenticacion.

TLS minimo 1.2, certificado obligatorio y cadena/vigencia validas. No CERT_NONE, rejectUnauthorized=false, fallback sin TLS, recuperacion automatica de claves RSA ni confianza global de Windows. La comprobacion de nombre se reemplaza de forma explicita y limitada a este cliente QA por el pin, no se afirma que el nombre coincida. Leer la CA/hoja por Docker local exige primero verificar contexto, imagen, volumen, UUID y certificado esperado; un cambio de pin requiere parar y revisar, no actualizarlo silenciosamente.

Alternativa si se exige VERIFY_IDENTITY convencional: provisionar un certificado QA con SAN IP:127.0.0.1 o un nombre QA verificable, su CA de confianza y despliegue/reinicio controlados. Implica autorizacion distinta para nuevas claves/configuracion; no esta incluido ni se hizo ahora.

Cliente propuesto, todavia NO instalado: PyMySQL 1.2.4 en entorno Python aislado bajo qa/runtime, sin modificar Python global, Angular ni el prototipo OCR. Python local 3.12 cumple requires-python>=3.9. Wheel publico pymysql-1.2.4-py3-none-any.whl, SHA-256 e169bed906168fffd0088925f45b3df024c2775c6fd128da0df773d47c458840. Fijar hash y version, sin dependencias opcionales RSA ni red en la ejecucion de la prueba, salvo 127.0.0.1:13318 y Docker local. El ejecutor debe verificar la API de ESA version y que SSLContext.wrap_socket verifique cadena y pin antes de la autenticacion; abortar antes de credenciales si el servidor no ofrece TLS. No implementar autenticacion MySQL propia.

Antes de desbloquear, probar el cliente y guardian con datos simulados: CA ausente/no confiable, pin incorrecto, servidor sin TLS y fallo de certificado deben abortar sin emitir autenticacion. Probar cierre del guardian contra mocks, no desbloquear para probar el mecanismo. Solo luego habilitar el caso autenticado.

## Alcance y cuentas

Desbloqueo temporal UNICAMENTE de dimabug_qa_app@172.21.0.3. Windows conecta a 127.0.0.1:13318; MySQL ve la interfaz interna del proxy 172.21.0.3. Reconfirmar identidad/IP inmediatamente antes; no ampliar a % si cambia.

dimabug_qa_schema@localhost permanece bloqueado. Root@localhost se usa solo por el socket del contenedor para preflight, bloqueo y cierre de sesiones. No crear mas cuentas, dar mas grants, generar/rotar contrasenas ni habilitar root por proxy. App usa la entrada DPAPI ya custodiada en memoria; no argv, SQL renderizado en disco, variables persistentes, logs ni historial.

## Procedimiento posterior exacto, requiere aprobacion

1. Preparar y revisar ejecutor acotado, cliente fijado y guardian independiente. No instalar un servicio Windows persistente. El proceso guardian se iniciara oculto, con plazo monotono de 180 s y confirmacion LISTO antes de permitir UNLOCK. Su unica accion de escritura autorizable es bloquear la cuenta app y cerrar sus sesiones verificadas. Disponer ademas de una terminal root local para cierre manual.
2. Preflight SQL por root local: UUID/imagen/volumen QA, charset/collation, 44 tablas, fila control(1) unica, ambas cuentas bloqueadas, grants exactos, sin roles/PROXY/privilegios globales, REQUIRE SSL conservado, sin rutinas/triggers/eventos que alteren los ensayos. Verificar que no hay otras sesiones app ni escritores/DDL concurrentes. No arrancar Angular/Spring ni automatizaciones. Registrar baseline de inventario/grants/conteos y filas de control, sin leer tokens, hashes de contrasenas o datos personales. Confirmar logging sensible deshabilitado; los auxiliares cache_cleaner no son registradores de consultas.
3. Verificar ausencia de la clave sintetica 2000000001 en co_conocimiento_busqueda; confirmar que esa tabla es InnoDB, no tiene FK ni triggers y sus datos de partida son los esperados. Confirmar que cl_frecuencia existe y que __qa_tls_objeto_inexistente__ y la cuenta __qa_tls_destino_inexistente__@localhost NO existen. Si alguno existe, parar: no borrarlo ni elegir destinos sobre la marcha. Los identificadores de prueba solo son datos sinteticos, no cuentas que se vayan a crear.
4. Leer certificados publicos y repetir CA/pin vigentes. Armar guardian y reconfirmar LISTO. Root ejecutaria exclusivamente:

```sql
ALTER USER 'dimabug_qa_app'@'172.21.0.3' ACCOUNT UNLOCK;
```

5. Conexion de app, maximo una a la vez, sin reconexion/reintentos automaticos: localhost fijo, TLS obligatorio, CA+pin antes de autenticacion, timeouts de conexion/lectura/escritura 5 s, plazo total del ejecutor <=120 s, local_infile deshabilitado y sin multiples sentencias habilitadas. Tras login verificar DATABASE(), CURRENT_USER(), USER(), @@server_uuid y Ssl_cipher: cuenta/origen/instancia exactos y cipher no vacio. Guardar CONNECTION_ID para el cierre. No basta un HTTP200 ni una conexion cifrada sin identidad validada.
6. Ejecutar positivos en transaccion, luego ROLLBACK y cerrar esa conexion. Solo continuar a negativos si rollback/ausencia del marcador se verifican. Ningun DDL o GRANT comparte conexion con DML pendiente.
7. Ejecutar negativos en otra conexion autenticada, por sentencia, con lista cerrada de comandos y destinos. Exigir error de autorizacion especifico del comando: no considerar satisfactorio un error de sintaxis, cuenta inexistente, tabla inexistente o autenticacion fallida. Cualquier exito o error diferente detiene la prueba como fallo/inconclusa; no probar un comando mas peligroso para forzar una conclusion.
8. Cierre en finally Y por guardian independiente: bloquear app mediante root, cerrar las conexiones app de la prueba y, si hay sesiones app inesperadas durante esta ventana exclusiva, detenerse y cerrarlas por identidad verificada. Esperar rollback por desconexion y comprobar cierre efectivo. ACCOUNT LOCK no sustituye a cerrar conexiones ya establecidas.
9. Verificar con root: ambas cuentas bloqueadas; cero sesiones app; mismos grants, usuarios, tablas, columnas, indices y triggers; Gmail sin cambios; marcador ausente; control_id=1; ningun objeto/cuenta canario creado. Confirmar nuevo intento autenticado TLS de app rechazado por ACCOUNT LOCK, sin imprimir respuesta con secretos. El guardian se desarma solo DESPUES del cierre confirmado. No borrar nada automaticamente si la comprobacion falla.

## Positivos: datos exclusivamente sinteticos

Tabla autorizada co_conocimiento_busqueda, clave fija 2000000001 (preflight exige ausencia). No se modifica conocimiento real ni se crea un procedimiento, mensaje o codigo de acceso. El ejecutor parametriza los valores; el siguiente es el contenido revisable de la prueba, NO ejecutado:

```sql
START TRANSACTION;
SELECT control_id FROM dima_bug_qa_mvp.u_organizacion_control WHERE control_id=1;
INSERT INTO dima_bug_qa_mvp.co_conocimiento_busqueda(conocimiento_id,contenido_busqueda)
VALUES(2000000001,'QA_TLS_SINTETICO_A');
SELECT contenido_busqueda FROM dima_bug_qa_mvp.co_conocimiento_busqueda WHERE conocimiento_id=2000000001;
UPDATE dima_bug_qa_mvp.co_conocimiento_busqueda
SET contenido_busqueda='QA_TLS_SINTETICO_B' WHERE conocimiento_id=2000000001;
SELECT contenido_busqueda FROM dima_bug_qa_mvp.co_conocimiento_busqueda WHERE conocimiento_id=2000000001;
DELETE FROM dima_bug_qa_mvp.co_conocimiento_busqueda WHERE conocimiento_id=2000000001;
SELECT COUNT(*) FROM dima_bug_qa_mvp.co_conocimiento_busqueda WHERE conocimiento_id=2000000001;
ROLLBACK;
```

Esperados: SELECT control=1; INSERT/UPDATE/DELETE afectan una fila cada uno; SELECT devuelve A y luego B; conteo final y posterior a rollback=0. Ante cualquier excepcion se intenta rollback y se cierra conexion; guardian mata la sesion si no responde. No COMMIT. ROLLBACK protege filas transaccionales, no garantiza revertir todos los contadores/metadatos internos de InnoDB/FULLTEXT.

## Negativos sin cambios de negocio o permisos

La proteccion NO se basa en ROLLBACK de DDL/GRANT: tienen commits implicitos. Cada comando debe ser no mutante aun si los permisos estuvieran mal, bajo precondiciones verificadas y ausencia de DDL concurrente. Contadores operativos, estadisticas o mensajes de error del servidor pueden cambiar al realizar cualquier intento; no se promete inmovilidad fisica del servidor.

| Control | Sentencia propuesta | Condicion que evita cambios aun con permisos excesivos |
|---|---|---|
| CREATE TABLE | CREATE TABLE dima_bug_qa_mvp.cl_frecuencia(qa_col INT) | Tabla ya existente; sin IF NOT EXISTS. Si devuelve tabla existente en vez de denegacion, prueba falla/inconclusa |
| ALTER TABLE | ALTER TABLE dima_bug_qa_mvp.__qa_tls_objeto_inexistente__ ADD COLUMN qa_col INT | Destino inexistente, validado y sin DDL concurrente; nunca tabla real |
| DROP TABLE | DROP TABLE dima_bug_qa_mvp.__qa_tls_objeto_inexistente__ | Destino inexistente; sin IF EXISTS. Nunca borrar/recrear una tabla real para probar |
| Global CREATE USER | CREATE USER 'dimabug_qa_app'@'172.21.0.3' | Cuenta ya existente, sin IF NOT EXISTS ni cambio de password. Error de cuenta existente no prueba denegacion |
| GRANT OPTION | GRANT SELECT ON dima_bug_qa_mvp.cl_frecuencia TO '__qa_tls_destino_inexistente__'@'localhost' | Destinatario inexistente; MySQL8 no crea usuarios mediante GRANT. Error de destinatario inexistente/1410 no se acepta como prueba de ausencia de GRANT OPTION |
| Lectura de otra BD | SELECT 1 FROM mysql.user WHERE 1=0 | No proyecta ni devuelve columnas sensibles; si el permiso fuera excesivo, cero filas |
| Gmail UPDATE/DELETE | UPDATE dima_bug_qa_mvp.<tabla_gm> SET <columna>=<columna> WHERE 1=0; DELETE FROM dima_bug_qa_mvp.<tabla_gm> WHERE 1=0 | Predicado constante falso; tabla/columna provienen de la lista cerrada, nunca entrada del usuario |
| Gmail INSERT | INSERT INTO dima_bug_qa_mvp.gm_cuenta_oauth(id,version,estado) SELECT 2000000001,0,'DESCONECTADA' WHERE 1=0 | Cero filas y tabla sin AUTO_INCREMENT; no crea cuenta OAuth ni reserva ids automaticos |

Lista cerrada Gmail para UPDATE: gm_cuenta_oauth.version, gm_estado_oauth.version_cuenta, gm_auditoria_oauth.tipo, gm_prueba_correo.version, gm_prueba_evento.estado. DELETE se prueba con WHERE 1=0 en las cinco. SELECT limitado a COUNT(*) se permite para medir baseline, nunca seleccionar tokens. No se usa INSERT en tablas con AUTO_INCREMENT para evitar efectos secundarios en contadores; la ausencia de INSERT en todas las gm_* se contrasta exhaustivamente con metadatos de grants.

Esperados: denegacion de comando/privilegio (p.ej. 1142 o 1227 segun operacion), con SQLSTATE/control compatibles. No aceptar 1045 de login, 1050/1051/1146 de existencia ni sintaxis como prueba positiva. Si el servidor prioriza existencia de objeto/cuenta sobre permisos, registrar INCONCLUSA y sostener la conclusion solo en auditoria de grants; no modificar un destino real para lograr otro error.

Privilegios globales FILE/SUPER/administracion/etc. y GRANT OPTION se contrastan ademas en USER_PRIVILEGES/SCHEMA_PRIVILEGES/TABLE_PRIVILEGES/COLUMN_PRIVILEGES, roles, mandatory_roles, proxies_priv y procs_priv contra allowlist. No ejecutar SET GLOBAL, INTO OUTFILE, GRANT a cuenta existente ni cambios reales de permisos para probarlos. El caso CREATE USER es representativo, no demuestra por si solo ausencia de todos los privilegios globales.

Si no se puede garantizar exclusividad y las condiciones de existencia/ausencia, omitir los negativos DDL/GRANT, marcar pendientes y limitarse a auditoria de permisos y DML de cero filas. Nunca afirmar que un rollback de esos comandos garantiza inocuidad.

## Cierre inmediato, normal y por fallo

Operacion prevista por root LOCAL (todavia NO ejecutada):

```sql
ALTER USER 'dimabug_qa_app'@'172.21.0.3' ACCOUNT LOCK;
```

Después identificar conexiones cuyo usuario sea exactamente dimabug_qa_app y que pertenezcan a la ventana de prueba. Validar sus ids numericos y ejecutar KILL CONNECTION <id> desde root; no KILL QUERY solamente, no ids fijos/copias antiguas ni sesiones de otro usuario. Reconsultar hasta cero y comprobar account_locked=Y de ambas cuentas. El guardian aplica el mismo cierre a los 180s o si el ejecutor desaparece; el ejecutor cierra antes en finally. Si Docker/root no responden, registrar cierre NO verificado y pedir intervencion; no declarar seguro el estado ni detener servicios ajenos automaticamente.

El administrador QA no necesita desbloquearse. No se restauran passwords, se regeneran claves, se cambian grants o se reinician servicios para cerrar esta prueba.

## Evidencias y aceptacion

Conservar JSON sanitizado: fecha/hora UTC, id de corrida no secreto, version/hash de scripts/cliente, identidad QA comprobada como booleano, cuenta/origen esperado, version TLS/cipher, hashes publicos CA/hoja, verificacion de pin antes de auth, ids de casos, errno/SQLSTATE, filas afectadas/conteos, rollback, baseline/final coincidentes, bloqueo final y cero sesiones. Hash SHA-256 del reporte. No logs completos, dumps, SQL con password, URI con credenciales, PEM privados, authentication_string, tokens, imagenes ni texto OCR. La CA/certificado publicos usados temporalmente pueden guardarse solo bajo qa/runtime ignorado; no instalar confianza global.

Aceptacion: TLS y pin reales en misma sesion autenticada; identidad/origen exactos; cuatro verbos positivos satisfactorios con marcadores ausentes al terminar; negativos devuelven denegacion especifica o quedan explicitamente inconclusos; ningun dato/permiso/canario persistente nuevo; ambas cuentas bloqueadas, cero sesiones app y grants intactos. Cualquier incertidumbre impide declarar la prueba completa. La evidencia actual TLS sin auth no sustituye ninguna de esas pruebas futuras.

Fuentes primarias: https://dev.mysql.com/doc/refman/8.0/en/using-encrypted-connections.html ; https://dev.mysql.com/doc/refman/8.0/en/account-locking.html ; https://dev.mysql.com/doc/refman/8.0/en/grant.html ; https://pymysql.readthedocs.io/en/latest/modules/connections.html ; https://github.com/PyMySQL/PyMySQL/blob/main/pymysql/connections.py . Metadatos de version/hash consultados en https://pypi.org/pypi/PyMySQL/json sin instalar el paquete.
