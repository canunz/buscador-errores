> Referencia histórica conservada. Describe una entrega previa y puede diferir del checkout actual. [Documentación vigente](../../docs/README.md). Los enlaces a archivos ausentes se identifican expresamente; [original íntegro y hash](../../docs/auditoria/historico/README.md).

# Concurrencia selectiva con MySQL real

Ensayo original: 2026-10-08. Corrección autorizada: 2026-10-09.
Alcance: concurrencia selectiva del MVP, no certificación general de concurrencia.

## Análisis de operaciones simultáneas

| Operación | Mecanismos comprobados | Riesgo / regla pendiente |
| --- | --- | --- |
| Edición de conocimiento | MantenerConocimientoService.modificar transaccional; conserva estado de la lectura; JPA + indexación JDBC en REQUIRED | Ediciones normales pueden sobrescribirse; no hay requisito de rechazo, versionado ni merge por campo. No se clasifica last-writer-wins aislado como defecto |
| Publicación/eliminación lógica | cambiarEstado/eliminar transaccionales; eliminado oculto en buscarPorId; DELETE retira proyección | Estado leído puede quedar obsoleto; edición/publicación concurrente podría deshacer un estado terminal |
| Procedimientos/pasos | Servicios transaccionales; publicación exige BORRADOR; UNIQUE(procedimiento_id,paso_orden) | UNIQUE impide orden duplicado, pero validación previa puede perder carrera. Edición conserva estado leído; historial consulta definición actual, sin snapshot |
| Inicio/cierre de ejecución | Inicio atómico cabecera/checklist; UNIQUE(ejecucion_id,paso_id); cierre/edición exigen EN_CURSO; completar exige todos cumplidos | Lectura de estado y escritura no están protegidas conjuntamente contra otro cierre; varios inicios no son duplicados indebidos sin regla de negocio que lo prohíba |
| Registro de resultados | Transacción; exige conocimiento publicado y solución perteneciente; FK usuario/solución | Múltiples resultados son aplicaciones independientes permitidas; no se exige idempotencia ni una evaluación por usuario. Carrera con eliminación lógica no ensayada |
| PUT/DELETE material | PUT transaccional e indexación; DELETE en REQUIRES_NEW confirma fila/proyección y luego bytes | Referencia obsoleta y carrera con borrado; compensación afterCompletion solo para subida fallida, no transacción distribuida |

En las entidades/repositorios revisados no se encontró @Version ni @Lock ni
SELECT FOR UPDATE funcional para estas operaciones. InnoDB sí bloquea escrituras;
eso no convierte automáticamente las lecturas previas en validaciones atómicas.
El MySQL de pruebas usa el aislamiento por defecto del servidor, REPEATABLE READ.
No se cambia aislamiento, DDL, timeout ni lógica funcional para estos ensayos.

Fuentes: MantenerConocimientoService y ConocimientoRepositoryAdapter.buscarPorId;
MantenerProcedimientoService; MantenerPasoService; GestionarEjecucionService;
RegistrarEfectividadSolucionService; MantenerMaterialApoyoService;
ArchivoMaterialService; MaterialApoyoRepositoryAdapter; esquema saneado de tests.

## Selección (máximo tres)

| Escenario | Prioridad de evaluación | Motivo de selección | Invariante evaluada |
| --- | --- | --- | --- |
| C1: checklist pendiente frente a completar | P1 | Varias sesiones pueden operar una ejecución; impacto sobre validez de su cierre | COMPLETADA no debe terminar con paso desmarcado por una escritura que validó un estado anterior |
| C2: edición pendiente frente a DELETE lógico | P1 | Edición/eliminación son operaciones frecuentes, con impacto en visibilidad y búsqueda | Edición ordinaria no debe restaurar ELIMINADO ni recrear su proyección después del commit de DELETE |
| C3: PUT pendiente frente a DELETE de archivo | P1 | BD/filesystem separados; riesgo de resucitar metadatos tras retirar bytes | DELETE confirmado conserva ausencia de fila/archivo; PUT puede rechazarse o ser inocuo, sin recrear el material |

C1 se deriva de exigirEnCurso y de todos los pasos cumplidos para completar.
C2 se deriva de exclusión del eliminado y ausencia de caso de uso de restauración
por PUT. C3 se deriva de buscar material existente antes de modificarlo y del
contrato de eliminación. No se exige que toda edición concurrente sea rechazada.
La política general de resolución de conflictos y la precedencia de DELETE frente
a ediciones iniciadas previamente **requieren validación del equipo**.

## Método determinista y límites

Se añaden tres métodos a MaterialesArchivoIT, reutilizando su contexto funcional,
MySqlIntegrationSupport, JPA/JDBC y @TempDir. No mocks, cambios en producción,
entidades, esquema, permisos, POM ni application.properties.

1. Un ExecutorService inicia una TransactionTemplate de escritura real en otro hilo.
2. Lee el recurso mediante servicio/repositorio real y mantiene transacción y
   contexto de persistencia abiertos. CountDownLatch señala lectura completada.
3. El hilo principal ejecuta la operación competidora y espera su commit: cierre
   o DELETE lógico por servicio real, DELETE de material por HTTP/JWT real.
4. Una segunda barrera libera la escritura pendiente; el servicio real participa
   mediante REQUIRED en la transacción envolvente y puede ver una entidad obsoleta.
5. Future.get espera commit/rollback; luego se verifica el estado con otra conexión.
   Se emiten solo estados, cantidades y nombre de excepción, nunca credenciales.

Se comprueban CONNECTION_ID distintos entre la transacción retenida y una conexión
del hilo principal. Los servicios competidores usan sus propias transacciones;
el DELETE HTTP de material corre además en el hilo Servlet. No comparten EntityManager.
No Thread.sleep: barreras, Future y límites temporales solo evitan esperas indefinidas.
finally libera la barrera y termina el executor antes de limpiar filas/archivos.
No se deshabilitan FK. La limpieza posterior se limita al contenedor propio.

**Límite importante:** se reproduce una lectura obsoleta bajo una transacción
envolvente que los servicios REQUIRED soportan, con una lectura anterior adicional.
No se intercepta una línea interna ni se prueba todavía la intercalación exacta
entre dos peticiones HTTP ordinarias sin esa transacción envolvente. El hallazgo
acredita el comportamiento en este alcance, no su frecuencia en producción ni
una reproducción HTTP simultánea completa. La selección evalúa transacciones
solapadas con orden controlado, no un benchmark de solicitudes simultáneas.

Se admite éxito inocuo o rechazo funcional/persistencia reconocido. Un error de
coordinación no se acepta como protección. Las aserciones de invariantes se
conservan si fallan; no se transforman en tests verdes de caracterización.

## Ejecución y resultados

Comandos con Java 21 configurado solo en la sesión:

```powershell
.\mvnw.cmd -B -Pintegration-mysql verify
.\mvnw.cmd -B test
```

El primer intento de integración no alcanzó los escenarios: Docker Desktop estaba
detenido y faltaba el pipe del motor. BUILD FAILURE: 0 PASS, 0 FAIL, 5 ERROR,
0 SKIPPED; tiempo Maven 11,831 s. Son errores de preparación de cinco clases, no
fallos funcionales. Se inició el Docker Desktop ya instalado, sin cambiar su
configuración; Docker Engine 29.8.2 quedó disponible y se repitió el mismo comando.

Primera ejecución con Docker disponible: 37 PASS, 3 FAIL, 0 ERROR, 0 SKIPPED;
BUILD FAILURE, Maven 1:36 min. Dos fallos corresponden a C1/C2. El tercero fue
la clasificación demasiado limitada del rechazo de C3 en el propio test.
Se agregó reconocimiento explícito de jakarta.persistence.OptimisticLockException
y diagnóstico del tipo/cadena de excepción; no se cambian las invariantes ni
se acepta un fallo arbitrario. Se repite la suite para verificar esa clasificación.

Repetición final: BUILD FAILURE, Maven **1:31 min**, finalización
2026-10-08 19:29:15 -03:00. Ambos fallos funcionales se reprodujeron en las dos
ejecuciones completas; el rechazo C3 fue exactamente OptimisticLockException,
con causas StaleObjectStateException/StaleStateException. La corrección del test
solo reconoció ese rechazo de persistencia; las aserciones de ausencia siguen intactas.

| Clase | PASS | FAIL | ERROR | SKIPPED | Tiempo |
| --- | ---: | ---: | ---: | ---: | ---: |
| AutenticacionHttpIT | 7 | 0 | 0 | 0 | 21,206 s |
| ConocimientoJpaIT | 5 | 0 | 0 | 0 | 13,352 s |
| MaterialesArchivoIT | 17 | 2 | 0 | 0 | 25,209 s |
| MySqlReferentialIntegrityIT | 1 | 0 | 0 | 0 | 7,444 s |
| ProcedimientosEjecucionesResultadosIT | 8 | 0 | 0 | 0 | 13,794 s |
| Total | 38 | 2 | 0 | 0 | Maven 1:31 min |

| Escenario nuevo | Resultado | Estado final verificado antes de limpiar fixtures |
| --- | --- | --- |
| C1 | FAIL P1 | COMPLETADA, cumplido=false, 1 fila checklist, ninguna referencia huérfana; escritura aceptada |
| C2 | FAIL P1 | PUBLICADO, 1 proyección, fila de conocimiento conservada y FK de creador válida; escritura aceptada |
| C3 | PASS | OptimisticLockException; 0 materiales, 0 archivos, proyección sin nombre pendiente, GET archivo=404 |

Regresión habitual: **472 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED**, BUILD SUCCESS;
tiempo Maven **23,922 s**, finalización 2026-10-08 19:30:02 -03:00.
Los XML corroboran ambas suites. Surefire no ejecuta las clases IT: su resultado
verde no resuelve las dos aserciones fallidas de Failsafe.
No se reejecuta indefinidamente la suite fallida ni se alteran estados para hacerla pasar.
Los diez MySQL temporales de los dos ensayos completos se comprobaron por ID
con Docker ps -a: todos eliminados. DEV/producción y storage funcional no utilizados.

## Hallazgos y clasificación

### C1 — P1: escritura de checklist posterior al cierre

Esperado: tras confirmar COMPLETADA, el checklist permanece completo; una escritura
obsoleta se rechaza o no invalida el cierre. Observado: estado COMPLETADA, un paso
con cumplido=false, una fila checklist conservada, cero referencias huérfanas y
ningún rechazo de la actualización. No es un problema FK: es integridad de estado.

Causa respaldada por código: GestionarEjecucionService.actualizarPaso (líneas 73–88)
consulta la ejecución y exigeEnCurso antes de guardar checklist, sin bloquear el
agregado. completar (91–96) consulta pasos y guarda cierre por separado. El adapter
de ejecución usa findById y una entidad sin @Version. Una transacción que conserva
la lectura anterior puede validar EN_CURSO después del commit ajeno.
Impacto: ejecución considerada completada aunque sus pasos ya no cumplan la regla.
Defecto reproducido bajo la composición transaccional ensayada; falta reproducción
de la intercalación exacta en HTTP ordinario. No se corrigió producción.

### C2 — P1: restauración por edición obsoleta tras eliminación lógica

Esperado: ELIMINADO y ninguna fila de proyección tras DELETE confirmado; PUT no
actúa como restauración. Observado: misma fila con PUBLICADO, una proyección y
ningún rechazo de la edición. Filas y FK siguen válidas, pero el contenido borrado
vuelve a poder aparecer en búsqueda. Las dos aserciones finales se conservan.

Causa respaldada por código: MantenerConocimientoService.modificar (61–74)
preserva actual.getEstado(); eliminar (89–100) guarda ELIMINADO y retira proyección.
ConocimientoRepositoryAdapter.buscarPorId filtra el estado de la entidad obtenida
por findById; no fuerza una lectura bloqueada/versionada. La entidad retenida aún
es PUBLICADO y se guarda nuevamente junto con la reindexación en la misma transacción.
Impacto: visibilidad restaurada de un conocimiento eliminado por administrador.
Defecto de estado en el alcance ensayado, no bypass HTTP demostrado.
La política general de conflicto/delete debe validarse antes de corregir.

### C3 — PASS: rechazo de fila eliminada sin residuos

El rechazo confirmado es jakarta.persistence.OptimisticLockException en el flush
JPA real, con StaleObjectStateException/StaleStateException. Hibernate detecta
que la escritura afecta una fila que ya no existe; no se exige @Version para
esta detección particular. Se comprueban después cero metadatos/archivos,
proyección sin nombre nuevo y descarga 404. Comportamiento protector permitido
por el diseño en este escenario; no es un defecto funcional abierto ni un
mecanismo general de prevención de actualizaciones perdidas en filas existentes.

## Riesgos no evaluados y decisiones necesarias

- Completar frente a cancelar, dos cambios de estado y ediciones generales de conocimiento/procedimiento.
- Creación de pasos con orden duplicado y concurrencia del catálogo/usuarios.
- Inicio mientras se agregan/modifican pasos; varias ejecuciones simultáneas válidas.
- Registro de efectividad durante cambio de estado del conocimiento e idempotencia de reintentos.
- Fallo de compensación/crash, symlink de ancestros y reconciliación de archivos.
- Intercalación HTTP exacta sin lecturas/transacciones envolventes, carga y frecuencia real.

Recomendaciones de corrección futura, si el equipo confirma invariantes: evaluar
serializar estado/checklist con bloqueo compartido del agregado, control optimista
con revalidación/reintento o actualizaciones condicionales. Para eliminación lógica,
evitar que una escritura obsoleta restaure estado; garantizar consistencia de la
proyección en la misma decisión transaccional. No introducir bloqueo/versionado
indiscriminado ni modificar negocio antes de acordar semántica de conflictos.

## Archivos y evidencia

- Modificado: `src/test/java/cl/casol/backend/integration/MaterialesArchivoIT.java`.
- Creado: este documento.
- Actualizado: `docs/auditoria-cobertura-riesgos-skill4.md`, solo con evidencias comprobadas.
- Reportes: `target/failsafe-reports/TEST-cl.casol.backend.integration.MaterialesArchivoIT.xml`
  y TXT; totales de las cinco clases; regresión en `target/surefire-reports`.
- Logs locales de ensayo en target, no versionados; conservar reportes por ejecución
  si un build futuro los sustituye. Los fallos no se omiten ni deshabilitan.

## Corrección autorizada de P1-01 / P1-02 (2026-10-09)

Las secciones anteriores registran el diagnóstico y los resultados históricos
anteriores a la corrección. La autorización posterior permite modificar únicamente
la protección transaccional de estos dos defectos, sin cambiar permisos ni reglas.

### Causa raíz confirmada y alternativas

Ambos servicios validaban entidades leídas antes del commit ajeno. Las transacciones
REQUIRED y MySQL REPEATABLE READ no invalidan por sí solos la caché de primer nivel
de JPA ni convierten una lectura previa en una validación actual/exclusiva.
El guardado por merge no incluía comparación de versión/estado. En conocimiento,
la actualización restauraba estado y la indexación JDBC recreaba la proyección
dentro de la misma transacción: atomicidad local correcta, decisión de estado obsoleta.
En ejecución, la cabecera y el checklist se validaban/escribían sin una exclusión común.

| Alternativa | Evaluación para estos dos defectos |
| --- | --- |
| Optimistic Locking | Requeriría columna/versionado y garantizar que editar checklist incremente/coordine la versión de cabecera; versionar solo pasos no protege el cierre. Mayor alcance y migración |
| Pessimistic Locking por agregado | Seleccionado: lectura actual y exclusiva por PK hasta commit/rollback. Todos los escritores de estado/cierre participan; no bloquea toda la aplicación |
| Actualización condicional | Viable para estado de una fila, pero requiere coordinar también checklist/proyección, controlar filas afectadas y caché JPA. No se selecciona para evitar mezclar varios protocolos |
| CHECK/FK/UNIQUE | Protegen referencias/duplicados, no comparan checklist de otra tabla ni evitan una restauración lógica con referencias válidas |

No se cambia la semántica de ediciones normales ni se introduce rechazo obligatorio
por concurrencia. Se preservan dos reglas ya existentes: editar/cerrar solo EN_CURSO
y no modificar/republicar conocimiento ELIMINADO. Se conserva DELETE idempotente.

### Implementación

- Puertos nuevos de lectura para actualizar; Application usa solo tipos de dominio.
- Conocimiento: modificar, cambiarEstado y eliminar adquieren la misma fila con
  lectura refrescada y bloqueo PESSIMISTIC_WRITE. Modificación/estado filtran ELIMINADO
  después de adquirir el bloqueo; eliminar puede leerlo y conservar idempotencia.
- Ejecución: actualizarPaso, completar y cancelar adquieren cabecera actual y
  exclusiva antes de autorización/validación de estado. Completar obtiene checklist
  actual bajo bloqueo antes de evaluar todos cumplidos.
- Los adaptadores hacen flush de escrituras pendientes antes de refrescar/retirar
  instancias, para conservar cambios propios de una transacción envolvente.
  Solo se recargan los elementos del checklist objetivo, sin clear global.
- Los bloqueos duran hasta el commit/rollback de la transacción propietaria,
  incluido un envolvente REQUIRED. Orden: cabecera y después checklist.
  Las consultas normales continúan sin estos bloqueos de escritura.
- JPA/JDBC siguen compartiendo transacción: el rechazo de eliminado ocurre antes
  de guardar o reindexar; DELETE retira proyección bajo la misma exclusión.

**Migraciones necesarias: ninguna.** No cambian entidades, DDL, SQL de migración,
aislamiento global, application.properties, dependencias, permisos ni contrato HTTP.
Los rechazos usan excepciones de dominio ya existentes (estado inválido / no encontrado).

### Pruebas y ensayos durante la corrección

Se conservaron íntegramente las aserciones originales de C1/C2/C3. Se añadieron
solo dos métodos: cierre con snapshot previo del checklist después de desmarcado
competidor, y operaciones sobre recursos distintos del mismo usuario mientras
otro recurso mantiene su bloqueo. Este último comprueba exclusión por recurso,
no certifica todos los patrones de contención.
Los tests unitarios existentes se adaptaron a los puertos de escritura; se
mantienen expectativas/reglas, y el fixture ELIMINADO comprueba el filtro actual.

Primer intento: Docker detenido, 5 ERROR de preparación, sin escenarios ejecutados;
BUILD FAILURE en 26,359 s. Se inició el Docker Desktop instalado sin reconfigurarlo.
Primer ensayo con Docker: 41 PASS, 1 FAIL, 0 ERROR/SKIPPED, Maven 1:51 min.
Las dos aserciones originales ya pasaron; la prueba nueva inversa detectó que
un checklist previamente gestionado seguía devolviendo datos obsoletos. Se ajustó
la recarga del checklist, sin cambiar la aserción ni la regla de cierre.

Resultados finales verificados en TXT/XML:

| Suite / clase | PASS | FAIL | ERROR | SKIPPED | Tiempo |
| --- | ---: | ---: | ---: | ---: | --- |
| AutenticacionHttpIT | 7 | 0 | 0 | 0 | 18,600 s |
| ConocimientoJpaIT | 5 | 0 | 0 | 0 | 12,048 s |
| MaterialesArchivoIT | 21 | 0 | 0 | 0 | 23,633 s |
| MySqlReferentialIntegrityIT | 1 | 0 | 0 | 0 | 7,933 s |
| ProcedimientosEjecucionesResultadosIT | 8 | 0 | 0 | 0 | 13,758 s |
| Integración total | 42 | 0 | 0 | 0 | Maven 1:27 min |
| Habitual | 472 | 0 | 0 | 0 | Maven 23,216 s |

Ambas ejecuciones: BUILD SUCCESS. Integración finalizada 2026-10-09 10:50:03 -03:00;
regresión 10:51:04 -03:00. Comandos completos indicados arriba, sin omitir escenarios.

| Escenario después de corregir | Resultado y estado final |
| --- | --- |
| C1 / P1-01 original | PASS: actualización obsoleta rechazada con ReglaEjecucionException; COMPLETADA, cumplido=true, una fila checklist y ninguna referencia huérfana |
| C2 / P1-02 original | PASS: edición obsoleta rechazada con ConocimientoNoEncontradoException; ELIMINADO y cero proyecciones |
| Caso inverso del checklist | PASS: cierre rechaza checklist desmarcado pese al snapshot previo; EN_CURSO, cumplido=false |
| Recursos independientes | PASS: modificar otro conocimiento y cancelar otra ejecución del mismo usuario termina mientras el primer recurso conserva su bloqueo |
| C3 material | PASS: protección previa se conserva; OptimisticLockException, cero metadatos/archivos y descarga 404 |

Los diez contenedores MySQL de ambos ensayos con Docker se identificaron por log
y se comprobaron individualmente con Docker ps -a: todos eliminados. No hubo
conexiones a DEV/producción ni modificaciones de sus datos. El almacenamiento
fue exclusivamente temporal. No quedan aserciones suavizadas/deshabilitadas:
los fallos históricos ahora pasan con la corrección de producción autorizada.

### Archivos de la corrección

- Conocimiento: `application/port/out/ConocimientoRepository.java`,
  `application/service/MantenerConocimientoService.java`,
  `infrastructure/persistence/adapter/ConocimientoRepositoryAdapter.java`.
- Ejecución: `application/port/out/EjecucionRepository.java`, `EjecucionPasoRepository.java`,
  `application/service/GestionarEjecucionService.java`,
  `infrastructure/persistence/adapter/EjecucionRepositoryAdapter.java`, `EjecucionPasoRepositoryAdapter.java`.
- Tests: `integration/MaterialesArchivoIT.java`,
  `conocimiento/application/service/MantenerConocimientoServiceTest.java`,
  `ejecucion/application/service/GestionarEjecucionServiceTest.java`.
- Documentación: este documento y `auditoria-cobertura-riesgos-skill4.md`.

Riesgos residuales: transacciones envolventes largas retienen bloqueos; timeouts/deadlocks
requieren política operativa futura. No se introducen reintentos automáticos ni
se certifican combinaciones HTTP simultáneas/carga, modificación de definición de
pasos, registro de resultados concurrente con estado, otras carreras de archivos,
CI, provisión o configuración productiva. No se declara GO de producción.
