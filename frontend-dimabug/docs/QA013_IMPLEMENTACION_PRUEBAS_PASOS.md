# QA-013 — Implementación local de Pruebas y Pasos

Fecha: 2026-10-14, America/Santiago. Referencia: QA013_DIAGNOSTICO_PRUEBAS_PASOS.md.

## 1. Resultado

Se implementó administración persistente del catálogo reutilizable de Pruebas: consulta completa, alta, edición, desactivación y reactivación. Angular reutiliza `/pruebas`, sin duplicar componentes; muestra controles de escritura exclusivamente para ADMINISTRADOR y usa HTTP. Se conserva resultado esperado como campo opcional según el modelo existente.

Las asociaciones de un conocimiento ahora incluyen también Pruebas inactivas con ID, descripción, resultado esperado, orden e indicador `activa`. Angular las marca como **Inactiva · referencia histórica**. El catálogo para nuevas asociaciones sigue consultando exclusivamente activas; Backend también valida esa disponibilidad.

Pasos conserva sus endpoints y pertenencia exclusiva al Procedimiento. Se corrigieron posición vs orden, validación de entero positivo y mensajes HTTP. Solo se confirma éxito después del guardado; se conserva el formulario ante error. La regla BORRADOR permanece en Backend y se refuerza en los métodos Angular.

Trabajo exclusivamente local, rama `catalina`. Había 182 entradas de estado Git preexistentes al comenzar, incluidos QA-009 a QA-012 y otros trabajos. No se hicieron commits, resets, stash, clean ni despliegues. No se modificó ninguna base de datos ni se ejecutó SQL. No se avanzó a QA-014.

## 2. Endpoints

### Nuevos, exclusivos de ADMINISTRADOR

| Método | Endpoint | Contrato |
|---|---|---|
| GET | `/api/admin/pruebas` | Todas las Pruebas, activas/inactivas, ordenadas por descripción e ID. |
| GET | `/api/admin/pruebas/{id}` | Prueba por ID; 404 si no existe. |
| POST | `/api/admin/pruebas` | Crear activa; 201 y Location. |
| PUT | `/api/admin/pruebas/{id}` | Editar descripción/resultado, preservando estado. |
| PATCH | `/api/admin/pruebas/{id}/estado` | Desactivar/reactivar, preservando definición y asociaciones. |

POST/PUT:

```json
{"descripcion":"Comprobar conectividad","resultadoEsperado":"Respuesta sin pérdida"}
```

PATCH:

```json
{"activa":false}
```

Respuesta administrativa:

```json
{"id":7,"descripcion":"Comprobar conectividad","resultadoEsperado":"Respuesta sin pérdida","activa":false}
```

No hay DELETE. Estado no puede inyectarse en POST/PUT: los DTO rechazan campos ajenos. `resultadoEsperado` omitido, nulo o blanco queda nulo. PUT conserva la semántica de edición de ambos campos: omitir resultado esperado permite vaciarlo; Angular siempre lo envía.

La protección usa el matcher Backend existente `/api/admin/**` de SecurityConfig, sin modificar autenticación o permisos operacionales. Se probaron ADMINISTRADOR, TECNICO, SOPORTE, USUARIO y ausencia de JWT. HTTP: 400 validación, 401 sin autenticar, 403 por rol, 404 ausencia, 409 duplicado/integridad.

### Reutilizados

- GET `/api/pruebas`: conserva DTO y filtro de activas para asociación operacional.
- GET `/api/conocimientos/{id}/pruebas`: ahora incluye asociaciones activas e inactivas, con campo **aditivo** `activa`; orden por asociación e ID como desempate. No se removieron campos previos.
- POST `/api/conocimientos/{id}/pruebas`: preservado; prueba activa obligatoria y rechazo de duplicado del mismo ID. Se añadió bloqueo de la Prueba antes de comprobar disponibilidad.
- PUT `/api/conocimientos/{id}/pruebas/{pruebaId}`: preserva modificación del orden; no edita el catálogo global ni elimina asociaciones.
- GET/POST/PUT `/api/procedimientos/{id}/pasos` y `/{pasoId}`: sin cambios de contrato/endpoints Backend.
- Material por Paso y ejecución/checklist: se conservaron sus recursos y permisos.

La consulta de asociaciones continúa respetando la visibilidad del conocimiento y exclusión existente de ELIMINADO. Este trabajo no añade acceso nuevo a conocimientos eliminados. “Conservación histórica” significa conservar vínculos y mostrar inactivos en conocimientos accesibles; no crea snapshots o una nueva API de archivo de conocimientos eliminados.

## 3. Reglas y protección de integridad

- Descripción obligatoria, máximo 500 caracteres; recorte de espacios exteriores.
- Resultado esperado opcional, máximo 500; blancos se normalizan a nulo.
- Se comprueba descripción duplicada exacta después de recortar espacios, incluyendo inactivas. No se introdujo una regla nueva de unicidad ignorando mayúsculas: el diagnóstico no la aprobaba y la collation física no está confirmada. Una restricción MySQL más estricta, si existe, produce el 409 de integridad existente.
- Alta activa por defecto. PUT no sobrescribe el estado administrativo. Edición de una definición inactiva es permitida para administración; no la reactiva ni cambia sus vínculos.
- Desactivar/reactivar solo cambia prueba_estado; no escribe, mueve ni elimina co_conocimiento_prueba. No hay eliminación física.
- Cada Prueba sigue siendo reutilizable en varios conocimientos. Edición global actualiza la presentación de sus usos; el formulario informa ese alcance. Se preservan los registros/vínculos, no versiones antiguas de textos.
- Prueba inactiva no aparece en GET operativo del catálogo ni puede recibir una nueva asociación. La selección Angular refresca el catálogo operativo al abrir el modal; una selección que quedó obsoleta también es rechazada por Backend.
- Órdenes de asociaciones mantienen reglas previas: mínimo 1 por DTO, default 1 si falta, empates/saltos permitidos. No se agregó continuidad, UNIQUE por orden, desvinculación ni reordenamiento colectivo.
- PUT de orden conserva la regla existente incluso si la asociación apunta a una Prueba inactiva; no constituye una nueva asignación. No se introdujo prohibición nueva no acordada.
- Pasos mantiene orden único por Procedimiento y cambios solo en BORRADOR. No se agregaron estado individual, DELETE, reapertura de PUBLICADO ni una regla nueva de pasos críticos/publicación sin pasos.

El guardado administrativo es transaccional y bloquea pesimistamente la Prueba existente. La nueva asociación utiliza el mismo bloqueo y refresco antes de validar activa. Esto ordena desactivación y asociación entre solicitudes que siguen esos servicios: una asociación confirmada antes de desactivar permanece histórica; una que espera a la desactivación debe ver inactiva y rechazarse. **Esta estrategia necesita validación con varias conexiones InnoDB; las pruebas locales no sustituyen esa verificación.**

No se añadió @Version ni versión en requests; el bloqueo serializa escrituras, pero no detecta formulario obsoleto en dos ediciones de texto. El control previo de duplicados tampoco reemplaza un UNIQUE físico frente a dos altas concurrentes/escritores externos. No se afirma unicidad o concurrencia física no comprobada.

## 4. Índice y cachés

Editar definición o cambiar estado publica PruebaActualizada. Después del commit se reindexan los conocimientos que la referencian y siguen accesibles/no eliminados, usando nueva transacción. El índice SQL existente ya excluye pruebas inactivas; se conserva ese filtro. No se borran relaciones históricas para actualizar búsquedas.

La reindexación ocurre después de liberar el bloqueo de Prueba. Si falla, no se revierte el CRUD confirmado; queda un error de log y se puede reintentar PATCH con su estado vigente. No existe cola durable/outbox: fallo o cierre del proceso después del commit puede dejar índice pendiente. La respuesta exitosa del CRUD no garantiza por sí sola la actualización del índice.

Angular invalida el catálogo operativo en memoria tras escritura exitosa y recarga el listado administrativo y detalle abierto. La asociación consulta de nuevo el catálogo operativo al abrir. Otras pestañas/navegadores necesitan recarga; no se agregó sincronización en tiempo real. No se modificaron caches de Soluciones u otros catálogos.

## 5. Angular y respaldo local

La pantalla de Pruebas ofrece Añadir, Editar, Desactivar/Reactivar, confirmación de estado, detalle y resultado esperado. El estado administrativo y el contador proceden de HTTP; errores no se sustituyen por una lista local antigua ni se anuncia persistencia si la consulta falla. Usuarios no administradores conservan consulta operacional y no ven ni pueden activar los métodos de escritura del componente.

Guardar mantiene estado de carga y no permite repetir la solicitud mientras está pendiente. Solo tras respuesta exitosa se cierra el formulario, se muestra confirmación y se recarga; el error conserva datos/formulario o confirmación. El modal de asociación en Conocimiento también espera el POST antes de actualizar lista/cache y anunciar éxito.

Pasos muestra **Paso 1 de 2 · Orden 4**, separando posición del valor almacenado. No renumera datos existentes ni exige que 4/10 sean 1/2. El formulario rechaza fracciones, cero, negativos y texto de instrucción vacío antes de HTTP; Backend conserva Bean Validation y BORRADOR. ProcedimientoService preserva el mensaje de conflicto 409 del Backend y usa un fallback genérico si falta, sin convertirlo siempre en orden duplicado. El rechazo de edición PUBLICADO sigue siendo 400 con el mensaje actual del servidor.

Se retiraron las escrituras/lecturas operativas de estas claves antiguas de Pruebas, **sin borrarlas**:

- `dimabug.pruebas.lista.v1`.
- `dimabug.pruebas.activo.v1`.
- `dimabug.pruebas.ocultos.v1`.
- Colección `pruebas` de `dimabug.catalogos.v2`.

No puede inspeccionarse el navegador personal desde los comandos de la suite. El nuevo servicio de respaldo comprueba las tres claves en sessionStorage/localStorage y la colección legacy en localStorage, conservando incluso JSON ilegible para revisión. Las listas antiguas pueden ser caché y `catalogos.v2.pruebas` puede contener registros temporales/seed; ningún ID o flag local se considera confirmado en MySQL.

Antes de limpiar el navegador o cerrar una sesión con sessionStorage, ADMINISTRADOR puede abrir `/pruebas` y pulsar **Respaldar datos locales anteriores** si aparece el aviso. Descarga `dimabug-pruebas-local-respaldo.json`. También puede inspeccionar DevTools → Application → Local Storage / Session Storage. El respaldo no exporta otras colecciones del almacén compartido. No hay importación ni limpieza automática. El servicio CatalogoService conserva métodos legacy sin consumidores actuales para no modificar otros catálogos.

Los estilos nuevos están acotados a Pruebas, con cabecera adaptable y acciones que pueden envolver. La UAT visual de móvil/teclado/zoom aún debe realizarse.

## 6. Archivos de QA-013

Raíz Backend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/`.
Prefijo Java producción: `src/main/java/cl/casol/backend/`.

Nuevos:

- `conocimiento/application/port/out/PruebaAdministrativaRepository.java`.
- `conocimiento/application/service/AdministrarPruebasService.java`.
- `conocimiento/application/service/PruebaActualizada.java`.
- `conocimiento/application/service/ReindexarPruebaService.java`.
- `conocimiento/application/service/ReindexarPruebaListener.java`.
- `conocimiento/domain/exception/PruebaInvalidaException.java`.
- `conocimiento/domain/exception/PruebaConflictoException.java`.
- `conocimiento/infrastructure/persistence/adapter/PruebaAdministrativaRepositoryAdapter.java`.
- `conocimiento/infrastructure/web/AdminPruebaController.java`.
- `conocimiento/infrastructure/web/dto/GuardarPruebaAdministrativaRequest.java`.
- `conocimiento/infrastructure/web/dto/EstadoPruebaRequest.java`.

Modificados:

- `conocimiento/application/port/out/PruebaRepository.java`: bloqueo de disponibilidad.
- `conocimiento/application/port/out/ConocimientoPruebaRepository.java`: lectura de todas las asociaciones.
- `conocimiento/application/service/GestionarPruebasConocimientoService.java`: historial y bloqueo previo a asociación.
- `conocimiento/infrastructure/persistence/adapter/PruebaRepositoryAdapter.java`: bloqueo y refresh.
- `conocimiento/infrastructure/persistence/adapter/ConocimientoPruebaRepositoryAdapter.java`: lectura sin filtro de estado.
- `conocimiento/infrastructure/persistence/entity/PruebaEntity.java`: constructor para escritura JPA, sin columnas nuevas.
- `conocimiento/infrastructure/persistence/repository/ConocimientoPruebaJpaRepository.java`: consulta completa con desempate por ID.
- `conocimiento/infrastructure/web/dto/ConocimientoPruebaResponse.java`: campo aditivo activa.
- `shared/infrastructure/web/GlobalExceptionHandler.java`: excepciones administrativas nuevas.

Pruebas Backend (`src/test/java/cl/casol/backend/`):

- Nuevo `AdminPruebasSecurityIntegrationTest.java`.
- Nuevo `conocimiento/application/service/AdministrarPruebasServiceTest.java`.
- Nuevo `conocimiento/infrastructure/persistence/adapter/PruebasPersistenciaQa013Test.java`.
- Modificado `conocimiento/application/service/GestionarPruebasConocimientoServiceTest.java`.
- Modificado `PruebaSecurityIntegrationTest.java`: expectativa del campo activa aditivo.
- Modificado `procedimiento/application/service/MantenerPasoServiceTest.java`: edición de PUBLICADO, orden ocupado y saltos.

Raíz Angular: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/`.

Nuevos:

- `src/app/core/services/admin-pruebas-api.service.ts`.
- `src/app/core/services/respaldo-pruebas-local.service.ts`.
- `src/app/core/services/admin-pruebas-api.service.spec.ts`.
- `src/app/core/services/organizacion-pruebas.service.spec.ts`.
- `src/app/features/pruebas/pruebas.component.spec.ts`.
- `src/app/features/procedimiento/pasos-qa013.component.spec.ts`.
- `src/app/features/conocimiento/conocimiento-pruebas-qa013.component.spec.ts`.
- `docs/QA013_IMPLEMENTACION_PRUEBAS_PASOS.md`.

Modificados:

- `src/app/core/services/organizacion.service.ts`: Pruebas con fuente HTTP, sin flags/storage operativo; mantiene organización QA-012.
- `src/app/core/services/procedimiento.service.ts`: mensajes de 409.
- `src/app/core/services/conocimiento.service.ts`: normalización de activa, compatible con respuestas anteriores sin campo.
- `src/app/core/models/conocimiento.model.ts`: activa opcional en PruebaAsociada.
- `src/app/features/pruebas/pruebas.component.ts`, `.html`, `.css`: pantalla administrativa persistente reutilizada.
- `src/app/features/conocimiento/conocimiento-detalle.component.ts`, `.html`: asociación confirmada por HTTP, selección fresca y etiqueta histórica.
- `src/app/features/procedimiento/procedimiento-detalle.component.ts`, `.html`: presentación, validación y confirmación de Pasos.
- `src/app/features/admin/admin.component.html`: acceso a Pruebas persistentes.

No se modificaron servicios Backend de mantenimiento de Pasos, entidad Paso, permisos, infraestructura, dependencias ni budgets. No se modificó el formulario general de Nuevo Procedimiento.

## 7. Migraciones y despliegue consolidado

**QA-013 no crea migración:** los tres campos utilizados ya existen en PruebaEntity. No es necesario agregar estado, tabla relacional o auditoría para este alcance. Se confirmó el modelo, no el DDL MySQL real.

Migraciones pendientes que siguen siendo requisitos del artefacto consolidado:

- QA-009: `V20261013_01__ampliar_solucion.sql` (título/estado/autoservicio).
- QA-012: `V20261014_01__organizacion_persistente.sql` (versiones y fila de control).
- Revisar también el estado del esquema previo de ELIMINADO de conocimientos según documentación existente.

No ejecutadas aquí. No se incorporó Flyway/Liquibase. Antes de un futuro despliegue autorizado: comprobar esquema MySQL real, PK/FK/índice unique de descripción y collation; respaldar; validar migraciones pendientes en entorno aislado; coordinar esquema → Backend → Angular. No desplegar el JAR consolidado contra esquema anterior sin esas validaciones. Para QA-013, Backend debe preceder a Angular para proveer nuevos endpoints y el campo activa histórico.

La administración de Pruebas no depende de la fila u_organizacion_control en su servicio, pero el Backend consolidado sí incluye QA-012 y sus requisitos de esquema. No se puede omitir esa dependencia porque QA-013 no añada SQL propio.

## 8. Pruebas y artefactos

| Comando local | Resultado final |
|---|---|
| Backend Java 21: `mvnw.cmd -o verify` | **645 pruebas; 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS**, JAR ejecutable empaquetado. |
| Angular: `npm test -- --watch=false --browsers=ChromeHeadless` | **141 pruebas satisfactorias**, salida 0. |
| Angular: `npm run build -- --configuration production` | Correcto; inicial **1,36 MB**, salida 0. |
| `git diff --check` sobre archivos relacionados | Sin defectos de whitespace; Git avisa normalización futura LF→CRLF. |

Se añadieron 33 casos Backend y 29 Angular respecto a 612/112 de QA-012. Se ejecutaron suites completas, conservando cobertura QA-009, QA-010, QA-011 y QA-012. Una expectativa previa de ausencia de activa se adaptó al contrato aditivo. Una nueva expectativa Angular de texto 503 se corrigió para respetar la sanitización compartida existente; no se modificó el manejador común de errores Angular.

Cobertura: CRUD/estados/campos; resultado opcional/límites; preservación de estado en PUT; asociaciones históricas e inactivas; selección activa y bloqueo previo; roles ADMINISTRADOR/TECNICO/SOPORTE/USUARIO/401; 400/404/409 y ausencia de DELETE; adaptador JPA/saveAndFlush/refresco; consulta derivada con orden/desempate; reindexación y fallo postcommit; HTTP sin storage/falso éxito; errores; posición vs orden; BORRADOR/PUBLICADO; orden entero y guardado/recarga tras respuesta.

Los adaptadores se prueban con mocks de JPA. MockMvc verifica seguridad/contrato con servicio simulado. No se ejecutó persistencia física ni concurrencia real InnoDB; no presentar esas suites como evidencia de MySQL real.

Advertencias que permanecen:

- CSS Inicio: 18,62 kB frente a warning 16 kB.
- CSS Detalle de Conocimiento: 22,53 kB frente a warning 16 kB.
- Suite Angular: avisos preexistentes de disabled con formulario reactivo y recurso del logo en Karma cuando aparece. No hubo fallos finales.
- Backend: aviso Mockito de auto-attachment del agente en pruebas; no es fallo de producción.

Artefactos verificados:

- JAR: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar`.
- HTML: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/dist/buscador-errores/browser/index.html`.
- `jar tf` confirmó application.properties, AdministrarPruebasService y AdminPruebaController en BOOT-INF/classes. No se inició el JAR contra una base de datos.
- Logs locales: Backend `target/qa013-verify.log`; Angular `.angular/cache/qa013-tests.log`, `.angular/cache/qa013-build.log`. No versionar ni distribuir logs de pruebas.

## 9. Riesgos y validaciones pendientes

1. MySQL aislado autorizado: verificar descripción UNIQUE/collation/longitudes y FK reales; altas concurrentes, desactivación vs asociación, dos ediciones obsoletas, rollback, consultas JPA/JPQL reales e indexación. No se asumieron restricciones físicas.
2. Control optimista/auditoría: no hay versión nueva ni bitácora/snapshots. Edición global cambia presentación histórica; vínculos y resultados existentes permanecen. Si se requiere conservar texto anterior, diseñar versiones antes de implementar.
3. Índice: retry postcommit sin cola durable y recuperación tras fallo necesitan UAT/operación autorizada. No se garantiza frescura entre pestañas sin recarga.
4. Órdenes de Pruebas: empates/saltos, default1 y carreras entre asociaciones diferentes permanecen como reglas/riesgos previos. No se implementó reordenamiento colectivo ni unicidad física nueva.
5. Pasos: crítico sigue siendo indicador informativo; no se agregó bloqueo secuencial ni publicación mínima de Pasos. Esas decisiones y el CRUD de Nuevo Procedimiento quedan fuera de QA-013.
6. QA-012: validación de responsableId con padre inactivo y coordinación concurrente de nuevas asignaciones sigue pendiente por decisión previa. No se alteró ese flujo.
7. UAT pendiente: dos usuarios/navegadores, alta/edición/estados compartidos, conocimiento con referencia inactiva, intento de nueva asociación obsoleta, técnico sin controles administrativos, respaldos manuales, errores sin falso éxito, Paso 4/10 en BORRADOR/PUBLICADO, móvil/teclado/zoom y despliegue consolidado en un entorno autorizado.

**QA-013 implementado localmente y detenido para revisión. Sin migraciones ejecutadas ni despliegue; QA-014 no iniciado.**
