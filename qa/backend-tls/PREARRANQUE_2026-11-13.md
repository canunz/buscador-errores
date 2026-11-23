# Controles previos al primer arranque QA — 2026-11-13

## Resultado y alcance

JWT exclusivo QA generado con CSPRNG (64 bytes aleatorios, 512 bits, codificación Base64). Guardado como `jwt.dpapi` dentro de la carpeta de custodia Windows existente `DimaBug-QA-MySQL-13318`, con DPAPI CurrentUser y propósito `dimabug-qa-jwt-v1`. Se verificaron recuperación, formato, identidad QA, propietario y ACL. Ningún valor, hash de la clave o contenido cifrado se incluyó en Git, manifiestos, informes o logs.

No se reescribió `credentials.dpapi` ni su respaldo portable: sus copias siguen correspondiendo a las mismas credenciales MySQL. El JWT usa un archivo hermano para evitar invalidar ese respaldo. No existe todavía respaldo portable del JWT: depende del perfil Windows/DPAPI. Si se pierde, puede regenerarse previa autorización, invalidando las sesiones JWT QA anteriores. No es una clave de cifrado de datos MySQL ni Gmail.

Se implementaron el supervisor y un segundo vigilante. No se ejecutó su modo operativo, porque ese modo ejecutaría SQL de cierre. Solo se ejecutaron pruebas de componentes con procesos inocuos y acceso SQL simulado. No se inició Spring/Angular, no se ejecutó SQL y no se desbloquearon cuentas. No se requirieron privilegios Windows adicionales ni cambios de permisos MySQL para las pruebas realizadas.

El JAR backend y el complemento JDBC del candidato anterior no se recompilaron ni modificaron en esta etapa. Cambia el manifiesto de fuentes por los nuevos controles operativos; se conserva el manifiesto anterior en `qa/history`.

## Implementación

| Archivo | Responsabilidad |
|---|---|
| `Initialize-QaJwt.ps1` | Generación explícita, escritura CreateNew, protección DPAPI y verificación. Rechaza sobrescribir una clave existente. Sin `-Create` no genera nada. |
| `Qa.Custody.psm1` | Lectura DPAPI QA y JWT; propietario/ACL, propósito y formato estrictos. Devuelve SecureString. |
| `Qa.Jdbc.psm1` | Carga DB/JWT desde custodia. Crea el entorno Java desde cero, con TLS, perfiles y artefactos explícitos. Limpia variables sensibles al terminar o fallar. No arranca procesos. |
| `QaBackendJob.cs` | Crea el proceso suspendido, lo asigna al Job Object y solo entonces lo reanuda. Conserva handles del proceso exacto; no busca ni mata procesos por nombre o PID reutilizable. Kill-on-job-close incluye descendientes. |
| `Invoke-QaBackendSupervisor.ps1` | Proceso independiente: observa al padre, Java, segundo vigilante y plazo; solo consulta HTTP liveness. Ante cierre detiene Java y llama al adaptador de bloqueo. No contiene desbloqueo. |
| `cleanup_sentinel.py` | Segundo proceso independiente: observa al supervisor mediante handle Windows y solicita cierre ante su muerte, señal de cierre o plazo. Reintenta el cierre hasta tres veces; nunca declara éxito sin verificación. |
| `close_qa_account.py` | Adaptador de cierre restringido al Docker local, imagen/volumen/red/secret mount QA esperados y cuenta de aplicación exacta. Reutiliza el mecanismo root QA existente y `close_window`. Lista SQL cerrada: ACCOUNT LOCK, lectura de sesiones/estado y KILL de sesiones QA. Sin UNLOCK, grants o cambios de esquema. |
| `Qa.Supervisor.psm1` | Prepara una carpeta de ejecución con ACL usuario/SYSTEM y ProcessStartInfo del supervisor con rutas de herramientas explícitas. No inicia procesos por sí mismo. |
| `test_supervisor.py` | 19 pruebas, acceso Docker/SQL simulado; incluye salida real de un padre inocuo y callback de limpieza simulado. |
| `Test-QaBackendJob.ps1`, `test_job_owner.ps1` | 6 verificaciones Windows con procesos PowerShell inocuos: parada, idempotencia, Dispose, salida con error y terminación abrupta del propietario. Sin Java/Spring/SQL. |

El modo predeterminado de supervisor, sentinel y adaptador es inerte. `Start-QaBackend.ps1 -Start` sigue bloqueado. Los flags de ejecución operativa no constituyen por sí mismos autorización: requieren una aprobación posterior del usuario.

## Comportamiento ante fallos

- Creación/asignación del Job denegada: el hijo permanece suspendido y se termina; no se usa una alternativa sin supervisión. Si exige permisos adicionales, detenerse y pedir autorización.
- Error o salida de Java: el supervisor cierra el Job y solicita ACCOUNT LOCK/cierre de sesiones.
- Arranque sin liveness: plazo de 120 segundos desde el lanzamiento Java, sujeto al límite global.
- Cierre solicitado, interrupción o muerte del padre: el supervisor detiene el Job y cierra la cuenta.
- Muerte forzada del supervisor: Windows cierra sus handles y termina el Job; el segundo vigilante intenta bloquear la cuenta y cerrar sesiones.
- Muerte del segundo vigilante: el supervisor la detecta, termina Java y realiza su propio cierre.
- Ventana global: 180 segundos desde el inicio del supervisor; el segundo vigilante tiene también un máximo de 180 segundos desde su propio inicio. Incluye la espera previa al arranque. No hay reinicios ni ampliaciones automáticas.
- Docker/MySQL inaccesible, fallo de cierre o sesiones remanentes: `CIERRE_NO_VERIFICADO`, recuperación manual obligatoria; no afirmar que la cuenta quedó bloqueada. Los intentos SQL tienen límites de tiempo, pero su cierre puede prolongarse más allá de la ventana de ejecución Java. El supervisor espera de forma acotada y el segundo vigilante puede seguir intentando el cierre.

## Límites que no deben ocultarse

1. No se verificó hoy la ejecución real de ACCOUNT LOCK/KILL ni el estado actual de cuentas: hacerlo violaría la restricción de no SQL. Se reutiliza el mecanismo root QA ya usado, sin conceder nuevos permisos. Su capacidad efectiva debe reconfirmarse en el preflight autorizado.
2. El supervisor completo en modo operativo no se ejecutó; se probaron sus componentes y condiciones de cierre. Sigue pendiente la primera integración con Java/Spring y MySQL.
3. Si terminan ambos vigilantes, se apaga Windows o Docker no está disponible, el Job evita que Java sobreviva a su propietario, pero no puede garantizar que MySQL reciba ACCOUNT LOCK. Antes de cualquier nueva ejecución debe reconfirmarse el bloqueo y cero sesiones.
4. Los procesos de QA no heredan ni cargan credenciales staging: entorno vacío, rutas explícitas, perfiles QA, DB/JWT DPAPI QA y adaptador SQL local. No se creó un límite de seguridad entre usuarios Windows: código arbitrario ejecutado por el mismo usuario podría acceder a otros archivos de ese usuario. No se afirma un aislamiento de sistema operativo que no existe.
5. `GET /api/health` es liveness estático. Un HTTP 200 no demuestra TLS JDBC, validación Hibernate, ausencia de tareas o cierre de cuenta.
6. SecureString/DPAPI reducen exposición persistente; el backend necesita secretos en memoria y en su entorno. Los strings administrados no admiten borrado garantizado. No registrar ProcessStartInfo, entorno completo ni excepciones JDBC del proveedor.

## Procedimiento exacto que debe autorizarse posteriormente

La siguiente etapa necesita autorización explícita para las consultas SQL previas, el desbloqueo temporal de la cuenta de aplicación, la prueba JDBC y un único arranque Spring QA. Ninguna de esas acciones se ejecutó hoy.

1. **Revisión previa sin arranque.** Validar el manifiesto actual con `python -I -B qa/qa_manifest.py --check`; verificar hashes del bundle y ejecutar `qa/Start-QaBackend.ps1 -CheckCustody` desde una terminal normal de Windows. Confirmar puerto 19090 libre y MySQL QA/proxy existentes; no iniciar o reemplazar contenedores automáticamente.
2. **Preflight SQL autorizado.** Reconfirmar instancia/volumen, esquema `dima_bug_qa_mvp`, ambas cuentas bloqueadas, cero sesiones de aplicación, privilegios mínimos y automatizaciones SQL deshabilitadas. Comprobar capacidad del mecanismo root QA existente para las operaciones necesarias, sin otorgar nuevos privilegios. Si falta acceso/permisos, detener y pedir autorización.
3. **Respaldo y baseline.** Generar un respaldo lógico consistente solo de QA en una ruta privada, sin mostrar su contenido; conservar recuentos y huellas de filas/estructura para detectar también UPDATE sin cambio de recuentos. No usar datos staging/producción. Confirmar recuperación antes de desbloquear.
4. **Preparar el padre y los dos vigilantes.** Mantener viva una terminal/orquestador dedicado. `New-QaBackendRunDirectory` crea una carpeta nueva usuario/SYSTEM; `New-QaSupervisorStartInfo -RunDirectory <carpeta> -ParentId <PID_padre>` prepara el proceso independiente. Iniciarlo solo bajo la nueva autorización. Esperar `supervisor-ready.json` y `sentinel-ready.json`, ambos del directorio NUEVO; confirmar que siguen vivos. Readiness acredita vigilancia, no capacidad SQL.
5. **Prueba JDBC acotada.** Dentro de la misma ventana, desbloquear únicamente `dimabug_qa_app@172.21.0.3`. Ejecutar una sola vez el `JdbcProbe` ya preparado, con CA/huella, bundle y contraseña QA desde custodia; conservar solo sus resultados sanitizados. Cerrar la conexión antes de abrir Hikari. No arrancar Spring si falla.
6. **Señal de arranque.** Solo tras el probe satisfactorio y con ambos vigilantes vivos, crear `begin.request` en la carpeta privada. El supervisor prepara el entorno desde custodia y lanza el JAR mediante el Job. No utilizar un `java -jar` manual ni retirar el bloqueo de `Start-QaBackend.ps1 -Start` para eludir el procedimiento.
7. **Verificación de arranque.** Exigir TLS CA/huella en conexiones reales de Hikari, esquema validado por Hibernate y perfil/destino QA. Confirmar Gmail real/OAuth false, SMTP excluido, despacho y seguimiento false y modo DESHABILITADO. No llamar login, recuperación, códigos o endpoints de negocio: pueden escribir aunque el transporte esté bloqueado. Consultar solo salud; revisar registros filtrados, no logs completos en el chat.
8. **Cierre obligatorio.** Crear `close.request` o dejar vencer la ventana. Exigir Java terminado, `supervisor-final.json` y `sentinel-final.json` de cierre verificado, ambas cuentas bloqueadas, cero sesiones y comparación de baseline. Si cualquiera informa incertidumbre, no repetir el arranque; realizar recuperación manual autorizada.

## Reversión

No hay cambios MySQL que revertir en esta etapa. La nueva clave no sustituye una clave JWT anterior de QA y no cambia los tokens Gmail. No eliminar ni regenerar automáticamente `jwt.dpapi`. Para retirar controles, revisar un diff acotado preservando cambios locales y manifiestos históricos; no usar reset/clean global. Durante la futura ventana, primero detener Java, bloquear cuenta y cerrar sesiones; cualquier restauración de datos o cambio de esquema exige autorización aparte.
