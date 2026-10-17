# QA-012 — Implementación de Departamentos y Contactos

Fecha: 2026-10-14. Entorno exclusivamente local. Referencias: QA012_DIAGNOSTICO_DEPARTAMENTOS.md y QA012_DISENO_PERSISTENCIA.md.

## 1. Resultado y alcance

Se implementaron consultas y comandos administrativos persistentes mediante Spring Data JPA, y Angular utiliza HTTP para crear, editar y cambiar estados. Se reutilizó la pantalla `/departamentos`; no se creó un catálogo duplicado. La implementación respeta Controller → Application Service → Port → Persistence Adapter.

El código compila y las pruebas locales pasan. **La persistencia física y las transacciones InnoDB todavía requieren validación en un MySQL aislado autorizado. No se ejecutó la migración ni se inició el nuevo Backend contra STAGING/PRODUCCIÓN.** Las nuevas columnas son un requisito previo para utilizar este Backend, incluidas las lecturas de las entidades organizacionales.

Se preservaron los trabajos locales anteriores y la rama `catalina`. El repositorio padre ya contenía numerosos cambios sin commit, incluidos QA-009/QA-010/QA-011 y otros trabajos. No se revirtieron ni se adjudican a QA-012 todos los archivos que aparecen en `git status`. No se hicieron commits, push, resets, stash, clean, despliegues ni operaciones sobre servidores o bases de datos.

**Decisión explícita del usuario:** “Implementar CRUD y dejar el ajuste pendiente”. No se modificó `GestionarAsignacionSolucionService` durante QA-012. Su diff preexistente corresponde al trabajo anterior. No se implementó CRUD de Responsables ni se modificaron sus entidades, endpoints o permisos.

## 2. Contrato REST implementado

Todos los endpoints nuevos se encuentran bajo `/api/admin/**`, protegido por el control Backend existente de ADMINISTRADOR. Las consultas operacionales conservan sus permisos y contratos.

| Método | Endpoint | Función |
|---|---|---|
| GET | `/api/admin/departamentos` | Todos los departamentos, incluidos inactivos. |
| GET | `/api/admin/departamentos/{id}` | Departamento administrativo. |
| POST | `/api/admin/departamentos` | Crear activo; 201 y Location. |
| PUT | `/api/admin/departamentos/{id}` | Editar exclusivamente nombre con versión. |
| PATCH | `/api/admin/departamentos/{id}/estado` | Desactivar/reactivar con versión. |
| GET | `/api/admin/departamentos/{departamentoId}/contactos` | Contactos propios activos/inactivos, incluso con padre inactivo. |
| GET | `/api/admin/departamentos/{departamentoId}/contactos/{id}` | Contacto propio. |
| POST | `/api/admin/departamentos/{departamentoId}/contactos` | Crear en ese departamento; 201 y Location. |
| PUT | `/api/admin/departamentos/{departamentoId}/contactos/{id}` | Editar tipo/valor con versión. |
| PATCH | `/api/admin/departamentos/{departamentoId}/contactos/{id}/estado` | Desactivar/reactivar con versión. |

No hay DELETE administrativo. La ruta sin implementación devuelve 405 para ADMINISTRADOR autenticado.

Ejemplos de bodies:

```json
{"nombre":"Soporte TI"}
```

```json
{"nombre":"Soporte Técnico","version":3}
```

```json
{"activo":false,"version":3}
```

```json
{"tipo":"Anexo","valor":"618","activo":false}
```

```json
{"tipo":"Anexo","valor":"619","version":2}
```

POST de Contactos omite `activo` para crear activo por defecto; bajo padre inactivo debe enviarse `false`. El PUT de Contactos no acepta padre ni estado. Los DTO rechazan propiedades ajenas, por ejemplo `activo` en edición de nombre o `departamentoId` en edición de Contactos.

Las respuestas administrativas incluyen `version`. Contactos añade `departamentoActivo` y `disponible` (activo propio y padre activo). Los DTO operacionales no incorporan estos campos internos.

Respuestas: 400 por validación/body ajeno/versión ausente, 401 sin JWT, 403 por rol, 404 por padre o contacto inexistente/ajeno, 409 por duplicado/transición prohibida/versión obsoleta o conflicto de integridad. Los fallos de infraestructura no se presentan como errores de validación.

## 3. Reglas de negocio y concurrencia

- Departamento: nombre obligatorio, máximo 255 conforme al mapeo JPA actual; se recortan espacios exteriores. Unicidad con comparación Java sin distinguir mayúsculas y espacios exteriores, incluyendo registros inactivos.
- Un departamento puede tener múltiples contactos; cada contacto pertenece a un único departamento. No existe ni se creó N:M.
- Contacto: tipo obligatorio máximo 50, valor obligatorio máximo 100. `valor` es texto, no un número. Se impide repetir tipo (ignorando mayúsculas) y valor (comparación exacta, recortada) dentro del mismo departamento, incluyendo inactivos. No se impone unicidad global de nombres personales ni normalización telefónica/correo no acordada.
- Desactivar/reactivar un departamento solo modifica su estado. No modifica estados de contactos ni elimina asignaciones o conocimientos. Un contacto que permanece activo bajo padre inactivo queda indisponible; recupera disponibilidad al reactivar el padre.
- No se crean contactos activos ni se reactivan contactos inactivos bajo un departamento inactivo. Se permite registrar contactos inicialmente inactivos bajo ese padre y editar sus datos sin moverlos ni cambiar su estado.
- Las consultas operacionales existentes exigen padre activo y consultan solo hijos activos. Un padre inactivo devuelve 404 en GET operacional de hijos.
- Las referencias históricas permanecen. Los nombres históricos siguen siendo referencias a datos actuales, no snapshots: editar un nombre puede cambiar su presentación en históricos.
- Se conserva la validación existente de `departamentoId` explícito en asignaciones. La validación cuando se envía **solo `responsableId`** queda pendiente por decisión del usuario.

Las escrituras nuevas son transaccionales. Primero bloquean con PESSIMISTIC_WRITE la fila singleton de `u_organizacion_control`, después el departamento cuando corresponde. La fila global permite serializar la validación de duplicados incluso con el catálogo vacío y entre instancias de la aplicación. Se refrescan las entidades bloqueadas. El bloqueo se libera al completar la transacción.

`@Version` en Departamento y Contacto impide sobrescribir una edición concurrente; PUT/PATCH requieren la versión leída. Un estado idéntico no reescribe la entidad, pero puede solicitar de nuevo la reindexación.

Este protocolo protege a escritores que usan el nuevo servicio; **no sustituye una restricción UNIQUE física frente a escritores SQL externos**. No se agregó un índice sobre tablas existentes sin confirmar duplicados/collation/coste de DDL. La fila global limita el paralelismo administrativo; evaluar duración y esperas en MySQL real antes de mayor carga.

Al editar nombre/estado de Departamento se emite un evento AFTER_COMMIT para reindexar conocimientos referenciados por asignaciones directas o por Responsables del departamento, excluyendo conocimientos ELIMINADO. La reindexación usa otra transacción, después de liberar los bloqueos organizacionales. Los Contactos no forman parte del índice actual y no disparan reindexación.

Si falla la reindexación, el CRUD confirmado permanece guardado y se registra el fallo. No se incorporó una cola durable de reintentos: revisar el log y repetir PATCH con estado vigente y versión actual, o utilizar el proceso de reindexación aprobado. Un cierre del proceso tras el commit puede dejar el índice pendiente. La respuesta CRUD por sí sola no certifica que el índice haya terminado.

## 4. Angular y datos anteriores del navegador

ADMINISTRADOR dispone de alta, edición, desactivación/reactivación de Departamentos y Contactos, confirmación de estados y mensajes del servidor. Los demás roles con acceso operacional a la pantalla conservan lectura; no ven controles administrativos y los métodos de escritura comprueban el rol.

Después de guardar se invalidan las cachés operacionales en memoria y se recarga el catálogo administrativo y el detalle abierto. Ante error se mantiene el formulario o la confirmación, sin ocultar filas ni simular éxito. Los errores parciales de Contactos/Responsables son visibles. El listado incluye inactivos para administración. No se agregaron filtros fuera del alcance acordado.

El panel Administrador incluye acceso a “Departamentos y Contactos”. Se conservaron rutas, guards, autenticación y permisos operacionales. Los estilos añadidos están limitados al componente y permiten ajustar cabecera/acciones en pantallas pequeñas; la UAT visual en dispositivos reales sigue pendiente.

La administración dejó de usar estado/ocultamiento local de Departamentos. Las claves anteriores **no se borran ni se importan**:

- `dimabug.departamentos.detalle.v1`, en localStorage y sessionStorage.
- `dimabug.departamentos.ocultos.v1`, en ambos almacenamientos.
- Colección `departamentos` de `dimabug.catalogos.v2`, en localStorage.

Antes de limpiar o cambiar el navegador, un ADMINISTRADOR debe abrir Departamentos y pulsar **Respaldar datos locales anteriores** si aparece el aviso. Se descarga `dimabug-departamentos-local-respaldo.json`, con origen y datos originales, sin exportar otras colecciones. Puede inspeccionarse también mediante DevTools → Application → Local Storage / Session Storage. Los IDs y flags locales no se consideran IDs/estados confirmados de MySQL. No hay importación ni limpieza automática.

Los métodos legacy de Departamentos en `CatalogoService` ya no tienen consumidores administrativos; se conservó el almacén compartido para no modificar otros catálogos. La administración de Pruebas no se tocó.

## 5. Migración preparada, no ejecutada

Archivo:

`C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/src/main/resources/db/migration/V20261014_01__organizacion_persistente.sql`

Añade `departamento_version BIGINT NOT NULL DEFAULT 0`, `contacto_version BIGINT NOT NULL DEFAULT 0` y crea `u_organizacion_control` con fila 1. No cambia FK, relaciones o estados existentes. No incorpora Flyway/Liquibase ni dependencias. El nombre/directorio del archivo no implica que se haya ejecutado automáticamente.

Los ALTER exigen ALGORITHM=INSTANT y limitan la espera de metadata lock a 5 segundos. Si no es compatible o no consigue el lock, detenerse: no reemplazar por COPY/INPLACE sin evaluación y aprobación. MySQL DDL confirma cada statement; el archivo no es atómico ni idempotente. Una ejecución parcial requiere inspeccionar qué objetos existen antes de continuar; no repetir a ciegas. La variable de sesión debe restaurarse también si la herramienta interrumpe el script ante un error.

Preflight de solo lectura para una futura revisión autorizada (no ejecutado aquí):

```sql
SELECT VERSION(), @@transaction_isolation;
SHOW CREATE TABLE u_departamento;
SHOW CREATE TABLE u_departamento_contacto;
SHOW CREATE TABLE u_responsable;
SHOW CREATE TABLE co_solucion_asignacion;
SHOW INDEX FROM u_departamento;
SHOW INDEX FROM u_departamento_contacto;
SELECT LOWER(TRIM(departamento_nombre)) AS nombre_normalizado, COUNT(*) AS cantidad
FROM u_departamento GROUP BY LOWER(TRIM(departamento_nombre)) HAVING COUNT(*) > 1;
SELECT contacto_id FROM u_departamento_contacto c
LEFT JOIN u_departamento d ON d.departamento_id=c.departamento_id
WHERE d.departamento_id IS NULL;
```

Comprobar InnoDB, versión y límites INSTANT, objetos ausentes, longitudes físicas, collation, FK/ON DELETE y datos existentes. LOWER/TRIM SQL sirve para detectar candidatos; revisar diferencias respecto a comparación Unicode Java. No se confirmó ninguna restricción física MySQL. No se corrigen automáticamente duplicados u huérfanos.

Antes de futura ejecución: respaldo autorizado y prueba de restauración en entorno aislado, revisión de esperas y resultado por statement. Orden futuro: esquema aditivo revisado → Backend → Angular. La migración de QA-009 también continúa pendiente. Rollback de aplicación: evaluar retorno a artefactos anteriores dejando columnas/tabla aditivas; no preparar DROP ni eliminar datos.

## 6. Archivos de QA-012

Raíces de las siguientes rutas:

- Backend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/`.
- Angular/documentación: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/`.

### Backend

Todos los Java siguientes tienen prefijo `src/main/java/cl/casol/backend/`.

| Ruta | Cambio |
|---|---|
| `identidad/domain/DepartamentoAdministrativo.java` | Nuevo modelo administrativo con versión. |
| `identidad/domain/ContactoDepartamentoAdministrativo.java` | Nuevo modelo administrativo con pertenencia/versión. |
| `identidad/domain/exception/OrganizacionConflictoException.java` | Nuevo conflicto de negocio. |
| `identidad/domain/exception/OrganizacionInvalidaException.java` | Nueva validación. |
| `identidad/domain/exception/ContactoDepartamentoNoEncontradoException.java` | Nuevo 404 de contacto. |
| `identidad/application/port/out/AdministracionOrganizacionRepository.java` | Nuevo puerto de lectura/escritura/bloqueo. |
| `identidad/application/port/out/ReferenciasOrganizacionRepository.java` | Nuevo puerto de referencias para índice. |
| `identidad/application/service/AdministrarOrganizacionService.java` | Nuevo CRUD transaccional. |
| `identidad/application/service/OrganizacionActualizada.java` | Nuevo evento. |
| `identidad/application/service/ReindexarOrganizacionListener.java` | Nuevo listener postcommit. |
| `identidad/application/service/ReindexarOrganizacionService.java` | Nuevo caso de reindexación. |
| `identidad/infrastructure/persistence/adapter/AdministracionOrganizacionRepositoryAdapter.java` | Nuevo adaptador JPA. |
| `identidad/infrastructure/persistence/adapter/ReferenciasOrganizacionJdbcAdapter.java` | Nueva consulta de referencias. |
| `identidad/infrastructure/persistence/entity/DepartamentoEntity.java` | Versionado y constructor de persistencia. |
| `identidad/infrastructure/persistence/entity/DepartamentoContactoEntity.java` | Versionado y constructor de persistencia. |
| `identidad/infrastructure/persistence/entity/OrganizacionControlEntity.java` | Nueva fila de exclusión administrativa. |
| `identidad/infrastructure/web/AdminDepartamentoController.java` | Nuevo REST administrativo. |
| `identidad/infrastructure/web/AdminDepartamentoContactoController.java` | Nuevo REST anidado. |
| `identidad/infrastructure/web/dto/CrearDepartamentoRequest.java` | Nuevo request validado. |
| `identidad/infrastructure/web/dto/EditarDepartamentoRequest.java` | Nuevo request con versión. |
| `identidad/infrastructure/web/dto/EstadoOrganizacionRequest.java` | Nuevo cambio de estado con versión. |
| `identidad/infrastructure/web/dto/CrearContactoDepartamentoRequest.java` | Nuevo request validado. |
| `identidad/infrastructure/web/dto/EditarContactoDepartamentoRequest.java` | Nuevo request sin traslado/estado. |
| `identidad/infrastructure/web/dto/ContactoDepartamentoAdminResponse.java` | Nueva respuesta con disponibilidad. |
| `shared/infrastructure/web/GlobalExceptionHandler.java` | Mapeo de errores nuevos y conflicto optimista. |

Además del SQL anterior, pruebas nuevas con prefijo `src/test/java/cl/casol/backend/`:

- `AdminOrganizacionSecurityIntegrationTest.java`.
- `identidad/application/service/AdministrarOrganizacionServiceTest.java`.
- `identidad/application/service/OrganizacionConcurrenciaTest.java`.
- `identidad/application/service/OrganizacionOperacionalQa012Test.java`.
- `identidad/application/service/ReindexarOrganizacionTest.java`.
- `identidad/infrastructure/persistence/adapter/AdministracionOrganizacionRepositoryAdapterTest.java`.

### Angular

- `src/app/core/models/catalogo.model.ts`: ID opcional del contacto operacional.
- `src/app/core/models/organizacion-admin.model.ts`: nuevos contratos administrativos.
- `src/app/core/services/organizacion.service.ts`: eliminación de estado/ocultación local de Departamentos, HTTP y invalidación de caché.
- `src/app/core/services/admin-organizacion-api.service.ts`: nuevo cliente HTTP.
- `src/app/core/services/respaldo-departamentos-local.service.ts`: nuevo respaldo de solo lectura.
- `src/app/features/departamentos/departamentos.component.ts`: comandos y carga persistente por rol.
- `src/app/features/departamentos/departamentos.component.html`: formularios/contactos/estados/confirmaciones/respaldo.
- `src/app/features/departamentos/departamentos.component.css`: estilos limitados al módulo.
- `src/app/features/admin/admin.component.html`: acceso a gestión persistente.
- `src/app/core/services/admin-organizacion-api.service.spec.ts`.
- `src/app/core/services/respaldo-departamentos-local.service.spec.ts`.
- `src/app/features/departamentos/departamentos.component.spec.ts`.
- `docs/QA012_IMPLEMENTACION_DEPARTAMENTOS.md`: este informe.

## 7. Validación ejecutada

| Comando local | Resultado final |
|---|---|
| Backend, Java 21: `mvnw.cmd -o verify` | **612 pruebas; 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS**, JAR generado. |
| Angular: `npm test -- --watch=false --browsers=ChromeHeadless` | **112 pruebas satisfactorias**, salida 0. |
| Angular: `npm run build -- --configuration production` | **Correcto**, salida 0; inicial 1,36 MB. |
| `git diff --check` limitado a archivos QA-012 modificados | Sin incidencias. |

Se añadieron 47 casos Backend y 23 Angular respecto a la base de 565/89 de QA-011. La suite completa conserva cobertura de Soluciones QA-009, Sistemas/Hardware QA-010 y Módulos QA-011.

Cobertura nueva: reglas de nombres/duplicados, estado/versionado, pertenencia Departamento–Contacto, ausencia de cascada al cambiar estado, validación/errores REST, ADMINISTRADOR frente a TECNICO/SOPORTE/USUARIO y sin JWT, ausencia de DELETE, filtrado operacional, mapeo del adaptador, reindexación postcommit, HTTP Angular, errores y refresco, controles por rol y respaldo no destructivo.

Concurrencia: dos altas del mismo nombre en catálogo vacío dejan un ganador; dos ediciones de la misma versión no se sobrescriben; un alta de contacto que espera desactivación ve padre inactivo y se rechaza. Son pruebas con puerto simulado y ReentrantLock, **no pruebas InnoDB**. Las pruebas del adaptador usan mocks de JPA; MockMvc prueba contrato/seguridad, no almacenamiento físico.

Advertencias persistentes:

- Producción Angular: CSS Inicio 18,62 kB y Detalle de Conocimiento 22,53 kB, superiores al warning de 16 kB; las dos advertencias preexistentes permanecen. No se cambiaron budgets ni optimizaciones.
- Suite Angular: aviso existente de disabled con formulario reactivo, asset del logo 404 en Karma y finalización de ChromeHeadless tras timeout de cierre. Las pruebas completaron y el proceso salió 0.
- Backend: aviso Mockito de auto-attachment del agente y avisos de contextos de pruebas; no son fallos de la compilación.

Artefactos locales:

- JAR: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar`.
- HTML: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/dist/buscador-errores/browser/index.html`.
- Logs: Backend `target/qa012-verify.log`; Angular `.angular/cache/qa012-tests.log` y `.angular/cache/qa012-build.log`. No añadir logs temporales al despliegue.

Se inspeccionó el JAR con `jar tf`: contiene `BOOT-INF/classes/application.properties`, ambos controladores administrativos y el archivo SQL QA-012. Incluir el SQL como recurso no significa ejecutarlo. No se lanzó el JAR contra una base de datos.

## 8. Riesgos y validaciones pendientes

1. **Responsables:** si se envía solo un `responsableId` activo cuyo padre esté inactivo, el flujo actual no comprueba ese padre. También está pendiente coordinar la nueva asignación con desactivación concurrente. El usuario decidió aplazar este ajuste fuera del CRUD. La exclusión de responsables de padres inactivos en GET/selectores no reemplaza la protección Backend pendiente. No se afirma que esta regla esté resuelta integralmente.
2. **MySQL:** confirmar esquema real y probar persistencia entre sesiones, FK, versionado/merge JPA, rollback transaccional, bloqueo global/parental con varias conexiones y tiempos de espera. No se conectó a STAGING/PRODUCCIÓN ni se ejecutaron migraciones.
3. **Integridad:** las nuevas escrituras del CRUD validan duplicados, pero registros ya duplicados no se depuran y escritores externos pueden evitar el protocolo. No hay índice UNIQUE nuevo. Auditar duplicados y referencias antes de considerar un índice, con autorización del DDL necesario.
4. **Histórico:** validar en UAT que desactivar/editar organización conserva asignaciones/resultados y solo cambia disponibilidad/presentación actual. No se implementó auditoría durable ni snapshots históricos nuevos; `@Version` es control de concurrencia, no auditoría.
5. **Índice:** validar actualización tras nombre/estado, recuperación de fallos postcommit y comportamiento bajo escrituras concurrentes con Conocimiento. No hay outbox/cola durable.
6. **UAT pendiente:** dos navegadores/usuarios; crear/editar/estados/contactos; parent inactivo con hijo activo; reactivación sin cascada; conflicto 409 y recarga; técnico sin escritura; revisión del respaldo y ausencia de importación; móvil/teclado/zoom. Conservar respaldo del navegador antes de cualquier limpieza manual.
7. **Despliegue pendiente:** coordinar esquema QA-009 y QA-012, Backend y Angular. No usar el JAR nuevo contra esquema anterior. Este trabajo no autoriza despliegue ni ejecución de SQL.

QA-012 queda implementado localmente para revisión, con los límites indicados. No se avanzó a Pruebas, Pasos o Procedimientos.
