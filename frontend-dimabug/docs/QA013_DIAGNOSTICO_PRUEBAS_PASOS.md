# QA-013 — Diagnóstico funcional y técnico de Pruebas y Pasos

Fecha: 2026-10-14, America/Santiago. Revisión del código local, no de la versión anterior desplegada en STAGING.

## 1. Resumen ejecutivo

Pruebas y Pasos tienen modelos, propietarios y ciclos de vida diferentes. **Pruebas es un catálogo global reutilizable** asociado a conocimientos mediante una tabla N:M que contiene el orden. **Paso es una definición exclusiva de un Procedimiento**, con orden e indicador crítico; sus ejecuciones se registran aparte.

El Backend solo consulta el catálogo global de Pruebas: no tiene comandos de alta, edición ni estado del catálogo. Sí permite asociar una prueba activa a un conocimiento y editar el orden de esa asociación. Angular ofrece botones de estado y “Eliminar” en el catálogo, pero sus efectos son exclusivamente locales en sessionStorage; no representan cambios MySQL y no están limitados a ADMINISTRADOR en esa pantalla.

Pasos dispone de GET/POST/PUT persistentes y de formulario existente dentro del detalle del Procedimiento. No requiere un catálogo independiente. La escritura solo se permite en BORRADOR, con bloqueo transaccional del Procedimiento y comprobación de pertenencia/orden duplicado. No tiene estado propio, desactivación, eliminación ni reordenamiento colectivo.

Se encontraron defectos verificables de persistencia aparente, mensajes HTTP y presentación del orden. Hay decisiones funcionales pendientes antes de diseñar administración persistente de Pruebas, política de orden o bajas de Pasos. No se implementó ninguna corrección.

## 2. Alcance, evidencia y límites

Se inspeccionaron entidades/puertos/adaptadores, controladores/DTO/servicios, seguridad/visibilidad, Angular y suites existentes. Se revisó Git: rama `catalina`, numerosos cambios locales preexistentes de QA-009 a QA-012 y trabajos anteriores. Solo se creó este documento; no se modificó código ni configuración, no se ejecutaron pruebas nuevas, builds, migraciones, commits u operaciones destructivas. No hubo acceso a STAGING/PRODUCCIÓN.

No se inspeccionó MySQL real. Los archivos SQL disponibles cubren estado ELIMINADO de conocimientos, ampliación de Soluciones QA-009 y versionado de organización QA-012. No contienen DDL base de Pruebas/Pasos. Las restricciones JPA descritas son el esquema previsto, **no certificación de PK/FK/UNIQUE/CHECK/collation/ON DELETE físicos**. Las migraciones pendientes permanecen sin ejecutar.

Se leyó el formulario general de Procedimiento únicamente para ubicar el flujo de Pasos. No se amplió el diagnóstico al CRUD de Nuevo Procedimiento ni se propone implementarlo en QA-013.

## 3. Modelo real

| Entidad / tabla | Campos relevantes | Relaciones y restricciones declaradas |
|---|---|---|
| `PruebaEntity` / `co_prueba` | prueba_id IDENTITY; prueba_descripcion obligatoria, longitud 500 y unique=true; prueba_resultado_esperado opcional, longitud 500; prueba_estado boolean obligatorio | Catálogo global sin conocimiento_id ni causa_id. Sin orden global, auditoría ni @Version. |
| `ConocimientoPruebaEntity` / `co_conocimiento_prueba` | PK compuesta conocimiento_id + prueba_id (`ConocimientoPruebaId`); prueba_orden Integer obligatorio | @ManyToOne/@MapsId a Prueba. Conocimiento se representa en la clave, sin @ManyToOne en esta entidad. No se declara UNIQUE por conocimiento+orden ni estado/auditoría/versionado propio. |
| `PasoEntity` / `pr_paso` | paso_id IDENTITY; procedimiento_id obligatorio; paso_orden obligatorio; paso_instruccion TEXT obligatoria; paso_es_critico boolean obligatorio | Procedimiento es ID escalar. @UniqueConstraint(procedimiento_id,paso_orden). Sin estado propio, usuario/fecha de modificación ni @Version. |
| `ProcedimientoEntity` / `pr_procedimiento` | Nombre, descripción, estado BORRADOR/PUBLICADO; creado_por/fecha_creacion; modificado_por/fecha_modificacion | Padre de Pasos. Auditoría del padre no equivale a auditoría de cada cambio de Paso. |
| `EjecucionPasoEntity` / `se_ejecucion_paso` | ID; ejecucion_id; paso_id; cumplido; observación máximo 300; fecha opcional | UNIQUE declarado por ejecución+paso. Registra cumplimiento, no estado de disponibilidad del Paso. IDs escalares; sin snapshot de instrucción/orden ni auditoría de todos los cambios. |
| `co_material_apoyo` / `MaterialApoyoEntity` | material_id, conocimiento_id opcional, paso_id opcional; nombre, tipo, URL | Material asociado a Conocimiento o Paso por servicios existentes. No es un catálogo de Pruebas ni una relación Prueba–Paso. |
| `co_causa` / `CausaEntity` | Descripción, orden, referencia al conocimiento | No existe vínculo directo causa_id en co_prueba/co_conocimiento_prueba. La prueba comprueba una causa conceptualmente, pero su asociación persistente es al conocimiento. |

La N:M de Pruebas existe **entre Conocimiento y Prueba**. La misma prueba puede estar en varios conocimientos con diferentes órdenes. Una prueba no se repite dos veces en el mismo conocimiento por la clave compuesta y la validación del servicio. No se encontraron relaciones directas Prueba–Procedimiento, Prueba–Paso o Prueba–Causa, ni una tabla de resultados de ejecución de pruebas diagnósticas en este flujo.

Un Paso pertenece a un único Procedimiento. Distintos procedimientos pueden tener el mismo número de orden; no se reutiliza el mismo registro Paso en varios padres. Una ejecución referencia las definiciones de Pasos existentes y guarda su cumplimiento individual.

## 4. Funcionalidades y CRUD actuales

| Recurso | Backend actual | Angular actual | Ausencias / observaciones |
|---|---|---|---|
| Catálogo global de Pruebas | READ de activas ordenadas por descripción | Listado, detalle, resultado esperado, contador; estados/ocultamiento locales | Sin CREATE/UPDATE/PATCH/DELETE administrativo ni consulta completa de inactivas; sin formularios de creación/edición o filtros/búsqueda. |
| Pruebas de Conocimiento | GET activas, POST asociación, PUT orden | Asociación de prueba existente desde detalle, lista ordenada y resultado esperado | Sin desvinculación ni edición global de descripción/resultado; PUT orden disponible en servicio Angular, sin control visible de reordenamiento. |
| Pasos de Procedimiento | GET ordenados, POST/PUT en BORRADOR | Modal crear/editar, orden/instrucción/crítico, navegación y material por Paso | Sin DELETE, PATCH estado o ordenamiento colectivo. La falta de estado propio es un diseño actual, no necesariamente un defecto. |
| Cumplimiento de Pasos | Inicio de ejecución crea checklist; actualización y consulta; cierre/cancelación | Flujo de ejecución independiente | No es CRUD de definiciones ni un estado administrativo. No usarlo para desactivar Pasos. |

## 5. Endpoints existentes y permisos

| Método / endpoint | Comportamiento | Roles relevantes |
|---|---|---|
| GET `/api/pruebas` | Solo catálogo activo; devuelve id/descripcion/resultadoEsperado | ADMINISTRADOR, TECNICO y SOPORTE; USUARIO no está autorizado por esta ruta. |
| GET `/api/conocimientos/{id}/pruebas` | Asociaciones cuya prueba global está activa, orden ASC | ADMINISTRADOR/TECNICO/SOPORTE; USUARIO solo si conocimiento PUBLICADO y visible. |
| POST `/api/conocimientos/{id}/pruebas` | `{pruebaId,orden?}`; 201; orden omitido = 1 | ADMINISTRADOR, TECNICO, SOPORTE. |
| PUT `/api/conocimientos/{id}/pruebas/{pruebaId}` | `{orden}`; modifica asociación, no catálogo | ADMINISTRADOR, TECNICO, SOPORTE. |
| GET `/api/procedimientos/{id}/pasos` | Pasos orden ASC | ADMINISTRADOR/TECNICO/SOPORTE; USUARIO con padre PUBLICADO. |
| POST `/api/procedimientos/{id}/pasos` | `{orden,instruccion,esCritico}`; 201; padre BORRADOR | ADMINISTRADOR, TECNICO, SOPORTE. |
| PUT `/api/procedimientos/{id}/pasos/{pasoId}` | Mismos campos; exige pertenencia y BORRADOR | ADMINISTRADOR, TECNICO, SOPORTE. |

Recursos relacionados existentes, que no deben duplicarse:

- GET/POST/PUT de `/api/procedimientos/{id}/pasos/{pasoId}/materiales` y PUT/DELETE sobre el material identificado; POST `/materiales/archivo` y acceso al archivo mediante rutas existentes. La eliminación de material es distinta de la inexistente eliminación de Paso.
- POST `/api/procedimientos/{id}/ejecuciones`; GET `/api/ejecuciones` y `/{id}`; GET `/{id}/pasos`; PATCH `/{id}/pasos/{ejecucionPasoId}`, `/{id}/completar` y `/{id}/cancelar`. Su autorización adicional se basa en propietario de la ejecución o ADMINISTRADOR.

`SecurityConfig` ya restringe `/api/admin/**` a ADMINISTRADOR. Los comandos operacionales anteriores conservan sus permisos; no se recomienda convertirlos automáticamente en exclusivos de ADMINISTRADOR. Cualquier futura administración del catálogo global debe usar protección Backend y ocultar sus controles Angular según rol.

`VisibilidadContenidoConfig` comprueba GET de hijos. No permite ver pruebas de conocimientos ELIMINADO a través de la ruta normal, tampoco como administrador: las excepciones históricas específicas se aplican a resultados/efectividad. El servicio de asociaciones usa `buscarPorId`, cuyo adaptador excluye ELIMINADO. Un conocimiento PUBLICADO puede recibir asociaciones/actualizar su orden: este servicio no exige BORRADOR.

## 6. Validaciones, ordenamiento, estados e histórico

### Pruebas

- POST: pruebaId obligatorio y positivo; orden opcional mínimo 1 por Bean Validation; si falta, servicio utiliza 1.
- PUT: orden obligatorio mínimo 1. Estas validaciones son de DTO; los métodos del servicio no validan por sí mismos esos límites.
- Verifica conocimiento existente/no eliminado; nueva asociación requiere prueba activa. Duplicado de la misma prueba produce 409; prueba inactiva/inexistente y asociaciones inexistentes producen 404.
- **No verifica exclusividad del orden** entre distintas pruebas del mismo conocimiento. Se admiten órdenes repetidos y saltos. El listado ordena solo por orden; empates no tienen desempate explícito en JPA. No existe operación atómica de reordenamiento.
- Actualizar orden recupera la asociación por IDs y no exige que su prueba siga activa. Una asociación a prueba inactiva podría reordenarse por API aunque no aparezca en GET operacional. Decidir si debe rechazarse o admitirse como administración histórica.
- No se expone desactivación global persistente. Si en el futuro se cambia prueba_estado a false, los vínculos se conservarían, pero desaparecerían de GET de catálogo y de todos los conocimientos asociados debido al filtro `PruebaActivaTrue`.
- No hay snapshot de descripción ni resultado esperado por asociación. Editar globalmente una prueba afectaría a todos sus conocimientos, incluida su presentación histórica. No hay autor/fecha de alta/edición por prueba ni por asociación.
- Asociar/actualizar orden reindexa el conocimiento en la transacción. Una futura modificación/desactivación del catálogo deberá evaluar reindexación de todos los conocimientos que la reutilizan y cachés, sin modificar relaciones históricas automáticamente.

### Pasos

- GuardarPasoRequest exige orden entero representado por Integer, obligatorio positivo; instrucción no vacía; esCritico obligatorio. No hay límite explícito de longitud en DTO para TEXT.
- Servicio bloquea el Procedimiento antes de crear/modificar; exige BORRADOR y valida orden duplicado. PUT exige Paso propio; no permite moverlo de Procedimiento.
- Orden único por padre en la aplicación y en el mapeo JPA. **No exige secuencia consecutiva**: 1, 4, 10 es admisible; tampoco implementa permuta de dos posiciones ocupadas.
- No existe estado individual ni baja lógica de Paso. El estado BORRADOR/PUBLICADO del Procedimiento gobierna la edición y visibilidad. No existe endpoint actual para volver PUBLICADO a BORRADOR; las definiciones publicadas permanecen protegidas por el servicio.
- No existen DELETE de definición de Paso o desvinculación automática de materiales/ejecuciones en este CRUD. No introducirlos sin política de historial e integridad aprobada.
- Inicio de ejecución requiere Procedimiento PUBLICADO con al menos un Paso. MantenerProcedimientoService.publicar no comprueba que existan Pasos; se puede publicar vacío y el inicio de ejecución lo rechazará. Es una brecha entre publicación y ejecución que requiere decisión funcional, no autorización para cambiar el CRUD de Procedimiento.
- La ejecución guarda IDs de Pasos al iniciarse. El listado histórico usa instrucción/orden/esCritico de la definición actual, no snapshots. La inmutabilidad de PUBLICADO protege este historial frente a comandos normales; escrituras externas o una futura reapertura romperían esa garantía.
- `esCritico` se muestra como advertencia; no impide navegar ni impone ejecución secuencial. Completar ejecución requiere todos los Pasos cumplidos, críticos o no. Definir si se necesita regla distinta antes de implementarla.

### Concurrencia y errores

Pruebas/asociaciones: transacciones presentes, pero sin bloqueo del conocimiento en este servicio ni @Version. El check existe→save no garantiza por sí solo un 409 determinista en dos POST concurrentes de la misma asociación. El adaptador usa save con clave compuesta (merge posible). Se debe probar conflicto/upsert real; no afirmar un resultado físico sin MySQL. Dos PUT del mismo orden pueden sobrescribir la edición previa y distintas altas calcular el mismo orden.

Pasos: bloqueo pesimista del padre y unicidad por padre/orden reducen carreras entre comandos que siguen ese protocolo. No hay versión enviada por Angular ni @Version: serializar no evita que una edición basada en datos antiguos sobrescriba la anterior. No hay pruebas InnoDB de esas carreras. MantenerPasoService aplica validaciones de negocio; orden/instrucción inválidos dependen de entrada por Controller validado.

GlobalExceptionHandler convierte validaciones y EstadoProcedimientoInvalidoException a 400, ausencias a 404 y duplicados/integridad a 409; autenticación/autorización produce 401/403. Por tanto, editar un Paso de PUBLICADO se rechaza actualmente con 400 y el mensaje de preservación histórica. No introducir dependencia o configuración nueva para este diagnóstico.

## 7. Angular: navegación y persistencia

`/pruebas` tiene staffGuard y acceso desde Catálogos en ShellLayout. `PruebasComponent` no consulta AuthService: Ver/Estado/Eliminar se muestran a todo staff permitido, incluido TECNICO. No hay botón Añadir ni formulario global. El panel Administrador declara la gestión de Pruebas como pendiente.

`OrganizacionService.listarPruebas` hace GET real, mantiene shareReplay y caché sessionStorage (`dimabug.pruebas.lista.v1`); puede mostrar datos antiguos y usarlos como fallback ante fallo de red. `listarPruebasDetalle` aplica ocultos (`dimabug.pruebas.ocultos.v1`) y estados locales (`dimabug.pruebas.activo.v1`). Los dos comandos de la pantalla no hacen HTTP.

La asociación de Pruebas está en **ConocimientoDetalleComponent**, no en el formulario inicial general. Selecciona una prueba existente con un select sin búsqueda y calcula max(orden)+1. El catálogo de ese selector usa `listarPruebas`, no los estados/ocultos locales del listado; una prueba “desactivada” localmente sigue disponible para asociar. Se preserva resultadoEsperado y se muestra “No indicado” si no existe. No hay modal de creación/edición de definición global ni selección explícita de causa.

`ConocimientoService` dispone de GET/POST/PUT reales para asociación/orden; GET ordena en Angular. La pantalla no consume PUT para editar orden. El alta usa un elemento temporal y anuncia éxito antes de la respuesta; revierte la lista si falla, pero cierra y reinicia el formulario. El selector no excluye pruebas ya asociadas: Backend rechaza el duplicado con 409.

Pasos se gestionan en **ProcedimientoDetalleComponent**, después de que existe el padre, no como catálogo en Catálogos. ProcedimientoService utiliza GET/POST/PUT reales. El detalle muestra modal de orden/instrucción/crítico, Cancelar, errores, controles de anterior/siguiente y material reutilizando MaterialApoyoFormComponent. Los botones de edición solo aparecen para staff y padre BORRADOR. Crear/editar recarga los Pasos tras éxito; no es simulación local.

El servicio guarda Pasos en localStorage (`dimabug.procedimientos.lista.v1.pasos.{id}`) como caché de lecturas; no sustituye sus escrituras HTTP. El detalle presenta la caché primero y luego consulta servidor. Los datos de Pasos se confían al orden Backend, sin sort adicional Angular. `compartir` deduplica solicitudes simultáneas y elimina la entrada al finalizar, por lo que recargar tras guardar puede iniciar una consulta nueva. No se encontró filtro/búsqueda de Pasos ni reordenamiento mediante arrastre.

## 8. Hallazgos verificables y clasificación

| ID | Evidencia / causa | Tipo | Propuesta sujeta a autorización | Impacto / complejidad |
|---|---|---|---|---|
| QA013-01 | PruebasComponent.confirmarEstado/confirmarEliminar llaman marcarPruebaActiva/ocultarPrueba; solo sessionStorage | Bug de persistencia aparente | Priorizar retiro de acciones engañosas o reemplazo por administración HTTP con política aprobada. Respaldar flags/cache antes de dejar de utilizarlos; no importar flags como estados reales. | Angular; Backend si se autoriza persistencia. BD por confirmar. Media. |
| QA013-02 | Ruta staffGuard, botones/métodos sin control ADMINISTRADOR en PruebasComponent | Defecto de acceso UI administrativo; no vulnerabilidad de escritura MySQL actual | Reservar controles administrativos a ADMINISTRADOR; Backend obligatorio en futuros endpoints. Mantener consulta/asociación operacional del técnico. | Angular y futuros endpoints. Baja/media. |
| QA013-03 | listado local usa detalle filtrado; selector usa catálogo sin esos filtros | Bug de coherencia UX | Una fuente persistente de disponibilidad; invalidar/refrescar catálogo y asociaciones tras cambios autorizados. | Angular/Backend; decisiones sobre histórico. Media. |
| QA013-04 | Contador/mensaje vacío dicen “desde/en la base de datos” aunque hay filtros/fallback locales | Bug de información | Distinguir resultado consultado/cache/error; no certificar BD con datos locales. | Angular. Baja. |
| QA013-05 | ProcedimientoService.mensaje asigna todo 409 a número duplicado, aunque GlobalExceptionHandler también usa 409 para integridad genérica | Defecto de clasificación de errores verificable en código; reproducción de otros conflictos pendiente | Mostrar mensaje Backend cuando existe; conservar fallback específico solo si corresponde. No confundir este hallazgo con editar un Paso de PUBLICADO: ese caso devuelve 400 y conserva el mensaje Backend. | Angular; sin BD ni endpoint nuevo. Baja. |
| QA013-06 | detalle muestra `Paso {{paso.orden}} de {{pasos.length}}`; Backend permite saltos | Bug de presentación | Separar posición en lista del número de orden; decidir si debe imponerse continuidad antes de alterar reglas. | Angular. Baja. |
| QA013-07 | nextOrden en cliente; backend permite órdenes iguales/default1; query solo order asc | Deuda de orden/concurrencia | Definir empates/continuidad; asignación de orden segura y/o reordenamiento transaccional con pruebas. No añadir UNIQUE sin preflight. | Backend/Angular; posible SQL posterior. Media/alta. |
| QA013-08 | guardarPrueba anuncia éxito/cierra/reset antes del POST y revierte al fallar | Bug UX de confirmación | Mostrar éxito tras confirmación; mantener formulario y datos si falla; controlar solicitudes pendientes. | Angular. Baja/media. |
| QA013-09 | Selector ofrece también pruebas ya asociadas; no búsqueda; no edición visible de orden | Mejora UX / funcionalidad faltante | Filtrar duplicados visibles, búsqueda y exponer PUT orden si se aprueba. Mantener validación Backend y contemplar asociaciones inactivas ocultas. | Angular; posible consulta administrativa de asociaciones. Media. |
| QA013-10 | publicar admite padre vacío, ejecutar exige Pasos | Decisión funcional pendiente | Acordar si publicar debe exigir al menos un Paso; documentar para el módulo Procedimiento sin implementarlo aquí. | Backend del padre, fuera del alcance de corrección actual. Media. |
| QA013-11 | Navegación usa ✓ para índices anteriores; esCritico solo aviso | Ambigüedad UX/negocio | Distinguir paso visitado de paso efectivamente cumplido; aprobar cualquier bloqueo secuencial/crítico. | Angular; Backend solo si regla nueva. Baja/media. |
| QA013-12 | No versionado de Pruebas/asociación/Paso; histórico referencial | Deuda técnica | Evaluar control optimista, auditoría y estrategia histórica. No confundir bloqueo padre con detección de edición obsoleta. | Backend/Angular y posible SQL. Media/alta. |

No se reprodujeron errores contra STAGING ni navegador conectado al Backend en esta etapa. Los hallazgos “verificables” describen rutas de código confirmadas; las carreras, restricciones físicas y experiencia en dispositivos reales son riesgos/UAT pendientes.

## 9. Archivos involucrados

Raíz Backend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/`. Prefijo Java: `src/main/java/cl/casol/backend/`.

- Modelo Pruebas: `conocimiento/domain/Prueba.java`, `ConocimientoPrueba.java`; entidades `conocimiento/infrastructure/persistence/entity/PruebaEntity.java`, `ConocimientoPruebaEntity.java`, `ConocimientoPruebaId.java`.
- Puertos: `conocimiento/application/port/out/PruebaRepository.java`, `ConocimientoPruebaRepository.java`, `ConocimientoRepository.java`.
- Servicios: `conocimiento/application/service/GestionarPruebasConocimientoService.java`, `IndexarConocimientoService.java`.
- Persistencia: `conocimiento/infrastructure/persistence/adapter/PruebaRepositoryAdapter.java`, `ConocimientoPruebaRepositoryAdapter.java`; repositorios `PruebaJpaRepository.java`, `ConocimientoPruebaJpaRepository.java`; mapper `PruebaMapper.java`.
- REST/DTO: `conocimiento/infrastructure/web/PruebaController.java`, `ConocimientoPruebaController.java`; DTO `PruebaResponse.java`, `ConocimientoPruebaResponse.java`, `AsociarPruebaRequest.java`, `ActualizarOrdenPruebaRequest.java`.
- Pasos: `procedimiento/domain/Paso.java`; `procedimiento/application/port/out/PasoRepository.java`, `ProcedimientoRepository.java`; servicios `MantenerPasoService.java`, `EdicionProcedimiento.java`, `MantenerMaterialPasoService.java`; entidades `PasoEntity.java`, `ProcedimientoEntity.java`; adaptadores `PasoRepositoryAdapter.java`, `ProcedimientoRepositoryAdapter.java`; `PasoJpaRepository.java`, `PasoMapper.java`; REST `PasoController.java`, `MaterialPasoController.java`, `MaterialPasoArchivoController.java`; DTO `GuardarPasoRequest.java`, `PasoResponse.java`.
- Historia/ejecución: `ejecucion/application/service/GestionarEjecucionService.java`; `ejecucion/infrastructure/persistence/entity/EjecucionPasoEntity.java`; `ejecucion/infrastructure/web/EjecucionController.java` (incluye inicio); `conocimiento/infrastructure/persistence/entity/MaterialApoyoEntity.java`.
- Seguridad/errores: `shared/infrastructure/security/SecurityConfig.java`, `shared/infrastructure/web/GlobalExceptionHandler.java`, `VisibilidadContenidoConfig.java`, `shared/application/service/VisibilidadContenidoService.java`.

Raíz Angular: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/`.

- `src/app/features/pruebas/pruebas.component.ts`, `.html`, `.css`.
- `src/app/core/services/organizacion.service.ts`, `catalogo.service.ts` (métodos legacy de Pruebas sin consumidores en pantalla actual).
- `src/app/core/services/conocimiento.service.ts`; `src/app/features/conocimiento/conocimiento-detalle.component.ts`, `.html`; `conocimiento-form.component.ts`, `.html` (solo datos generales).
- `src/app/core/services/procedimiento.service.ts`, `ejecucion.service.ts`; modelos `conocimiento.model.ts`, `catalogo.model.ts`, `procedimiento.model.ts`.
- `src/app/features/procedimiento/procedimiento-detalle.component.ts`, `.html`, `.css`; formulario general solo como contexto del flujo de padre.
- `src/app/shared/ui/material-apoyo-form.component.ts`; `src/app/shared/layout/shell-layout.component.html`; `src/app/features/admin/admin.component.html`; `src/app/app.routes.ts`, `src/app/core/guards/auth.guard.ts`.

## 10. Cobertura existente y faltante

Se inspeccionaron fuentes de pruebas y reportes Surefire ya existentes de la última ejecución local QA-012. **No se ejecutaron nuevas pruebas QA-013.** Los reportes indicados registran 0 errores/0 fallos; sus resultados no certifican persistencia InnoDB.

| Suite Backend existente | Casos en reporte previo | Qué cubre / límites |
|---|---:|---|
| GestionarPruebasConocimientoServiceTest | 12 | Catálogo/lista, asociación válida/default1, duplicados, inactivas/ausencias, PUT orden conserva definición. Mocks. |
| PruebaRepositoryAdapterTest | 2 | Delegación a consultas activas/ordenadas; no consulta SQL real. |
| PruebaSecurityIntegrationTest | 19 | GET/POST/PUT, admin/técnico, DTO, 400/401/403/404/409. MockMvc con servicios simulados. |
| MantenerPasoServiceTest | 9 | Alta crítico/no crítico, duplicado, distinto padre, modificar propio/ajeno, ausencias, listar, bloqueo de alta en PUBLICADO. |
| Hu05SecurityIntegrationTest | 10 | REST de recursos del Procedimiento, técnico/admin, validación Paso, orden/409, materiales, autorización. No InnoDB. |
| MantenerMaterialPasoServiceTest | 7 | Material asociado/validaciones/pertenencia y restricción de padre publicado. |
| GestionarEjecucionServiceTest | 22 | Inicio PUBLICADO/no vacío, checklist, cumplimiento/fecha, cierre/cancelación/propietario/admin; no snapshots ni carreras reales. |
| ContenidoRestringidoSecurityIntegrationTest | 4 | Visibilidad de hijos y contenido publicado/borrador/eliminado con servicios simulados. |

No se localizaron specs específicas de PruebasComponent, OrganizacionService para Pruebas ni ProcedimientoDetalleComponent para alta/edición de Pasos. `procedimiento-material-archivo.service.spec.ts` y `material-apoyo-form.component.spec.ts` cubren material reutilizable, **no el CRUD de Paso ni estados de Pruebas**. Los specs de Conocimiento existentes se centran en otras funciones y no reemplazan cobertura de asociación/orden.

Pruebas futuras necesarias:

1. Catálogo administrativo de Pruebas, si se autoriza: alta/edición/estado, descripción obligatoria/límites/unicidad acordada, resultado esperado, inactivas, roles ADMINISTRADOR vs TECNICO/SOPORTE/USUARIO, campos ajenos y HTTP.
2. Asociaciones: reutilización entre conocimientos, duplicado de mismo ID, orden repetido/omitido/ties/saltos según decisión, reordenamiento, inactive-at-update, estados del padre y conservación de vínculos.
3. Pasos: PUT de PUBLICADO rechazado (además del alta ya cubierta), orden ocupado en modificación, orden 0/negativo/nulo/no entero y esCritico ausente; instrucción vacía/límite acordado; padre ajeno y persistencia real.
4. MySQL aislado: PK/FK/UNIQUE y collation reales; dos POST concurrentes misma asociación/orden, dos PUT obsoletos, publicación concurrente con edición Paso, rollback/reindexación y bloqueos con varias conexiones. No ejecutar migraciones sin autorización.
5. Histórico: cambio/desactivación global de Prueba compartida y lecturas históricas; definiciones de Pasos publicadas inmutables; checklist existente no regenerado; resultados de ejecuciones preservados. Solo implementar comportamiento aprobado.
6. Angular: controles por rol, API real sin escrituras locales ficticias, manejo de 409 de integridad vs orden y 400 de estado del padre, error sin falso éxito, precarga/recarga, caches antiguas, filtros/búsquedas si aprobados, orden con saltos, material y Cancelar.
7. UAT: dos navegadores/usuarios; catálogo operativo vs administrativo; prueba compartida; revisión de respaldo local; pasos no consecutivos, crítico, publicación/edición, móvil/teclado/zoom; mensajes consistentes con persistencia.

## 11. Decisiones funcionales requeridas

- Catálogo Pruebas: ADMINISTRADOR para escritura global, manteniendo técnico para asociar/reordenar; confirmar altas/ediciones y si reactivar estará disponible. Reutilizar `/api/admin/**`, sin duplicar GET operacional.
- Unicidad de descripción: comparación exacta, mayúsculas, espacios y collation. JPA unique=true no define esa normalización. No crear restricciones SQL nuevas antes de validar datos.
- Desactivación global compartida: confirmar que deja de ofrecerse operacionalmente en todos los conocimientos y cómo mostrar referencias históricas/inactivas. No borrar relaciones. Decidir si debe advertirse por número de conocimientos afectados y si una publicación exige alguna prueba activa; actualmente no hay esa regla en el servicio inspeccionado.
- Edición global: aceptar cambio visible para todos los usos o diseñar versiones/snapshots. No asumir que se necesita copiar pruebas por conocimiento.
- Orden de asociaciones: permitir empates/saltos o exigir secuencia; definir asignación por servidor, desempate y reordenamiento atómico. La ausencia de UNIQUE de orden no justifica añadirlo sin decisión/preflight.
- Pasos: mantener definición exclusiva e inmutable al publicar. No añadir estado/baja individual por analogía con Pruebas. Si se requieren correcciones posteriores, diseñar versiones de Procedimiento y conservación de ejecuciones antes de abrir edición.
- Crítico: advertencia informativa vs bloqueo secuencial. No convertir navegación en registro de cumplimiento sin acuerdo.
- Publicar sin Pasos: decidir y trasladar la implementación al módulo autorizado del padre; no avanzar a Nuevo Procedimiento en este diagnóstico.
- Auditoría y edición concurrente: definir trazabilidad requerida y respuesta ante versión obsoleta. No imponer migraciones en esta etapa.

## 12. Propuesta de correcciones priorizadas

| Etapa / prioridad | Trabajo propuesto | Dependencias / riesgo / complejidad |
|---|---|---|
| A — Alta | Corregir texto del catálogo, 409 de ProcedimientoService y posición vs orden; documentar alcance local de acciones o retirarlas hasta persistencia; controles administrativos por rol | Requiere autorización de cambios Angular. Bajo riesgo; baja complejidad. No cambiar permisos operacionales. |
| B — Alta | Aprobar política del catálogo compartido; diseñar administración persistente Pruebas en Controller → Application Service → Port → Adapter; reutilizar consultas existentes, estado ya disponible | Backend + Angular. Media. Verificar esquema antes de determinar SQL; no hace falta inventar tabla/estado nuevo. |
| C — Alta | Sustituir estado/ocultamiento local por HTTP, respaldo manual previo sin importación automática; actualizar listado/detalle/selector tras éxito; preservar resultado esperado e historial | Depende B. Media. Revisión de caches/indexación compartida. |
| D — Media | Corregir éxito anticipado de asociación; búsqueda/select sin duplicados; acordar y exponer edición de orden existente | Angular primero; Backend para reglas de orden/concurrencia si aprobadas. Media. |
| E — Media | Concurrencia/versionado y auditoría según contrato acordado; test MySQL aislado de bloqueos/integridad/historial | Posible SQL aditivo revisable, sin ejecutarlo hasta autorización. Media/alta. |
| F — Posterior | Reordenamiento colectivo o nuevas políticas de críticos/baja de Pasos/versionado del padre | Funcionalidad nueva y posible impacto histórico; alta. No asumirla parte del MVP. |

Mantener regresión QA-009 a QA-012 al implementar cualquier cambio. No tocar almacenamiento local de otros catálogos ni la validación de Responsables pendiente de QA-012. No duplicar componentes ni endpoints; reutilizar formulario de material y pantalla detalle de Pasos.

**Diagnóstico completado. Pendiente revisión y autorización antes de implementar.**
