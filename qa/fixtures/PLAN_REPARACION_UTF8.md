# Plan de reparación UTF-8 — preparado, no ejecutado

Actualización de infraestructura (sin SQL): el motor y su integración Windows
están implementados. Véase `EJECUTOR_REPARACION_UTF8.md` para transporte, cierre,
pruebas y comando futuro. Las referencias siguientes a componentes pendientes
describen el estado histórico de la preparación inicial. No autorizan ejecución.

## Estado que se conserva

La operación del candidato QA-local-f823d61-d4490e5e5ebf confirmó 66 inserciones y dejó 67 filas, ocho usuarios y cuatro roles. Falló después del COMMIT durante la lectura/verificación. La evidencia hexadecimal posterior confirmó coincidencia de bytes con mvp-v1; los textos acentuados de esa versión tienen doble codificación. El cliente mysql usa latin1. No se emitió recibo normal de carga satisfactoria.

En esta preparación **no se ejecutó SQL, no se consultó Docker ni se modificó MySQL**. No se cargaron datos, desbloquearon cuentas, iniciaron aplicaciones o alteraron credenciales. La última comprobación operativa de seguridad continúa siendo la del intento anterior, no una consulta nueva.

## Corrección de herramientas

- tls_support.py: `mysql --default-character-set=utf8mb4`; continúa usando socket local, contenedor QA, contraseña interna y salida UTF-8 explícita.
- preflight_backup.py: `mysqldump --default-character-set=utf8mb4`. No cambia defaults del servidor, variables globales, archivos my.cnf ni configuración JDBC.
- Lecturas de archivos de los fixtures y recibos con encoding UTF-8 explícito. No reescribir textos JSON mediante la codificación implícita de Windows.
- mvp-v1.json queda byte a byte intacto como referencia histórica. mvp-v2.json es la nueva versión preparada y el valor predeterminado de las herramientas; conserva 67 filas y las mismas identidades/relaciones.
- Verificación de recibos selecciona la versión explícita del recibo. Se rechaza reset desde v1: la reparación existente requiere UPDATE dirigido, no borrar/reinsertar. Los recibos v1 siguen siendo verificables para auditoría.

## Plan exacto revisable

`encoding-repair-v1-to-v2.json` contiene **35 actualizaciones de celdas en 15 tablas**, generadas desde la versión v1 preservada. Incluye huellas de ambas versiones, clave primaria, columna permitida, bytes anteriores esperados, bytes nuevos y sentencia parametrizada. `execution_authorized=false` deja explícito el alcance de esta entrega.

No hay INSERT, DELETE, DDL, cambios de privilegios ni desactivación de restricciones. No cambian usuarios, emails, hashes BCrypt, asignaciones de rol, nombres canónicos de rol, identificadores, fechas, estados o relaciones. Solo cambia el contenido textual listado, incluida la representación del índice de búsqueda; MySQL mantiene su índice FULLTEXT mediante el UPDATE normal.

Cada sentencia tiene la forma:

```sql
UPDATE `dima_bug_qa_mvp`.`tabla_permitida`
SET `columna_permitida` = CONVERT(UNHEX(?) USING utf8mb4)
WHERE `clave_primaria` = ? AND HEX(`columna_permitida`) = ?
```

Los parámetros proceden exclusivamente del plan registrado. La comparación HEX evita que una colación insensible a acentos o mayúsculas acepte un valor anterior distinto. Se exige una fila afectada por actualización, sin ignorar filas cero o duplicadas.

`prepare_encoding_repair.py` es una herramienta offline y no tiene modo de ejecución SQL. Con --write-plan solo crea/comprueba los archivos de revisión, sin sobrescribir una variante distinta. La simulación trabaja sobre una copia y exige igualdad completa con v1 antes de cambiar nada; la repetición sobre v2 o un plan alterado se rechazan.

## Procedimiento posterior que necesita nueva autorización

1. Verificar el candidato nuevo y las huellas del plan/versiones; confirmar que no hay Spring/Angular, puertos libres, ambas cuentas QA bloqueadas y cero sesiones. Validar contenedor, imagen, volumen, UUID, red, permisos y restricciones como en el preflight original.
2. Preparar un **preflight de recuperación para la base ya poblada sin recibo**. Debe comparar todas las filas con v1 usando el cliente corregido, contrastar esquema/privilegios con la evidencia anterior y exigir exactamente 67 filas, ocho usuarios, cuatro roles, Gmail/códigos/sucursales vacíos. No usar el preflight de base vacía ni fabricar un recibo antiguo para eludirlo.
3. Crear un respaldo consistente NUEVO del estado de 67 filas, con utf8mb4 y verificación de lectura/esquema/recuentos. Conservar además el respaldo anterior a la carga de una fila. Registrar línea base completa de filas, esquema, privilegios y las 35 celdas objetivo, en carpeta privada.
4. Activar supervisión y preparar un ejecutor transaccional revisado para esta reparación. No se entrega como comando de ejecución automática en esta fase.
5. Utilizar **una sola conexión y transacción**. Bloquear/verificar las filas necesarias, comprobar el estado completo v1 y aplicar los 35 UPDATE parametrizados. Exigir ROW_COUNT/rowcount igual a uno en cada paso. Verificar v2 dentro de la misma transacción antes del COMMIT. Cualquier diferencia provoca ROLLBACK completo.
6. No implementar lo anterior mediante 35 llamadas separadas a sql(): ese helper abre una conexión por llamada. Tampoco enviar un lote que ejecute COMMIT antes de que el controlador valide resultados; ese patrón produjo la ambigüedad del intento anterior. El ejecutor deberá permitir comprobaciones intermedias en la misma sesión, con tiempos máximos y salida sanitizada.
7. Tras COMMIT, realizar comprobaciones independientes: igualdad exacta con v2, 67 filas, mismos usuarios/roles/relaciones/hashes, diferencias limitadas a las 35 celdas, esquema y privilegios sin cambios, Gmail y códigos vacíos. Emitir recibo NUEVO v2 enlazado a esta reparación; no sobrescribir el historial de fallo.
8. Cerrar el mantenimiento, confirmar ambas cuentas bloqueadas y cero sesiones, conservar resultados de supervisión y documentar solo las diferencias autorizadas.

La autorización de preparación actual no cubre estos pasos operativos, la creación de respaldos mediante SQL ni la modificación de datos.

## Reversión

Antes del COMMIT: ROLLBACK en la misma conexión. No ejecutar DDL ni cambiar restricciones.

Si la verificación independiente posterior al COMMIT falla, detenerse y conservar el estado como no verificado. Una reversión dirigida puede usar el plan inverso, exigiendo los bytes nuevos antes de reponer los antiguos, pero requiere aprobación específica. No restaurar automáticamente toda la base ni borrar/reinsertar usuarios. Conservar respaldos y huellas para decidir la recuperación.

## Pruebas aisladas

Regresiones de charset/comandos simulados, preservación de v1, integridad de v2, alcance exacto, conservación de usuarios/hashes, rechazo de deriva/repetición/plan alterado, simulación de igualdad completa con v2, campos prohibidos y UTF-8 con caracteres acentuados. No se ejecutó mysql, mysqldump, Spring o Angular.

Resultado: 14 regresiones nuevas correctas; también 18 casos de fixtures, 10 de recibos, 7 de ausencia de filtraciones, 9 de cierre Python, 7 de integridad del paquete y 28 comprobaciones PowerShell del cierre. Las consultas y transportes fueron simulados; no se leyeron custodias.
