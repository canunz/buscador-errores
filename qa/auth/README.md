# Autenticación y fixtures QA locales — implementación 2026-11-13

Actualización posterior a la carga: se preparó `mvp-v2` y la corrección del cliente UTF-8. La base todavía no fue reparada. Consultar `../fixtures/PLAN_REPARACION_UTF8.md`; conservar mvp-v1 y sus evidencias. No repetir una carga inicial ni usar reset para corregir la carga existente.

Estado: herramientas y complemento compilados/probados aisladamente. **No se ejecutó SQL ni se cargaron fixtures. Spring, Angular y el visor gráfico no se iniciaron.** No se desbloquearon cuentas ni se cambiaron staging/producción. Sin commits.

## Archivos y decisiones

- `src/dimabug/qa/otp/QaApplication.java`: entrada exclusiva del complemento QA. Añade la configuración y valida el entorno antes de crear beans. El BackendApplication y el servicio real de autenticación no cambian.
- `QaOtpConfiguration.java`: exige exactamente qa-local, UTC, endpoint/usuario JDBC QA, validación TLS fijada, validate/never, controles de correo false, SMTP excluido, automatización deshabilitada y registros sin detalles sensibles. Retira únicamente el adaptador Gmail de códigos de la definición de beans QA; registra el adaptador local. No crea endpoints.
- `QaOtpAdapter.java`: implementa el mismo EnvioCodigoAccesoPort. Entrega solo los ocho destinatarios del fixture. Exige transacción real activa y publica AES-256-GCM únicamente en afterCommit. Rollback no publica. Nonce aleatorio, AAD ligada a ejecución/registro, publicación atómica y lectura autenticada. Reemplaza entregas anteriores del destinatario y elimina caducadas al publicar; limita el buzón. No usa red, Gmail, SMTP ni loggers.
- `build_qa_auth.py`: compilación offline contra el backend ya validado y sus dependencias, sin arrancar Spring. Produce un JAR separado. Copia sin modificar el backend, TLS, CA y cargador al nuevo paquete y registra fuentes/hashes del complemento.
- `Initialize-QaOtpKey.ps1`: clave nueva de 256 bits en `otp.dpapi`, hermana de la custodia existente, con propósito DPAPI CurrentUser independiente. Generación/recuperación verificadas sin mostrar valores; no reescribe JWT, claves Gmail, contraseñas MySQL o respaldos previos.
- `Qa.Otp.psm1`: lectura SecureString de clave, creación/verificación de buzón usuario/SYSTEM y limpieza acotada sin eliminación recursiva.
- `Qa.Otp.Crypto.psm1`: descifrado y validación de formato, AAD, registro, destinatario y caducidad; devuelve el código como SecureString.
- `../backend-tls/Qa.Custody.psm1`: lectura compatible de DB/JWT/OTP mediante JSON .NET y SecureString construido sin pasar datos descifrados a cmdlets. Evita registrar sus valores como parámetros si se activa auditoría de módulos. No cambia valores ni formatos de custodias existentes.
- `Show-QaOtp.ps1`: visor Windows dedicado, fuera de HTTP/Angular. Código oculto por defecto, revelado mientras se mantiene el botón; sin portapapeles, sin salida del código a consola y con cierre máximo de 30 segundos o caducidad. Borra la entrega tras consultar. Esto consume la **entrega**, no el código del servidor: su consumo real ocurre al verificarlo en DimaBug.
- `PrepareFixtureHashes.java`: herramienta explícita de preparación futura que escribe hashes BCrypt de contraseñas aleatorias desconocidas a un archivo privado. El login por contraseña continúa retirado; no hay contraseñas predeterminadas en Git.
- `../backend-tls/Qa.Jdbc.psm1`: opción EnableQaOtp/OtpMailbox, inactiva por defecto, exige complemento registrado; carga clave en el entorno hijo y nunca en argumentos. Limpia también QA_OTP_KEY. Sin opción sigue usando el backend anterior.
- `../backend-tls/Qa.Supervisor.psm1` e `Invoke-QaBackendSupervisor.ps1`: propagan opción explícita, comprueban buzón/clave antes de readiness y limpian el buzón al cierre normal; conservan ambos vigilantes y el cierre de MySQL.
- `../qa_manifest.py`: permite paquete con el complemento adicional y comprueba sus archivos/fuentes.
- `../mysql/tls/run_privilege_test.py`: perfil de datos opcional mediante recibo de fixture. Sin recibo sigue exigiendo la base vacía. Identidad, imagen, volumen, privilegios, red, TLS, registros y ausencia de clientes siguen siendo obligatorios.
- `../mysql/tls/tls_support.py`: decodificación UTF-8 explícita para lecturas Docker/MySQL con textos sintéticos acentuados. No cambia credenciales ni SQL autorizado.

## Datos sintéticos

`../fixtures/mvp-v1.json`: 67 filas totales, incluida la fila estructural ya existente. Cuatro roles canónicos ADMINISTRADOR, TECNICO, SOPORTE y USUARIO; ocho usuarios bajo qa.dimabug.test, uno inactivo. Incluye dos departamentos/contactos/responsables, dos sistemas/equipos/módulos/frecuencias, tres conocimientos en estados distintos, síntomas/causas/prueba/soluciones y derivación, dos procedimientos con pasos manuales, tres tickets con historial coherente, etiquetas y comentarios público/interno.

Sin sucursales, OAuth, tokens, códigos o mensajes pendientes. Los hashes de usuario son placeholders hasta la carga autorizada. No hay dependencia usuario–departamento o usuario–sucursal inventada. Las imágenes/PDF sintéticos en `../fixtures/assets` se suben desde Chrome durante pruebas; no se insertan referencias a archivos inexistentes.

`fixture_tools.py` valida campos obligatorios, longitudes, enums, unicidad, referencias físicas/lógicas y órdenes de pasos. Prepara DML transaccional y verificación exacta. El perfil fixture exige versión/fingerprint, UUID de instancia, esquema y huellas de filas, no solamente recuentos. No acepta un conjunto arbitrario como fixture.

## Carga, verificación y restablecimiento — pendientes de autorización SQL

Únicamente usar `../fixtures/Invoke-QaFixtures.ps1`, en PowerShell normal sin perfiles:

1. `-Phase load -Apply`: requiere base vacía y cuentas bloqueadas/sin clientes; crea carpeta privada, realiza preflight/respaldo consistente y verifica integridad; genera hashes privados, carga un solo lote transaccional y verifica las 44 tablas. Conserva recibo vinculado a versión/instancia y huellas. No desbloquea cuentas.
2. `-Phase verify -Apply -Receipt <recibo>`: lecturas únicamente, con los mismos controles de aislamiento y comparación exacta. El recibo no contiene claves, contraseñas, hashes BCrypt ni códigos; solo huellas agregadas.
3. Tras una sesión funcional, detener Angular/backend y confirmar cierre. `-Phase reset -Apply -Receipt <recibo_anterior>` crea otra carpeta/respaldo. El modo reset acepta cambios de pruebas solamente después de validar instancia/esquema/recibo, cuentas bloqueadas, cero clientes y usuarios exclusivamente sintéticos. Rechaza datos Gmail o sucursales inesperados. Borra hijos antes de padres e inserta otra vez el fixture, conservando la fila estructural y gm_* intactos. No usa TRUNCATE, DDL ni FOREIGN_KEY_CHECKS=0.
4. Con el recibo nuevo verificado, `Reset-QaFixtureStorage.ps1 -Apply -VerifiedReceipt <recibo_nuevo>` reconfirma el estado y elimina únicamente archivos UUID planos dentro de los directorios QA materiales/tickets. Rechaza enlaces, directorios o entradas desconocidas; sin Apply solo prepara el inventario. Limpiar también el buzón con Clear-QaOtpMailbox y cerrar el perfil Chrome de prueba.

Todos los scripts operativos son inertes sin su flag de ejecución. Las operaciones verificadas y respaldos quedan privados en qa/runtime/fixture-runs; nunca se imprimen dumps, parámetros SQL, claves o hashes de contraseña. No se actualiza ni sobrescribe un recibo de una ejecución anterior. Una restauración de respaldo o corrección de esquema requiere autorización aparte.

## Verificaciones ejecutadas

| Grupo | Resultado |
|---|---|
| Java: servicio real con repositorios en memoria, callbacks commit/rollback, cifrado, límites, beans y rechazo de entornos/registros inseguros | 37 comprobaciones |
| PowerShell: AES-GCM, caducidad, AAD/registro incorrectos, manipulación y SecureString | 7 comprobaciones |
| Python: fixtures/esquema/relaciones, recibos/instancia/huellas, reset y ausencia de filtraciones | 35 casos |
| Regresión cierre PowerShell | 28 comprobaciones |
| Regresión cierre Python | 9 casos |
| Integridad paquete, incluido complemento OTP | 7 casos |

Además se verificaron la lectura real de la clave OTP desde DPAPI y las ACL de un buzón vacío, sin leer contraseñas MySQL ni JWT. No se abrió GUI ni se entregaron códigos reales. La compilación usó dependencias locales y no descargó paquetes. Los programas Java ejecutados fueron pruebas unitarias, no Spring Boot.

No se observaron políticas activas de transcripción/auditoría de módulos en las claves Windows consultadas; no se leyeron logs históricos ni se cambiaron políticas. Esto no acredita ausencia de instrumentación externa al proyecto; el diseño evita exponer valores a cmdlets y la operación requiere una terminal sin transcripción.

## Garantías probadas y límites

- La autenticación sigue generando códigos aleatorios y hashes mediante el servicio real; conserva 5 minutos de vigencia, 60 segundos de reenvío, cinco intentos, límites por usuario, invalidación anterior, registro de ingreso y consumo único. No se modificó AutenticarUsuarioService.
- Si falla la entrega después del commit, se conserva un marcador QA sin contenido sensible; no se declara rollback ni se reenvía por correo real. El usuario deberá respetar el límite de reenvío. No se altera el servicio para ocultar esa semántica.
- El adaptador no crea u_correo_codigo ni prueba Gmail/outbox: esta etapa acredita autenticación con entrega local, no estados reales de correo.
- Las pruebas Java usan repositorios en memoria y callbacks transaccionales simulados; no prueban todavía la integración JPA/commit real ni el arranque completo del complemento. Deben verificarse bajo una próxima ventana autorizada.
- El visor gráfico no fue abierto en esta etapa. Su núcleo criptográfico se probó sin GUI; quedan pendientes la interacción Windows y la entrada manual en Chrome.
- DPAPI depende de este perfil Windows. No hay respaldo portable de la nueva clave OTP. Si se pierde, no puede recuperarse el buzón anterior; se podrá regenerar con autorización y limpiar códigos/entregas QA. No cifra información funcional de negocio.
- En cierre normal se limpia el buzón. Una caída abrupta puede dejar archivos cifrados; no se muestran después de caducar y deben limpiarse antes de otra sesión. El perfil Windows/administrador conserva acceso al proceso: no se creó un sandbox entre usuarios.
- Los strings gestionados y el texto del control Windows no admiten borrado garantizado de toda memoria. No se utilizan consola, stdout, logs ni HTTP para distribuir códigos.
- La entrada normal de verificación transporta el código y devuelve JWT al navegador autenticado. No capturar HAR, logs de cuerpos ni capturas con esos valores. El JWT se conserva solo según el mecanismo existente; un reset de datos no revoca por sí solo tokens ya emitidos. Usar perfil Chrome limpio, borrar almacenamiento y esperar caducidad o autorizar rotación cuando se necesite revocación completa.
- La ventana supervisada permanece en 180 segundos. Antes de sesiones manuales debe aprobarse una duración mayor para ambos vigilantes, manteniendo un cierre finito. No se amplió silenciosamente.
- Se mantiene pendiente probar desactivaciones y sesiones JWT ya existentes; no se cambió la política real ni la caché de autorización para esta implementación.

## Procedimiento posterior para Chrome

Tras revisar esta entrega: autorizar carga inicial y verificación de fixtures; aprobar una ventana funcional finita más larga; validar una vez el complemento con transacción/JPA real y cierre corregido. Crear buzón privado nuevo mediante New-QaOtpMailbox y arrancar con EnableQaOtp/OtpMailbox únicamente bajo supervisión. El supervisor verifica la custodia antes del desbloqueo.

Luego autorizar Angular QA en 127.0.0.1:14200, backend 127.0.0.1:19090 y proxy /api local. En Chrome separado, solicitar código con uno de los ocho correos. Consultar el archivo correspondiente desde Show-QaOtp.ps1 en una terminal local sin transcripciones; introducir el código manualmente en la UI. Probar roles, permisos, visibilidad, CRUD y archivos; mantener Gmail, SMTP, despacho y automatizaciones false. Terminar la ventana, verificar bloqueo/cero sesiones, registrar cambios esperados y restablecer antes del siguiente conjunto.

No ejecutar estos pasos con la autorización de preparación actual.
