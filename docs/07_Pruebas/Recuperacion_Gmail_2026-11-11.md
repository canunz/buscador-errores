# Recuperación Gmail con clave AES perdida — ejecución 2026-11-11

## Completado

- El usuario confirmó que el cliente OAuth y la cuenta corresponden exclusivamente a staging.
- Se creó custodia cifrada DPAPI para el usuario Windows y copia portable cifrada con frase introducida de forma oculta. Clave de 32 bytes generada una sola vez. No se muestran secretos.
- Se verificaron ambas copias en memoria, permisos restringidos y exclusión Git de `.dimabug-private/`.
- Se capturó cifrado el contexto de arranque del backend local, expandiendo el archivo de argumentos para no depender del archivo temporal original.
- Respaldo consistente de solo lectura de cuenta OAuth, intentos, auditoría, cola y códigos; cifrado con la nueva clave y verificado antes de crear el archivo.
- Cuenta observada: id 1, ACTIVA, versión 2; acceso/refresco presentes y sin revocación pendiente. Ocho correos NO_CONFIGURADO, cero intentos.
- El Java local PID 24280 se detuvo tras verificar identidad, ejecutable y fecha de creación contra el contexto respaldado. No se modificó la cuenta OAuth ni su estado.
- La herramienta de mantenimiento funciona sin Spring, schedulers, JWT ni transporte de envío. Solo admite MySQL staging por loopback. El reset requiere respaldo coincidente, administrador activo, versión exacta y confirmaciones de mantenimiento/revocación externa. Callback de recuperación restringido a localhost:9090 y cinco minutos, con estado, PKCE, cookie, control de replay y versión.

## Pruebas aisladas

- RecuperarGmailTest: 6 pruebas, 0 fallos, 0 errores.
- CifradoGmailTest: 7 pruebas, 0 fallos, 0 errores.
- AislamientoCorreoAutenticacionTest: 4 pruebas, 0 fallos, 0 errores.
- Custodia PowerShell: roundtrip portable, rechazo de frase incorrecta y roundtrip DPAPI con datos sintéticos; sin archivos ni clave de producción en el self-test.

## Archivos privados (nunca adjuntar ni versionar)

Dentro de `.dimabug-private/`:

- `gmail-staging.credentials.dpapi`: custodia Windows.
- `gmail-staging.credentials.recovery.json`: copia portable cifrada; la frase debe conservarse fuera de esta carpeta.
- `staging.runtime.dpapi`: configuración de arranque anterior cifrada.
- `gmail-staging.database.backup.aes`: respaldo previo cifrado, no funcional sin su clave de custodia.

El usuario eligió una ubicación dentro del mismo repositorio/disco. Se requiere copiar el respaldo portable y el respaldo de base a otro medio para cubrir pérdida del disco. Nunca guardar la frase junto a ellos.

## Pendiente antes de modificar tokens

1. Confirmar detención de `dimabug@staging` y `dimabug@staging7` en DigitalOcean. No se ha intervenido en esos servicios desde esta tarea.
2. Confirmar callback Google registrado: `http://localhost:9090/login/oauth2/code/google`.
3. Con instancias pausadas, retirar manualmente el acceso anterior en la cuenta Google remitente y confirmar la revocación. No usar el botón normal de revocar de DimaBug con la nueva clave.
4. Crear respaldo final post-pausa con otro nombre; verificar que la cuenta siga en versión 2. La herramienta aborta si cambió.
5. Ejecutar reset controlado con actor 1 (Administrador DimaBug), respaldo final y versión confirmada. No restablecer versiones a cero ni marcar ACTIVA manualmente.
6. Ejecutar autorización local sin jobs, verificar tokens nuevos y renovación; no mostrar secretos. Instalar coherentemente la nueva custodia en todos los procesos que compartan esa fila.
7. Probar envío controlado y login, y regresión de Procedimientos antes de reanudar jobs. No reproducir mensajes inciertos ni cambiar el modo de Procedimientos.

## Reversión

Antes de reset/revocación, el respaldo permite conservar/restaurar los datos previos pero no recupera el correo sin la clave antigua. Después de revocar en Google no se pueden reactivar los tokens anteriores con una restauración MySQL. Mantener mantenimiento ante fallo; nunca volver a generar otra clave como reintento. Tras autorización nueva, conservar juntos nueva clave y nuevos tokens y verificar compatibilidad antes de revertir código.

## Herramientas preparadas

- `docs/implementacion/automatizacion/herramientas/Inicializar-GmailCustodia.ps1`: inicialización una sola vez y self-test.
- `docs/implementacion/automatizacion/herramientas/Invoke-GmailRecuperacion.ps1`: carga secretos en memoria y ejecuta inspect/backup/reset/authorize sin exponerlos en argumentos.
- `dima-bug-backend/src/main/java/cl/casol/backend/correo/maintenance/RecuperarGmail.java`: utilidad explícita, no registrada como servicio web.
- `dima-bug-backend/src/test/java/cl/casol/backend/correo/maintenance/RecuperarGmailTest.java`: validación de destino, administrador, respaldo, versión, invalidación y auditoría.

No hubo autorizaciones Google, envíos reales, cambios de tokens, commits ni despliegues en esta parte de la ejecución.


## Avance posterior: pausa y respaldo final

- El usuario confirmó ambos servicios DigitalOcean en `inactive/dead`, MainPID 0.
- El usuario confirmó el callback local registrado en Google.
- Se comprobó nuevamente cuenta ACTIVA, versión 2, y ocho registros NO_CONFIGURADO con cero intentos.
- Se creó y verificó `gmail-staging.database.paused.backup.aes` en la carpeta privada, posterior a la pausa. No se sobrescribió el respaldo anterior.
- Se añadieron controles de pausa independientes, habilitados por defecto para conservar comportamiento: `dimabug.auth.correo.habilitado` y `dimabug.automatizacion.seguimiento-habilitado`.
- La herramienta admite verificación de descifrado y renovación sin envío; emplea UTC para su JVM, sesión JDBC y parámetros temporales.
- Se preparó `Restaurar-GmailBackend.ps1`: arranque local en Maintenance/Pilot/Normal desde el contexto y custodia cifrados, con verificación previa de los tokens y sin valores secretos en argumentos. Pilot limita autenticación a un intento/minuto y mantiene seguimiento de evidencias pausado. No se ha ejecutado.
- Pruebas consolidadas de mantenimiento, recuperación, cifrado, OAuth y regresión: 72 aprobadas, sin errores ni envíos reales.

**Punto actual:** pendiente confirmación del propietario de la cuenta Google de que retiró el acceso anterior a la aplicación. Hasta recibirla no se ejecuta reset ni autorización y los servicios siguen pausados. La cuenta sigue intacta en ACTIVA/versión 2.


## Control adaptado: revocación anterior no comprobada

Este checkpoint sustituye el requisito anterior de confirmar una revocación que no pudo comprobarse. El usuario autorizó únicamente adaptar/probar el control y realizar verificaciones de lectura; reset y OAuth siguen sin autorización.

- Remitente oficial y custodia: dimabug507@gmail.com; coincidencia exacta verificada previamente. Usuario confirma alta guardada como usuario de prueba en Google Cloud.
- El reset exige ahora --google-revocation-unverified y mantenimiento; rechaza --google-revoked-confirmed. No afirma revocación exitosa.
- Al ejecutar posteriormente un reset aprobado, la misma transacción insertará RECUPERACION_AES_PERDIDA y GOOGLE_REVOCACION_NO_COMPROBADA con actor y fecha. Ninguno de estos eventos se insertó ahora. La incertidumbre no se elimina al autorizar tokens nuevos.
- Preflight de solo lectura satisfactorio: respaldo final gmail-staging.database.paused.backup.aes autenticado/descifrado en memoria, cinco tablas presentes y cuenta coincidente en versión, estado y los tres campos de tokens cifrados. Cuenta id=1, estado ACTIVA, versión=2. Columna tipo de auditoría compatible con el evento. No se descifraron tokens antiguos.
- RecuperarGmailTest: 7 pruebas, cero errores/fallos/omitidas. Comprueban incertidumbre auditada, rechazo de la afirmación de revocación y declaración vacía, versión/administrador/respaldo incorrectos y controles existentes. Datos sintéticos H2; sin MySQL/Gmail. Parser PowerShell: cero errores.
- La ejecución combinada de pruebas no terminó y fue detenida; no se contabiliza como aprobada. La ejecución aislada de RecuperarGmailTest terminó satisfactoriamente.
- No se ejecutó reset, autorización OAuth, renovación, envío, migración ni arranque de servicios. Último estado remoto confirmado por usuario: ambos staging inactive/dead; no hubo nueva verificación remota desde este entorno.
- Antes del futuro reset se requiere aprobación explícita, confirmar que ambas unidades siguen detenidas y repetir preflight/versión exacta. Un cambio de cuenta o respaldo incompatible obliga a abortar. La aceptación de la incertidumbre no invalida tokens antiguos en Google.


## Reset autorizado y ejecutado

- Tras autorización explícita del usuario y nueva confirmación de ambos servicios remotos inactive/dead, MainPID=0, se ejecutó únicamente reset con actor 1, versión esperada 2, respaldo final y declaración de revocación no comprobada.
- Resultado confirmado por SELECT: cuenta id=1, REAUTORIZAR, versión=3; access/refresco/revocación ausentes y expiración nula.
- Auditoría confirmada por SELECT: RECUPERACION_AES_PERDIDA y GOOGLE_REVOCACION_NO_COMPROBADA, actor 1, fecha 2026-11-11T20:48:01 UTC, en la transacción del reset.
- Cola observada: ocho NO_CONFIGURADO con cero intentos. No se enviaron ni reencolaron mensajes.
- Se amplió inspect para consultar exclusivamente metadatos de los eventos de recuperación; compilación satisfactoria.
- No se ejecutó autorización OAuth, renovación ni arranque de servicios. OAuth sigue pendiente de autorización específica. El futuro checkpoint para autorizar es versión 3; no repetir reset ni emplear versión 2.
- Los tokens anteriores no se revocaron en Google; el reset solo eliminó sus copias operativas locales. Los respaldos cifrados históricos se conservan.


## Autorización OAuth completada

- Usuario autorizó iniciar OAuth exclusivamente para la cuenta oficial, sin envío ni reactivación de staging. Se inició utilidad para actor 1, versión esperada 3, callback local y ventana de cinco minutos.
- Usuario comunicó el mensaje de éxito. La utilidad terminó con OAUTH_OK y exit code 0.
- SELECT posterior: cuenta id=1, ACTIVA, versión=4, tokens acceso/refresco presentes, sin revocación pendiente. No se muestran tokens.
- verify versión 4: ambos tokens se descifran en memoria con la custodia nueva; no se contactó Google ni se renovaron tokens en esta comprobación.
- Se creó y verificó, sin sobrescribir anteriores, gmail-staging.database.oauth-v4.backup.aes cifrado del nuevo estado.
- Los eventos históricos de recuperación y revocación no comprobada se conservaron. No se enviaron correos, renovaron tokens ni arrancaron servicios. Cola: ocho NO_CONFIGURADO, cero intentos.
- Pendiente confirmación explícita del usuario de la cuenta Google seleccionada: el mensaje de éxito y login_hint no prueban identidad. Renovación, despliegue de custodia en staging y prueba real de correo siguen pendientes; no asumir recuperación de extremo a extremo completada.


## Confirmación de identidad del consentimiento

El usuario confirmó expresamente que seleccionó y comprobó dimabug507@gmail.com durante el consentimiento OAuth. Se resuelve el pendiente de confirmación manual de identidad; no constituye una validación programática del sujeto Google. Checkpoint vigente: cuenta ACTIVA, versión 4. Sin operaciones adicionales de MySQL, OAuth, renovación, envío ni arranque de servicios en este paso.


## Prueba de renovación autorizada

- Usuario autorizó únicamente renovación: se ejecutó refresh con versión esperada 4 y mantenimiento confirmado.
- Google aceptó el refresh token; utilidad terminó RENOVACION_OK, exit code 0. Se guardó el resultado cifrado mediante control de versión y transacción.
- SELECT posterior: cuenta id=1, ACTIVA, versión=5, tokens de acceso/refresco presentes, sin revocación pendiente. Caducidad observada 2026-11-11T21:56:44.524953 UTC.
- verify versión 5 satisfactorio: ambos tokens se descifran en memoria. Esta comprobación adicional de descifrado no contacta Google; la operación refresh previa sí acreditó aceptación del refresh token en ese momento.
- Respaldo nuevo gmail-staging.database.refresh-v5.backup.aes creado y verificado cifrado, sin sobrescribir anteriores.
- Sin envío de correos ni arranque de servicios. Cola observada: ocho NO_CONFIGURADO, cero intentos. Se conserva auditoría de revocación anterior no comprobada.
- Pendiente configurar custodia coherente en servicios staging y verificar envío controlado y regresión. No se considera probado todavía el envío de códigos de extremo a extremo.


## Custodia instalada únicamente en staging

- Alcance autorizado: /etc/dimabug/staging.env. staging7 fuera del procedimiento, sin modificaciones.
- Variables verificadas en application-staging.properties y application-gmail.properties del JAR analizado; instalador comprobó SHA-256 exacto del JAR remoto 21df61ccb07090d36417fe3032583a38fe21a3c5721f2dac797387b2d82bd91f y nombres de ocho variables antes de recibir custodia.
- Primer intento rechazado por presencia de SPRING_APPLICATION_JSON. Diagnóstico posterior confirmó ocho variables Gmail aún ausentes y cero respaldos: no se había escrito configuración.
- Control ajustado y probado para conservar SPRING_APPLICATION_JSON íntegro, rechazar propiedades Gmail en él y comprobar que sus posibles valores de datasource/perfil corresponden a staging. No se vació ni reemplazó ese JSON.
- Datos reales transmitidos de DPAPI en memoria por stdin de SSH y socket Unix temporal root/grupo restringido; comprobación SO_PEERCRED del usuario carolina. Sin secretos en argumentos, salida ni archivos de transferencia. El archivo temporal de reemplazo quedó restringido a root y fue renombrado atómicamente.
- Segundo intento terminó INSTALACION_VERIFICADA. El receptor solo devuelve ese acuse tras verificar respaldo idéntico, permisos/propietario, contenido instalado exacto y servicios detenidos.
- Respaldo previo creado sin sobrescritura en /etc/dimabug/custodia-backups/staging.env.<timestamp>.<pid>.bak; directorio root 0700; copia con propietario/grupo root:root y modo 0600, contenido verificado igual al original antes de reemplazo. No se imprimieron ni copiaron valores al informe.
- Archivo final /etc/dimabug/staging.env: root:root 0600, custodia exacta remitida, remitente oficial confirmado, GMAIL_OAUTH_HABILITADO=true y GMAIL_ENVIO_REAL_HABILITADO=false explícitos. URLs callback/frontend de staging, no localhost. Otras variables y modo de Procedimientos preservados. Los tokens versión 5 ya se habían descifrado con esta misma custodia antes de instalar.
- Instalador sin acceso MySQL, sin ejecución de JAR, reinicios, daemon-reload, cambios en staging7 ni llamadas de correo. La configuración está preparada en disco, todavía no cargada por un proceso activo.
- Validaciones sintéticas: seis casos iniciales y seis casos de JSON (preservación y cinco rechazos), sin secretos reales ni acceso MySQL/Gmail. Sintaxis PowerShell del emisor verificada.


## Auditoría previa a arranque, solo lectura

- systemctl: EnvironmentFiles exacto /etc/dimabug/staging.env (ignore_errors=no), WorkingDirectory esperado, NeedDaemonReload=no. Sin UnsetEnvironment/PassEnvironment, ExecStartPre/Post ni raíz alternativa. PID1 root; archivo legible por root, root:root 0600. User=dimabug no impide que el gestor systemd lea el archivo antes de iniciar el proceso. No se ejecutó un arranque para observar la carga real.
- Preflight root satisfactorio: JAR remoto conserva SHA-256 21df61ccb07090d36417fe3032583a38fe21a3c5721f2dac797387b2d82bd91f; datasource/perfil efectivos revisados por env/JSON corresponden a staging. Sin acceso MySQL.
- GMAIL_ENVIO_REAL_HABILITADO=false explícito; SPRING_APPLICATION_JSON sin propiedades Gmail, sin aliases DIMABUG_GMAIL_, sin sobreescrituras detectadas de argumentos/JVM por preflight. No application*.properties/yml/yaml externos en las ubicaciones estándar inspeccionadas de /opt/dimabug/staging y config. Precedencia esperada demostrada estáticamente; configuración real del proceso aún no observable porque sigue detenido. Referencia oficial: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Revisión de clases de la aplicación dentro del JAR remoto: Scheduled detectado en SeguimientoEvidenciaWorker; no ApplicationRunner/CommandLineRunner/PostConstruct/ApplicationReadyEvent en el alcance de clases propias escaneado. Las clases relevantes listadas en el informe remoto coinciden por SHA con la copia local examinada por javap. Inventario por indicadores no equivale por sí solo a prueba de comportamiento.
- SeguimientoEvidenciaWorker.ejecutar tiene @Scheduled con intervalo/initialDelay por defecto 60000 ms; el binario no incorpora el nuevo control de pausa del código fuente local. Consulta vencidas y llama procesarSeguimiento; ese flujo puede bloquear/actualizar solicitudes y ejecuciones. No asumir cero escrituras al arrancar. Modo externo de Procedimientos no declarado en env/JSON, perfil staging empaquetado con default SIMULADO; configuración local nueva no equivale al JAR desplegado.
- Existen adaptadores SMTP directos (bienvenida y tickets críticos, además de la alternativa SMTP del adaptador de códigos). Hay configuración SMTP presente; no se probó su validez ni se contactó el proveedor. GMAIL_ENVIO_REAL_HABILITADO=false no es un interruptor global de SMTP. No se halló disparador de arranque para esos adaptadores en las clases propias revisadas; pueden responder a acciones/eventos de aplicación.
- Resultado punto 4: no se puede declarar ausencia total de automatismos. Seguimiento programado debe considerarse antes de autorizar arranque; no se aplicó pausa ni cambio adicional en esta auditoría.
- Ambos servicios permanecen detenidos; no se instalaron secretos adicionales ni se modificaron configuración/MySQL, ni se enviaron correos ni arrancaron procesos de aplicación.


## Aislamiento autorizado, aplicado y verificado

El usuario eligió preparar primero el aislamiento, recibió la propuesta concreta y autorizó aplicarla sin arranque. Se creó aislar_staging.py, validado con datos sintéticos (preservación, fusión de exclusiones, idempotencia y cuatro rechazos). Usuario ejecutó el instalador con sudo; informe sanitizado recogido por SSH.

Resultado AISLAMIENTO_INSTALADO_VERIFICADO. Respaldo previo del estado con custodia: /etc/dimabug/custodia-backups/staging.env.20261111T215141Z.564647.bak, contenido idéntico verificado y root:root 0600. Solo se aplicaron DIMABUG_AUTOMATIZACION_MODO=DESHABILITADO y la adición de org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration a SPRING_AUTOCONFIGURE_EXCLUDE, conservando exclusiones previas. Custodia, SPRING_APPLICATION_JSON y otras variables conservados; Gmail envío real sigue false. Archivo final root:root 0600, escritura atómica y comprobada.

Consulta systemctl posterior: staging y staging7 inactive/dead, MainPID=0. Sin acceso MySQL, arranque, despliegue, envíos ni modificación de staging7. El temporizador de seguimiento permanece en el JAR, pero la rama estática con transporte indisponible devuelve lista vacía sin procesar solicitudes vencidas. La ausencia efectiva del bean SMTP y el comportamiento del contexto Spring aún requieren comprobarse durante un futuro arranque autorizado. No se afirma aislamiento de red ni cero escrituras de toda la aplicación.


## Arranque controlado autorizado y verificado

Usuario autorizó iniciar únicamente dimabug@staging.service con aislamiento y sin pruebas de envío. SSH no tenía sudo sin contraseña; el usuario ejecutó sudo systemctl start dimabug@staging.service en su terminal. No se inició staging7.

Verificación posterior: staging active/running, MainPID 564925, NRestarts=0; staging7 inactive/dead, MainPID=0. Puerto 8081 escuchando en loopback. El usuario ejecutó con sudo el verificador pasivo y se recogió su informe sanitizado por SSH.

Entorno real del proceso: Gmail envío real false, Procedimientos DESHABILITADO, exclusión SMTP cargada, cuatro valores de custodia iguales al archivo, SPRING_APPLICATION_JSON idéntico al archivo, JAR y cwd esperados. JAR conserva hash verificado.

Logs de esta invocación, 38 entradas observadas: Started BackendApplication y Tomcat 8081 presentes; sin fallo de arranque, sin dependencia SMTP faltante, cero líneas ERROR y cero indicadores de envío definidos por el verificador. Estos contadores no equivalen a una auditoría exhaustiva de entrega de correo ni a inspección directa de beans Spring. La exclusión se confirmó en el entorno cargado, y el contexto inició correctamente; no se expusieron endpoints de diagnóstico ni se volcó el entorno.

El verificador solo leyó proceso/configuración/journal y abrió una conexión TCP sin petición de aplicación; sin acceso directo MySQL ni envío. No se ejecutó autorización/renovación, prueba de correo, despliegue ni modificación adicional de configuración en este paso. El arranque normal de la aplicación establece sus conexiones de persistencia; no se afirma ausencia absoluta de escrituras de inicialización de la aplicación, que no fueron auditadas aquí.

Estado final: staging activo en aislamiento; staging7 detenido. Correo real y Procedimientos permanecen bloqueados. Pruebas de envío y recuperación de contraseña de extremo a extremo siguen pendientes de autorización.


## Primera prueba funcional: rechazo HTTP por formato de petición

Una única petición real autorizada a recuperación devolvió 400. Error del controlador preparado: JSON identificador en vez de email (alias correo). Se corrigió el controlador de prueba, sin modificar JAR ni ejecutar un nuevo envío. SELECT posterior: usuario/rol activos, total 21 códigos igual al baseline; cola histórica sin cambios, ningún nuevo código ni aceptación Gmail observados. No se validó código ni se cambió contraseña.

Cierre con proceso bloqueado confirmado por el controlador: Gmail false, Procedimientos DESHABILITADO, SMTP excluido y Nginx restaurado. Consulta systemd posterior: staging active/running PID566691 NRestarts0; staging7 inactive/dead MainPID0; watchdog inactivo. Se conservan respaldos actuales y marcador persistente de solicitud única. La prueba de transporte y recepción sigue pendiente. Otro intento real requiere nueva autorización; no se ha reintentado.

Respaldos de esa ventana: /etc/dimabug/custodia-backups/staging.env.pre-prueba-codigo.20261111T221658Z.566444.bak y nginx.staging.pre-prueba-codigo.20261111T221658Z.566444.bak. Detalle de controles sin envío y de corrección en Plan_primera_prueba_codigo_Gmail_2026-11-11.md.


## Segundo intento autorizado: prueba funcional satisfactoria

Evidencia: informe sanitizado /home/carolina/prueba_codigo_intento2.jsonl y salida systemctl compartidos por el usuario. La conexión SSH de esta sesión rechazó autenticación aun tras recargar la identidad; no se atribuye a esta sesión una consulta remota directa del informe final.

- Segundo intento autorizado expresamente; controlador empleó JSON email y un marcador independiente, conservando el del intento rechazado anterior.
- Respaldos previos verificados: /etc/dimabug/custodia-backups/staging.env.pre-prueba-codigo.20261111T222134Z.567256.bak y /etc/dimabug/custodia-backups/nginx.staging.pre-prueba-codigo.20261111T222134Z.567256.bak.
- API pública bloqueada con HTTP 503 durante la ventana. Baseline usuario/rol activos, OAuth ACTIVA versión 5 y 21 códigos previos. No se comprobó aquí la versión OAuth posterior; no asumir que siguió en 5 después de un posible refresco durante el envío.
- Una petición privada POST /api/auth/recuperar/email, HTTP 200; aceptación Gmail observada, exactamente un código nuevo y cola histórica sin cambios. Código id 41, creado 2026-11-11 22:22:08, expira 22:27:08 según metadatos informados; intentos 0 y usado 0. No se guardan código, hash ni contenido del mensaje.
- Usuario confirmó recepción en el buzón. Validación privada del código satisfactoria; no se cambió contraseña ni se mostró el código. Validar recuperación no consume el código; no afirmar usado=true. No equivale a haber probado cambio de contraseña ni login completo.
- Cierre verificado por el controlador: Gmail false, Procedimientos DESHABILITADO, SMTP excluido, Nginx restaurado, staging7 detenido, sin secretos en salida.
- Estado systemctl compartido por usuario: staging active/running MainPID567507 NRestarts0; staging7 inactive/dead MainPID0 NRestarts0.
- Resultado: generación, transporte Gmail, recepción y validación de recuperación comprobados de extremo a extremo para el destinatario único autorizado dimabug507@gmail.com. No se habilitó operación general ni se probaron envíos de Procedimientos, recordatorios u otros destinatarios. Staging permanece activo en aislamiento; no realizar nuevos envíos sin autorización.
