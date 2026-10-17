# QA-014 — Diagnóstico de Nuevo Procedimiento

Fecha: 2026-10-14, America/Santiago. Diagnóstico del código local posterior a QA-013; no de la versión anterior en STAGING.

Solo se creó este informe. No se modificó código/configuración, no se ejecutaron pruebas nuevas, builds, migraciones, commits, resets, clean o stash. No hubo acceso a STAGING/PRODUCCIÓN. Git: rama `catalina`, 217 entradas preexistentes de estado, incluidos QA-009 a QA-013 y trabajos anteriores. Las restricciones físicas MySQL permanecen sin confirmar.

## 1. Resumen ejecutivo

El flujo existente es **incremental y persistente mediante API**: primero se guarda la cabecera en BORRADOR; después se agregan pasos desde el detalle, con un POST independiente por paso. No existe guardado único de Procedimiento + lista de Pasos ni un formulario de pasos en la pantalla inicial.

Cada comando Backend tiene su transacción. Si falla un paso, el procedimiento ya creado y los pasos anteriores confirmados permanecen guardados. No hay rollback del conjunto de solicitudes, eliminación compensatoria ni rollback automático del padre. Un BORRADOR sin pasos es un registro permitido por el diseño actual, no necesariamente corrupción.

Se conserva la protección de PUBLICADO y la integración QA-013. Los principales hallazgos nuevos son validación Angular incompleta, formulario editable después de fallo de precarga, pérdida de cambios sin aviso al abandonar y riesgo de duplicidad ante respuestas perdidas. Publicar sin pasos está permitido, pero ejecutar exige al menos uno: esa brecha requiere decisión funcional antes de cambiar reglas.

## 2. Flujo funcional actual

1. El menú enlaza `/procedimientos`. El listado ofrece **Nuevo** para staff. `/procedimientos/nuevo` y `/:id/editar` usan staffGuard; la aplicación requiere autenticación en su estructura protegida.
2. Nuevo Procedimiento presenta solo **Nombre** y **Descripción**. No incluye selectores de Conocimiento, Sistema, Hardware, Departamento, Responsable ni Pasos. El texto informa que queda en borrador y lo publica un administrador desde el detalle.
3. Nombre usa Validators.required en Angular y maxlength=150 en HTML; Descripción es opcional. Al guardar se revisan formulario, soloLectura y guardando; se marca guardando antes del HTTP, evitando otro envío desde esa misma instancia mientras está pendiente.
4. ProcedimientoService recorta nombre/descripcion y envía POST `/api/procedimientos`. No envía estado ni creador. Descripción vacía se convierte en null.
5. Backend valida nombre, resuelve usuario por identidad autenticada y guarda BORRADOR con creado_por/fecha_creacion. No crea Pasos. Responde 201 con DTO; Angular actualiza caché y navega a `/procedimientos/{id}` después de la respuesta, sin anunciar éxito anticipado.
6. El detalle consulta cabecera y pasos con GET separados; puede mostrar antes la caché de localStorage. Un nuevo BORRADOR sin pasos muestra estado vacío y acceso **+ Paso** para staff.
7. El modal de Paso recibe **Orden**, **Instrucción** y **Paso crítico**. Orden inicial es max(orden)+1. No hay selección de entidades externas: procedimientoId se toma de la URL/padre.
8. Guardar Paso valida entero positivo/instrucción, controla solicitud pendiente y envía POST; Editar usa PUT del Paso. Backend bloquea el padre, exige BORRADOR, comprueba orden/pertenencia y guarda en otra transacción.
9. Tras éxito, QA-013 muestra “Paso guardado”, cierra el modal y recarga GET de Pasos/materiales. Si ese GET posterior falla, el Paso ya está guardado: pueden coexistir aviso de éxito y error de recarga/lista antigua. No debe interpretarse como rollback del POST.
10. ADMINISTRADOR puede publicar desde listado o detalle con PATCH. Actualmente no hay confirmación previa ni condición de cantidad de pasos. Pasos conserva sus filas al cambiar estado; PUBLICADO bloquea ediciones por los servicios normales.

Editar cabecera usa el mismo formulario: GET inicial, precarga y PUT. Si GET devuelve PUBLICADO se deshabilita el formulario y se informa preservación histórica. Si GET falla, solo se quita loading y se muestra error: el formulario queda disponible, con editId ya asignado y soloLectura=false.

Cancelar/volver al listado/cambiar ruta o cerrar el modal de Paso descarta datos aún no guardados sin confirmación. No se encontró canDeactivate, beforeunload o comprobación dirty en este flujo. Los datos confirmados en servidor permanecen.

## 3. Modelo de datos y relaciones

| Entidad / tabla | Campos y restricciones JPA conocidas | Relación / historial |
|---|---|---|
| ProcedimientoEntity / `pr_procedimiento` | procedimiento_id IDENTITY; nombre no nulo, máximo 150; descripción TEXT opcional; estado enum BORRADOR/PUBLICADO | creado_por obligatorio y modificado_por opcional como relaciones Usuario; fecha_creacion y fecha_modificacion. Sin UNIQUE de nombre ni @Version declarados. |
| PasoEntity / `pr_paso` | paso_id IDENTITY; procedimiento_id escalar obligatorio; paso_orden obligatorio; instrucción TEXT obligatoria; esCritico boolean | Un Paso pertenece a un padre. UNIQUE declarado por procedimiento_id+paso_orden. Sin estado, auditoría ni @Version propio. |
| MaterialApoyoEntity / `co_material_apoyo` | conocimiento_id o paso_id opcionales; nombre, tipo, URL | Material por Paso reutiliza la entidad de apoyo, pero no vincula automáticamente ese Procedimiento a un Conocimiento. Los servicios verifican pertenencia. |
| EjecucionEntity / `se_ejecucion` | procedimiento_id, usuario, inicio/fin, estado EN_CURSO/COMPLETADA/CANCELADA, observaciones | Una ejecución referencia un Procedimiento publicado y un usuario. |
| EjecucionPasoEntity / `se_ejecucion_paso` | ejecución, Paso, cumplido, observación/fecha | UNIQUE declarado por ejecución+Paso; conserva cumplimiento y IDs. No guarda snapshot de instrucción/orden/crítico. |

No se encontró relación directa Procedimiento–Conocimiento, ni asignación a Hardware/Sistema/Departamento en la cabecera actual. La reutilización de MaterialApoyo y DTO de respuesta no constituye esa relación. No asumir que hace falta agregar selectores o FK.

La auditoría del Procedimiento cubre alta, edición de cabecera y publicación. Crear/modificar Paso no actualiza auditoría del padre ni identifica quién cambió ese Paso. El histórico de ejecución recupera textos y orden desde la definición actual; la inmutabilidad de PUBLICADO protege esas referencias frente a comandos normales. No hay snapshots/versiones ni auditoría completa de cambios.

PK/FK/UNIQUE/ON DELETE/CHECK/collation/longitudes físicas y motor InnoDB **no confirmados**: no se accedió a MySQL. Los IDs escalares no prueban una FK física. Los SQL locales no aportan DDL base de estas tablas.

## 4. Endpoints y autorización existentes

| Método / ruta | Operación | Validación / rol |
|---|---|---|
| GET `/api/procedimientos` | Listado por fecha_creacion DESC | Staff ve todos; USUARIO solo PUBLICADO. |
| GET `/api/procedimientos/{id}` | Cabecera | Visibilidad de PUBLICADO para USUARIO; staff puede BORRADOR. |
| POST `/api/procedimientos` | Crear cabecera BORRADOR, 201 | Nombre @NotBlank/@Size150; ADMINISTRADOR, TECNICO, SOPORTE. |
| PUT `/api/procedimientos/{id}` | Editar nombre/descripcion | DTO igual; solo BORRADOR; ADMINISTRADOR, TECNICO, SOPORTE. |
| PATCH `/api/procedimientos/{id}/estado` | Publicar | @NotNull estado; exclusivamente ADMINISTRADOR comprobado en Controller; solo BORRADOR→PUBLICADO. |
| GET `/api/procedimientos/{id}/pasos` | Pasos en orden ASC | Visibilidad del padre. |
| POST `/api/procedimientos/{id}/pasos` | Crear Paso, 201 | Orden obligatorio positivo, instrucción @NotBlank, esCritico @NotNull; staff y BORRADOR. |
| PUT `/api/procedimientos/{id}/pasos/{pasoId}` | Editar Paso propio | Mismos campos, pertenencia, orden no ocupado, staff y BORRADOR. |

POST/PUT de Procedimiento acepta `{nombre,descripcion?}`; no hay lista de Pasos en DTO. Estado y autor no se toman del body; el test existente demuestra que campos estado/creadoPor inyectados no publican ni cambian el creador. No existe endpoint compuesto de alta con pasos ni una clave de idempotencia.

Recursos relacionados: GET/POST/PUT y DELETE de Material por Paso, POST/GET de archivo; POST `/api/procedimientos/{id}/ejecuciones`, GET de ejecuciones/checklist y PATCH de cumplimiento/completar/cancelar. Su gestión no equivale a editar las definiciones de un Procedimiento.

No existen DELETE de Procedimiento/Paso, desactivación, activación genérica ni retorno a BORRADOR. Publicar no es activar un boolean; es una transición del enum. No convertir permisos operacionales de TECNICO/SOPORTE en exclusivos de ADMINISTRADOR sin justificación/aprobación. No hay restricción de edición de BORRADOR exclusivamente a su creador: cualquier staff autorizado puede editarlo.

Errores Backend: 400 por Bean Validation/JSON o estado incompatible (incluido editar PUBLICADO); 401/403 por autenticación/autorización; 404 por recursos ausentes/ajenos; 409 por orden duplicado/integridad; DataAccessException general se traduce a 503. Angular conserva mensajes 400 y 409; 0/5xx se muestran como fallo genérico. No certifica si el servidor confirmó un POST cuya respuesta se perdió.

## 5. Archivos involucrados

Raíz Backend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/`. Prefijo producción: `src/main/java/cl/casol/backend/`.

- `procedimiento/domain/Procedimiento.java`, `EstadoProcedimiento.java`, `Paso.java`.
- `procedimiento/application/service/MantenerProcedimientoService.java`, `MantenerPasoService.java`, `EdicionProcedimiento.java`, `MantenerMaterialPasoService.java`, `MaterialPasoArchivoService.java`.
- `procedimiento/application/port/out/ProcedimientoRepository.java`, `PasoRepository.java`.
- `procedimiento/infrastructure/web/ProcedimientoController.java`, `PasoController.java`, `MaterialPasoController.java`, `MaterialPasoArchivoController.java`.
- DTO `procedimiento/infrastructure/web/dto/GuardarProcedimientoRequest.java`, `CambiarEstadoProcedimientoRequest.java`, `ProcedimientoResponse.java`, `GuardarPasoRequest.java`, `PasoResponse.java`.
- Entidades `procedimiento/infrastructure/persistence/entity/ProcedimientoEntity.java`, `PasoEntity.java`; adaptadores `ProcedimientoRepositoryAdapter.java`, `PasoRepositoryAdapter.java`; repositorios `ProcedimientoJpaRepository.java`, `PasoJpaRepository.java`; mappers `ProcedimientoMapper.java`, `PasoMapper.java`.
- `ejecucion/application/service/GestionarEjecucionService.java`; entidades `ejecucion/infrastructure/persistence/entity/EjecucionEntity.java`, `EjecucionPasoEntity.java`; `conocimiento/infrastructure/persistence/entity/MaterialApoyoEntity.java`.
- `shared/infrastructure/security/SecurityConfig.java`; `shared/infrastructure/web/GlobalExceptionHandler.java`, `VisibilidadContenidoConfig.java`; `shared/application/service/VisibilidadContenidoService.java`.

Raíz Angular: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/`.

- `src/app/features/procedimiento/procedimiento-form.component.ts`, `.html`, `.css`.
- `src/app/features/procedimiento/procedimiento-lista.component.ts`, `.html`.
- `src/app/features/procedimiento/procedimiento-detalle.component.ts`, `.html`.
- `src/app/core/services/procedimiento.service.ts`, `ejecucion.service.ts`; `src/app/core/models/procedimiento.model.ts`.
- `src/app/shared/ui/material-apoyo-form.component.ts`, `loading-modal.component.ts`.
- `src/app/app.routes.ts`, `src/app/core/guards/auth.guard.ts`, `src/app/shared/layout/shell-layout.component.html`.
- Specs citados en sección 8 y documentos QA013_DIAGNOSTICO_PRUEBAS_PASOS.md/QA013_IMPLEMENTACION_PRUEBAS_PASOS.md.

## 6. Reglas implementadas y transacciones

- Nombre obligatorio, máximo 150 por DTO; descripción opcional sin límite DTO explícito. El servicio no añade validación de unicidad ni recorte; Angular recorta antes de HTTP.
- Alta siempre BORRADOR con usuario autenticado y fecha. Se admite alta sin pasos; es la única forma actual de empezar.
- BORRADOR significa editable por staff y no visible operacionalmente para USUARIO; no significa autosave o datos aún sin persistir. Staff puede consultar el borrador guardado y continuar más tarde.
- Publicación: solo ADMINISTRADOR, padre existente/BORRADOR, usuario resuelto; **no comprueba pasos ni materiales ni críticos**. PUBLICADO no puede editarse/republicarse ni volver a BORRADOR por API actual.
- Cambiar estado no agrega/elimina/renumera Pasos ni materiales. El guardado futuro de definiciones se rechaza si padre PUBLICADO.
- Orden de Paso obligatorio positivo, único dentro de su padre según servicio/mapeo; admite saltos y diferente numeración inicial. No se impone continuidad ni permuta atómica de dos órdenes ocupados.
- esCritico es boolean informativo; no hay bloqueo secuencial nuevo. Iniciar ejecución exige PUBLICADO y al menos un Paso; completar exige todos cumplidos.
- Crear/modificar/publicar cabecera y crear/modificar Paso son métodos @Transactional separados. Cada POST/PUT/PATCH es una unidad de trabajo; no hay transacción que abarque la navegación o varios HTTP.
- Si falla el POST de Paso antes de commit por excepción, su transacción debe revertir; padre y pasos anteriores confirmados quedan. Si falla solo el GET de recarga, o se pierde la respuesta después del commit, el Paso puede estar persistido. Validar el comportamiento físico en MySQL autorizado.
- El bloqueo PESSIMISTIC_WRITE del padre se usa en edición/publicación/cambios de Paso. No hay @Version/ETag/request de versión, por lo que no detecta edición basada en formulario antiguo. La efectividad de bloqueo/rollback debe probarse con múltiples conexiones InnoDB.

No hay cascada de borrado expuesta ni limpieza automática de BORRADOR incompleto. No proponer borrar el padre al fallar un Paso: puede contener trabajo previo y relaciones que deben conservarse.

## 7. Defectos y riesgos

| Hallazgo verificable o riesgo | Evidencia / consecuencia | Clasificación / prioridad |
|---|---|---|
| Validación Angular no refleja DTO | Validators.required admite espacios; no maxLength en FormControl (solo HTML). El servicio recorta y Backend rechaza vacío/>150. | Defecto de validación UX; alta, baja complejidad. |
| Edición habilitada tras error de precarga | editId se fija antes de GET; error solo apaga loading. Se puede enviar PUT con valores sin haber recuperado el registro. | Defecto de estado de carga; alta, baja/media. Bloquear hasta carga válida/reintento. |
| Cambios sin guardar se pierden sin aviso | Cancelar/links/ruta/cierre de Paso no revisan dirty o diferencia. | Mejora UX; alta, media. Confirmar criterio de aviso y no bloquear navegación tras éxito. |
| Publicación vacía | Publicar no consulta Pasos; inicio de ejecución rechaza vacío. | Brecha funcional; alta, requiere decisión. No inventar condición de publicación. |
| Creación y pasos no atómicos como conjunto | POST padre y cada Paso independientes. | Diseño actual; no llamarlo fallo transaccional. Explicar guardado incremental y continuidad del borrador. |
| Respuesta perdida/reintento puede duplicar padre | guardando protege solo instancia pendiente; Backend no tiene idempotencia ni UNIQUE de nombre declarado. | Riesgo de duplicidad, media/alta; comprobar UX de recuperación y decidir semántica antes de añadir índice/idempotencia. |
| Paso confirmado, recarga fallida | Aviso “Paso guardado” precede GET nuevo; error de recarga no revierte POST. | Riesgo de interpretación/doble reintento; media. Distinguir guardar y refrescar. |
| Navegación después de alta no controlada | router.navigate se invoca con void; guardando permanece true tras éxito. Si navegación se cancela/falla, puede quedar overlay Guardando pese a alta confirmada. | Riesgo por código, reproducción pendiente; media. No repetir POST para resolver navegación. |
| Estilos y template no coinciden ampliamente | CSS define .pf/.franja/.cuerpo/.tarjeta/.campo/.control/.btn-guardar; template usa section.formulario y campos/botones sin esas clases. .acciones/.alerta sí coinciden. | Deuda/defecto de integración visual; media. No afirmar apariencia real sin UAT visual y estilos globales. |
| Sin versión ni auditoría por Paso | Bloqueo serializa, pero último formulario puede sobrescribir; editar Paso no actualiza auditoría del padre. | Deuda técnica; media. Diseño antes de SQL. |
| Histórico referencial | Checklist guarda ID, lee definición actual; protegido por inmutabilidad PUBLICADO, sin snapshot. | Riesgo si se aprueba reapertura o escritura externa; preservar restricción. |
| Cache de listado puede ocultar fallo HTTP | ProcedimientoService.listar usa cache como fallback cuando existe; sin aviso claro de datos antiguos. | Riesgo UX de frescura; media, sin cambio en este diagnóstico. |

No se reprodujeron estos casos contra STAGING ni MySQL. Se distinguen comportamiento demostrado en código y escenarios que requieren prueba. No se diagnosticó duplicación real de datos del servidor.

QA-013 verificado en fuentes: posición vs orden con saltos; entero positivo; guardado de Paso sin confirmación anticipada; formulario mantenido ante error; mensajes 409 preservados; métodos Angular y Backend restringidos a BORRADOR. El formulario general no recibió esas validaciones de Paso y sigue separado; no es una regresión de QA-013.

Dependencias de despliegue: Procedimiento/Paso no referencian directamente columnas nuevas de QA-009/QA-012, pero el JAR consolidado sí incorpora esos módulos. Antes de futura ejecución autorizada se necesitan revisión/aplicación de V20261013_01__ampliar_solucion.sql y V20261014_01__organizacion_persistente.sql según preflight aprobado. No omitirlas por tratarse de un procedimiento independiente. No se preparó SQL nuevo en QA-014.

## 8. Cobertura existente y faltante

Se leyeron tests y reportes previos de QA-013, no se ejecutaron pruebas QA-014. Las últimas suites completas documentadas tienen 645 Backend y 141 Angular satisfactorias; no equivalen a pruebas MySQL ni a UAT de Nuevo Procedimiento.

| Cobertura existente | Reporte previo / alcance |
|---|---|
| MantenerProcedimientoServiceTest | 8 casos: alta BORRADOR/usuario, auditoría en edición, publicación/transición/republicación, ausente/listado, bloqueo PUBLICADO y visibilidad. Mocks. |
| MantenerPasoServiceTest | 12: alta/edición/pertenencia/orden/ausencia/saltos y restricciones PUBLICADO. Mocks. |
| Hu05SecurityIntegrationTest | 10: alta admin/técnico, publicación admin/técnico403, DTO, ausente, Paso inválido/409/orden/material, 401/rol. Servicios simulados. |
| GestionarEjecucionServiceTest | 22: ejecución PUBLICADO/no vacío, checklist, cumplimiento, estados/autoría/admin, conservación de IDs. |
| MantenerMaterialPasoServiceTest | 7: material/pertenencia/estado del padre. |
| pasos-qa013.component.spec.ts | 5: posición vs orden, fracción inválida, PUBLICADO, éxito tras HTTP/recarga y error conserva formulario. |
| admin-pruebas-api.service.spec.ts | Incluye errores Paso 409 y PUBLICADO400 de QA-013. |
| procedimiento-material-archivo.service.spec.ts | Material/HTTP, no alta de cabecera con pasos. |

No se encontró spec específico de ProcedimientoFormComponent ni cobertura extremo a extremo del recorrido Nuevo→detalle→Pasos→publicación. No hay evidencia de pruebas InnoDB de persistencia/concurrencia ni de rollback de múltiples solicitudes.

Pruebas necesarias si se autoriza implementar:

1. Nombre vacío/blanco/150/151, descripción vacía/opcional, normalización y validaciones DOM/API.
2. Precarga exitosa BORRADOR/PUBLICADO, GET 404/401/403/503 y no permitir PUT antes de carga válida.
3. POST único mientras pendiente, respuesta exitosa/navegación fallida, error HTTP conserva datos, respuesta perdida y recuperación sin repetir alta inadvertidamente.
4. Guardado padre→Paso fallido conserva BORRADOR; varios Pasos con fallo intermedio conservan confirmados; fallo del GET posterior se informa separadamente.
5. Crear/editar Paso con orden ocupado, saltos y pertenencia; mantener validación y restricciones QA-013. Permuta/reordenamiento solo si se aprueba.
6. Publicación vacía según decisión; técnico no publica; ADMINISTRADOR publica; POST/PUT staff y USUARIO403; escritura de PUBLICADO rechazada.
7. Abandonar dirty/pristine/enviando/guardado y modal, navegador/móvil/teclado/zoom; consistencia CSS real.
8. MySQL aislado autorizado: PK/FK/UNIQUE reales, commit/rollback por comando, dos conexiones publicación vs Paso, stale edits, IDs/histórico y persistencia tras reiniciar/nueva sesión. No ejecutar migraciones sin autorización.

## 9. Mejoras propuestas y prioridades

| Etapa | Propuesta | Alcance / complejidad / dependencias |
|---|---|---|
| A — Alta | Alinear validaciones de nombre y bloquear edición tras precarga fallida, con reintento | Angular; baja/media. Sin esquema o endpoints nuevos. |
| B — Alta | Explicar “Guardar borrador y agregar pasos”; separar errores de guardado/recarga/navegación; mantener datos para recuperación | Reutilizar pantalla y servicios; baja/media. No simular rollback global. |
| C — Alta | Aviso de cambios sin guardar según criterio aprobado, respetando requests pendientes | Angular, media; pruebas navegación. No persistir automáticamente datos locales. |
| D — Alta condicionada | Decidir mínimo de pasos y confirmación de publicación | Regla funcional pendiente; Backend/Angular si se aprueba. Conservar exclusividad ADMINISTRADOR y bloqueo del padre. |
| E — Media | Reconciliar template/CSS con componentes y estilos actuales | Angular; baja/media; UAT visual. Evitar duplicar un wizard sin necesidad. |
| F — Media | Diseñar recuperación/idempotencia, edición concurrente y auditoría según necesidad MVP | Backend/Angular, posible SQL aditivo revisable posterior. No imponer unicidad de nombre. |
| G — Posterior | Alta agregada con pasos o reordenamiento atómico, solo si UAT necesita un único guardado | Nueva funcionalidad media/alta; no reemplazar flujo incremental sin acuerdo. |

Mantener Java21/SpringBoot4/hexagonal y Angular19; reutilizar ProcedureForm/Detalle/Service y material existente. Ejecutar regresión QA-009 a QA-013 al autorizar cambios. No tocar validación de Responsables pendiente de QA-012 ni migraciones existentes por anticipación.

## 10. Decisiones funcionales pendientes

- ¿Se conserva guardado incremental de BORRADOR, o se requiere alta atómica de cabecera+Pasos? El flujo actual permite continuar un borrador, no un wizard con rollback global.
- ¿Publicar exige al menos un Paso? ¿Confirmación previa de publicación irreversible por API actual? No hay esas condiciones hoy.
- ¿Nombres iguales son válidos? No existe prohibición implementada; no tratar el nombre como clave de idempotencia.
- ¿Qué cambios ameritan aviso de descarte? Definir comportamiento durante guardado y tras persistencia confirmada.
- ¿Se necesitan auditoría de cambios de Paso/versiones y control de edición obsoleta para MVP? No confundir con timestamps actuales del padre.
- ¿Se requiere reordenamiento colectivo o numeración consecutiva? Mantener saltos hasta decisión.
- Cualquier reapertura/versionado/baja del Procedimiento publicado debe preservar ejecuciones/histórico; no autorizada ni implementada. La relación con Conocimientos o nuevos selectores tampoco debe inventarse.

**Diagnóstico QA-014 entregado; implementación pendiente de autorización.**
