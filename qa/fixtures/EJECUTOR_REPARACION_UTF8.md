# Ejecutor UTF-8: implementación aislada

Preparado sobre el candidato `QA-local-f823d61-ba0d80f1b919`. Los archivos nuevos
cambian su huella: ese candidato histórico no autoriza ejecutar las nuevas herramientas.
No se ejecutó SQL ni se accedió a MySQL durante esta implementación.

## Componentes y garantías

`encoding_executor.py` implementa una conexión DB-API para toda la transacción,
charset comprobado, aislamiento REPEATABLE READ y bloqueo de rangos de las 44
tablas InnoDB. Comprueba las filas v1 completas, aplica solamente el plan exacto
regenerado (35 campos/15 tablas), exige una fila afectada por UPDATE y compara
todas las filas contra la simulación v2 antes del COMMIT. Esta comparación
preserva también los hashes BCrypt reales, que no contiene el fixture versionado.
La validación de fixtures comprueba relaciones y estados.

Después de recibir confirmación del COMMIT, cierra la conexión y verifica por una
conexión nueva. No ejecuta reintentos. Una excepción durante COMMIT produce
UNKNOWN, incluso si el servidor llegó a confirmar; nunca se declara ROLLED_BACK
por enviar posteriormente un rollback. Una excepción anterior provoca rollback;
ROLLED_BACK significa respuesta confirmada del servidor. Cuando existe línea
base leída dentro de la transacción, una conexión nueva compara el estado y
registra ROLLBACK_VERIFIED; si esa comprobación falla queda NOT_VERIFIED.
Si rollback falla, el resultado queda UNKNOWN.

La evidencia retornada contiene estados y huellas, nunca filas, SQL, parámetros,
excepciones completas, credenciales ni hashes de contraseña individuales. No
debe emitirse un recibo de éxito si success no es true. La persistencia del
resultado debe ser exclusiva y atómica en la carpeta privada del supervisor.

`encoding_controls.py` conecta el motor con `preflight` y `preflight_backup`.
La rama nueva de recuperación mantiene todos los controles previos, exige la
metadata histórica sin alteraciones y verifica exactamente v1 con 67 filas.
No usa recibos ficticios, no admite reset ni carga inicial. El backup nuevo usa
utf8mb4, verifica lectura, huellas y estabilidad del estado.

## Infraestructura Windows completada (sin ejecución real)

`repair_transport.py` ofrece una sesión persistente del cliente mysql por socket
local dentro del contenedor QA. El secreto root existente se resuelve allí y no
se copia al host. No se usa TCP ni se desbloquea la cuenta de aplicación. No se
cambian privilegios. SQL por stdin, UTF-8 explícito, stderr descartado y protocolo
de respuestas con delimitadores aleatorios. Reconexión deshabilitada, conexión
máximo 5 s, espera de respuesta 8 s, bloqueos 5 s, SELECT 5 s, inactividad 15 s.
El SQL no va en argumentos ni archivos temporales. La referencia al archivo del
secreto sí aparece en el comando fijo; su valor solamente existe dentro de MySQL
y el entorno del cliente en el contenedor, como en el mantenimiento previo.

`repair_lifecycle.py`, `repair_cleanup.py`, `repair_acl.ps1` y
`repair_close_cli.py` implementan los callbacks: ACL protegida usuario/SYSTEM,
puertos locales libres, handles de ambos vigilantes (sin confundir reutilización
de PID), deadline y comparación de metadata/grants en la sesión transaccional.
El cierre comprueba contenedor local/imagen/UUID antes de actuar. Mata únicamente
sesiones root con la base QA como predeterminada, bloquea ambas cuentas QA y
confirma ausencia de sesiones de aplicación/administración/mantenimiento.

`Invoke-QaEncodingRepair.ps1` reutiliza `QaBackendJob.cs`, `Qa.Common.psm1`,
`Qa.Supervisor.psm1` y `Qa.Close.psm1`. Arranca el vigilante independiente antes
del worker. El worker se asigna suspendido al Job Object KILL_ON_JOB_CLOSE antes
de ejecutarse. Si muere PowerShell, Windows mata su árbol y el sentinel conserva
el handle del principal para detectar el cierre y limpiar MySQL. No se supone
que matar docker.exe termine inmediatamente la sesión del servidor: el cierre
independiente la mata y verifica, y existe el timeout de inactividad adicional.

El principal detiene el Job y ejecuta su propio cierre antes de solicitar y
comprobar el del segundo vigilante. Si falla cualquiera, no declara éxito. Si
se pierde el worker durante COMMIT, el resultado por defecto es UNKNOWN. Hay
mutex de exclusión y un marcador durable por candidato: un intento fallido no
se repite automáticamente, ni creando otra carpeta. No borrar ese marcador sin
una autorización específica posterior. El recibo preparado por el worker solo
se convierte en receipt.json después de confirmar ambos cierres.

Los reintentos limitados del sentinel se refieren exclusivamente al cierre
idempotente; nunca a la transacción ni al COMMIT. Si Docker/MySQL no responden,
no se puede garantizar bloqueo: se debe informar cierre no verificado.

## Procedimiento posterior sujeto a autorización independiente

1. Aprobar explícitamente una ejecución única para el nuevo candidato registrado.
   Esta fase solo autorizó implementar y simular: no ejecutar el siguiente comando.
2. En PowerShell 7.4+, usar el script `qa/fixtures/Invoke-QaEncodingRepair.ps1`
   con `-RunAuthorizedRepair`, `-Candidate <candidato aprobado>` y
   `-HistoricalBackup <ruta absoluta al backup.json histórico verificado>`.
   La referencia histórica de esta recuperación está bajo
   `qa/runtime/fixture-runs/0ed7c94e84de41ed86a863f1096b5faa/backup.json`.
   La ruta no es una credencial. No copiar ni mostrar backup.sql ni hashes.json.
3. El controlador valida manifiesto, aislamiento, estado v1, respaldo consistente
   nuevo y supervisión; una anomalía impide abrir la transacción de reparación.
   La cuenta de aplicación y la de esquema permanecen bloqueadas toda la operación.
4. Ejecuta una sola transacción y una lectura independiente posterior, cierra las
   conexiones y confirma ambos cierres. No carga fixtures ni ejecuta migraciones.
5. Conservar `operation-summary.json`, informes del sentinel y la carpeta privada
   con nuevo respaldo, plan referenciado, resultado y recibo. Revisar únicamente
   estados y huellas. Si falta un resultado o es UNKNOWN, detenerse sin reintentar.

Con cualquier error anterior al COMMIT se intenta ROLLBACK y se verifica si fue
posible; con pérdida de conexión al confirmar no se presupone reversión. No
restaurar automáticamente ni repetir la reparación.

Si el proceso muere durante COMMIT, el supervisor debe registrar UNKNOWN por
defecto. Si no puede confirmar bloqueo, declarar cierre seguro no verificado.
No restaurar automáticamente después de un commit incierto o ya confirmado.

## Pruebas aisladas

`python -I -B qa/fixtures/test_encoding_executor.py` usa exclusivamente objetos
simulados. Cubre éxito, controles previos, charset incorrecto, plan alterado,
deriva de validación, fila afectada incorrecta, desconexión en UPDATE, rollback
fallido, pérdida de confirmación antes/después de COMMIT, fallo de segunda
conexión, verificación posterior fallida, cierre no confirmado y segunda
aplicación rechazada. No sustituye la prueba de un driver/servidor real ni de
una comprobación operativa futura del servidor y Windows reales.

Las pruebas adicionales de `test_repair_infrastructure.py` simulan protocolo
persistente, parámetros, UTF-8, columnas múltiples, desconexiones, timeout,
interrupción del principal y del sentinel, deadline y cierre de sesiones. No
abren sockets ni procesos Docker, no leen custodias y no ejecutan SQL real.
La eliminación abrupta de procesos y el comportamiento de mysql 8.0.46 real
quedan como verificaciones operativas de la futura ventana autorizada; los
estados UNKNOWN evitan atribuirles un resultado que estas simulaciones no prueban.

## Archivos de esta integración

Nuevos: `repair_transport.py`, `repair_lifecycle.py`, `repair_cleanup.py`,
`repair_close_cli.py`, `repair_worker.py`, `repair_sentinel.py`, `repair_acl.ps1`,
`Invoke-QaEncodingRepair.ps1` y `test_repair_infrastructure.py`.
Modificados: `encoding_executor.py` (conservar columnas múltiples de respuestas),
`encoding_controls.py` (comprobar vigilantes antes del preflight),
`test_encoding_executor.py` (fallo de vigilancia durante la transacción),
`qa/mysql/tls/run_privilege_test.py` (snapshot con query inyectada, conserva sql
como valor predeterminado), este documento y la nota del plan inicial. El
manifiesto se actualiza y su versión anterior se conserva en qa/history.

Resultado aislado: 22 pruebas de infraestructura, 16 del motor, 14 UTF-8,
18 de fixtures, 10 de recibos, 7 de ausencia de filtraciones, 9 de cierre Python,
7 de integridad del paquete y 28 comprobaciones PowerShell. Parser PowerShell y
AST Python correctos; entradas por defecto comprobadas inertes. No se ejercitó
el modo autorizado ni se conectó a MySQL. No se leyeron credenciales ni datos.

## Corrección QA_REPAIR_ACL

El intento del candidato `QA-local-f823d61-46c7a5a40a5c` se detuvo antes de
MySQL: `repair_acl.ps1` devolvió PRIVATE_ACL_OK, pero no asignó LASTEXITCODE.
La comparación null -ne 0 provocó un falso rechazo. No hubo transacción ni
modificación de datos y ese intento no se repitió.

El controlador ahora importa `Qa.RepairAcl.psm1` y llama Assert-QaRepairAcl.
La función captura únicamente el resultado explícito del comprobador y acepta
un solo string exactamente PRIVATE_ACL_OK. No consulta LASTEXITCODE. Rechaza:

- PRIVATE_ACL_NOT_VERIFIED: QA_REPAIR_ACL_UNSAFE.
- Ausencia de salida, null o string vacío: QA_REPAIR_ACL_RESULT_MISSING.
- Otra salida, tipo o multiplicidad: QA_REPAIR_ACL_RESULT_INVALID.
- Excepción o error del comprobador: QA_REPAIR_ACL_CHECKER_ERROR.

Las comprobaciones reales de ruta, enlaces, propietario y permisos dentro de
repair_acl.ps1 permanecen intactas. No se cambian ACL ni se permite continuar
ante permisos inseguros. `Test-QaRepairAcl.ps1` contiene 15 comprobaciones,
incluyendo códigos de salida null/obsoletos, errores y la ejecución del
comprobador real con una ACL insegura simulada. Pasaron además 103 pruebas
Python existentes y 28 comprobaciones PowerShell de cierre.

Esta corrección no accedió a MySQL, no ejecutó SQL ni repitió la reparación.
El nuevo manifiesto identifica las herramientas corregidas; su autorización
de ejecución debe solicitarse por separado. El procedimiento operativo anterior
se mantiene y se debe usar el candidato nuevo registrado.
