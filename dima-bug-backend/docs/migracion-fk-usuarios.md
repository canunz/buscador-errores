> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. [Documentación vigente](../../docs/README.md). Los enlaces a archivos ausentes se identifican expresamente; [original íntegro y hash](../../docs/auditoria/historico/README.md).

# Migración controlada de FK hacia usuarios

Estado: preparada el 2026-10-08, sin conexión a MySQL, ejecución SQL ni pruebas.
La validez operativa queda pendiente de ensayo por el equipo en MySQL aislado.

## Archivos y convención

- [Diagnóstico SELECT — referencia no disponible](../../docs/auditoria/REFERENCIAS_HISTORICAS.md#archivos-no-disponibles).
- [Migración — referencia no disponible](../../docs/auditoria/REFERENCIAS_HISTORICAS.md#archivos-no-disponibles).

El script existente `V20260925_01__agregar_estado_eliminado_conocimiento.sql`
usa `VYYYYMMDD_NN__descripcion.sql` y ALTER TABLE en `src/main/resources/db/migration`.
Se mantiene esa convención para la migración. El prefijo D y el directorio de
diagnóstico son nuevos; separan consultas de lectura de scripts de migración.
El POM no incluye Flyway/Liquibase y Hibernate usa `ddl-auto=none`.
Estos archivos no incorporan ejecución automática. Véase [persistencia](base-de-datos.md).

## Alcance comprobado

Referencia: export externo `dima_bug_dev_esquema.sql`, revisado como texto.
No se copia el dump al repositorio ni se presume que refleje el estado actual de DEV.

| Tabla y columna | NULL permitido | Índice existente / nombre de FK propuesta | Línea del export |
| --- | --- | --- | --- |
| co_conocimiento.modificado_por | Sí | fk_conocimiento_modificador | 141, 147 |
| pr_procedimiento.creado_por | No | fk_procedimiento_creador | 325, 330 |
| pr_procedimiento.modificado_por | Sí | fk_procedimiento_modificador | 327, 331 |
| se_ejecucion.usuario_id | No | fk_ejecucion_usuario | 345, 352 |
| se_resultado.usuario_id | No | fk_resultado_usuario | 389, 395 |

Las cinco referencias apuntan a `u_usuario.usuario_id`, PK INT signed, en tablas
InnoDB. Todas las columnas hijas son también INT signed en el export.
Se agrega ON DELETE RESTRICT y ON UPDATE RESTRICT. No se modifican columnas,
nulabilidad ni datos y no se crean índices explícitos. Los índices de una columna
ya existentes son aptos para estas FK; verificar su conservación tras el ensayo.
Un índice y una constraint pueden tener el mismo nombre.

La decisión protege autoría e historial; no altera la desactivación lógica de usuarios.
No incluye cascadas, reparación de huérfanos ni cambios Java. Motivos históricos
de las ausencias: **Requiere validación del equipo**.

## Precondiciones y criterio de parada

Antes de ejecutar cualquier ALTER, un operador autorizado debe:

1. Confirmar servidor, conexión y esquema seleccionado; no usar una conexión DEV.
2. Confirmar versión exacta de MySQL 8, cinco tablas InnoDB y tipos compatibles.
3. Ejecutar el diagnóstico completo y conservar sus resultados de forma restringida.
4. Exigir cero en los cinco conteos de huérfanos y ninguna fila en su detalle.
   Los NULL de modificadores son válidos; usuarios inactivos existentes no son huérfanos.
5. Confirmar nulabilidad de la tabla anterior, PK del padre e índices existentes.
6. Revisar todas las FK listadas: ninguna de las cinco relaciones debe tener ya
   una FK equivalente, aunque use otro nombre. Los cinco nombres propuestos
   deben estar libres como constraints en todo el esquema.
7. Confirmar `foreign_key_checks=1` en la misma sesión que ejecutará los ALTER.
8. Disponer de respaldo comprobado y de una ventana sin escrituras ni trabajos
   concurrentes sobre las tablas. Registrar filas e índices antes de migrar.

Si una condición falla, detenerse. Este documento no autoriza reparar, borrar
o reasignar registros. Cualquier saneamiento necesita una decisión independiente.
Las comprobaciones son manuales: el script ALTER no incorpora un guard automático.

## Ensayo primero en una base aislada

1. Provisionar una instancia MySQL independiente, con la misma versión prevista
   para producción y sin credenciales, conectividad ni volúmenes de DEV.
2. Crear un esquema de ensayo, por ejemplo `<ESQUEMA_AISLADO>`. Desde Workbench,
   abrir el export y revisar el destino antes de importarlo. El dump original
   contiene DROP TABLE: importarlo únicamente en el esquema desechable vacío,
   nunca en DEV ni sobre datos que deban conservarse. No se incluye en estos entregables.
3. Seleccionar explícitamente ese esquema en Workbench. Los scripts nuevos no
   incluyen USE ni un nombre de base fijo. Confirmar DATABASE() en el diagnóstico.
4. Ejecutar el diagnóstico. Un ensayo de esquema vacío comprueba DDL, pero no
   demuestra compatibilidad con datos existentes. Para ello, el equipo debe
   preparar datos sintéticos representativos y, si se autoriza, una copia saneada
   gestionada por separado, sin acceder a DEV desde esta tarea.
5. Tras cumplir las precondiciones, ejecutar los cuatro ALTER en orden, uno por
   uno. Configurar Workbench para detenerse al primer error; no usar modo que
   continúe ignorando errores. Registrar éxito/error y duración de cada sentencia.
6. Repetir el diagnóstico y aplicar el plan de validación siguiente.
7. Ensayar recuperación y obtener aprobación del equipo antes de preparar una
   ejecución en producción. Esta preparación no autoriza ejecución sobre DEV.

## Plan de validación

Validación de lectura posterior, preparada en el mismo diagnóstico:

- Los cinco conteos siguen en cero y el detalle sigue vacío.
- Las FK muestran tabla padre `u_usuario`, columna `usuario_id`, el mismo esquema,
  DELETE_RULE=RESTRICT y UPDATE_RULE=RESTRICT. El resto de FK se conserva.
- La consulta de nombres devuelve exactamente las cinco constraints FOREIGN KEY
  previstas en sus tablas. Antes de migrar debía devolver cero filas.
- Nulabilidad y tipos no cambian; los cinco índices y la PK siguen disponibles,
  sin índices equivalentes nuevos. Comparar el inventario completo antes/después.
- Los conteos de las cinco tablas no cambian bajo la ventana sin escrituras.
  Conservar también una comparación/export de datos si se requiere certificar
  contenido: un conteo igual por sí solo no demuestra identidad de valores.

Pruebas funcionales futuras, únicamente en el entorno aislado, pendientes de ejecución:

| Escenario | Resultado esperado |
| --- | --- |
| Insertar/actualizar cada referencia con usuario existente | Aceptado |
| Insertar/actualizar cada referencia con usuario inexistente | Rechazado por FK |
| NULL en ambos modificadores | Aceptado |
| NULL en creador de procedimiento, usuario de ejecución o resultado | Rechazado por NOT NULL |
| Borrar usuario referenciado por cada FK nueva | Rechazado, sin eliminar hijos |
| Desactivar usuario conservando su PK | Permitido; referencias conservadas |
| Crear/modificar conocimiento y procedimiento por Application | Autoría persistida y lectura JPA correcta |
| Iniciar ejecución y consultar historial de resultados | Propietario/autor resolubles, transacciones coherentes |
| Repetir migración completa ya aplicada | No permitido; no es idempotente |

Los fixtures para cada FK deben aislarse de otras referencias, especialmente
`co_conocimiento.creado_por`, que ya tiene FK y podría ocultar qué constraint bloquea
un borrado. No se incluyen scripts de fixtures ni operaciones DML en esta entrega.
En una copia aislada adicional con huérfanos sintéticos, confirmar que agregar
la FK falla y que el plan de recuperación identifica los ALTER ya aplicados.

## Riesgos y plan de recuperación

MySQL 8/InnoDB ofrece atomicidad por sentencia DDL compatible; no convierte los
cuatro ALTER en una sola transacción. ALTER TABLE implica commit y un ROLLBACK
no deshace las sentencias previas. Las dos FK de procedimiento comparten sentencia.

- Huérfanos: la FK no puede incorporarse válidamente sobre referencias inexistentes.
- Esquema divergente, nombres ocupados o FK existentes: detenerse y revisar; no duplicar.
- Metadata locks, reconstrucción de tablas, espacio y duración: medir con volumen
  representativo. No se fuerza ALGORITHM ni LOCK ni se promete ejecución sin bloqueo.
- Escrituras concurrentes: el diagnóstico puede quedar obsoleto; mantener la ventana
  controlada y las comprobaciones FK activas durante la ejecución.
- RESTRICT bloqueará futuros borrados físicos de usuarios referenciados; es el
  comportamiento buscado y debe conocerlo el equipo operativo.
- No desactivar foreign_key_checks: reactivarlo no revisa retroactivamente los datos.

Ante un error o pérdida de conexión:

1. Detener las sentencias siguientes y mantener la ventana sin escrituras.
2. Guardar mensaje y sentencia; reconectar solo al entorno autorizado.
3. Ejecutar únicamente las consultas de diagnóstico para comprobar qué constraints
   existen realmente. Una desconexión no demuestra que el ALTER haya fallado.
4. Preferir recuperación hacia adelante: tras resolver la causa mediante un proceso
   aprobado, ejecutar solo las sentencias ausentes. Si la de procedimiento figura
   parcialmente aplicada por otro proceso, preparar una revisión independiente;
   no repetir el bloque ni alterar constraints a ciegas.
5. Si no puede continuarse, conservar el estado parcial documentado y no liberar
   escrituras hasta decidir con el DBA. En el ensayo, volver a una instancia aislada
   nueva usando la copia base verificada permite repetir sin tocar DEV.
6. Si se exige restauración operativa, el DBA debe definir y aprobar por separado
   alcance, ventana y copia verificable. No restaurar sobre producción a ciegas.

No se entrega SQL de reversión que quite constraints ni operaciones destructivas.
El plan evita asumir un rollback transaccional inexistente. No se elimina ni modifica
ningún registro para hacer que la migración pase.

## Referencias y pendientes

- [MySQL 8: claves foráneas, índices y acciones referenciales](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html).
- [MySQL 8: DDL atómico y commits](https://dev.mysql.com/doc/refman/8.0/en/atomic-ddl.html).

Pendientes: ejecutar el ensayo, comprobar datos reales mediante una actividad
autorizada, medir bloqueos/duración y validar respaldo/recuperación. La falta de
Flyway/Liquibase, preparación reproducible del esquema, DEBUG JWT, acoplamientos,
archivos huérfanos, concurrencia PUT/DELETE y pruebas contra MySQL real siguen
como deuda técnica; no se solucionan con esta migración.
