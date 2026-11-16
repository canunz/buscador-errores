# QA-011 — Diagnóstico funcional y técnico de Módulos

Fecha: 2026-10-13, America/Santiago. Proyecto DimaBug, estabilización local del MVP.

## 1. Resumen ejecutivo

El CRUD administrativo de Módulos ya existe, reutilizando el componente y el controlador genéricos de catálogos. Permite listar, consultar detalle, crear, editar y activar/desactivar. No hay eliminación física expuesta.

El acceso local está en Administrador → Módulos → Gestionar, ruta `/admin/catalogo/modulos`. No hay enlace directo a Módulos en el menú lateral de Catálogos. La interfaz no incorpora filtros por Sistema/estado y no informa preventivamente del impacto de desactivar un Módulo referenciado.

No existe descripción propia de Módulo en la entidad, dominio ni formulario. El DTO genérico tiene `descripcion`, pero el adaptador de Módulos no la persiste: recibir ese atributo no significa que la funcionalidad esté implementada.

La validación de aplicación impide duplicados dentro del mismo Sistema, incluidos registros inactivos, y permite el mismo nombre en Sistemas diferentes. Las restricciones físicas MySQL NO están confirmadas. No hay DDL base de cl_modulo en los SQL disponibles.

No se modificó código ni se ejecutaron tests, builds, migraciones o consultas de base de datos. Solo se creó este informe. QA-009 y QA-010 se preservaron. No hubo commits, reset, clean, stash ni operaciones destructivas; no se accedió a STAGING/PRODUCCIÓN ni se inició trabajo de otros módulos.

## 2. Funcionalidades implementadas

| Funcionalidad | Estado y evidencia |
|---|---|
| Listado administrativo | GET del catálogo modulos devuelve activos e inactivos, ordenados por ID. |
| Detalle administrativo | GET por ID, incluido un Módulo inactivo. |
| Alta | POST del catálogo con nombre, sistemaId y activo. |
| Edición | PUT conserva ID y valida Sistema, duplicados y uso en conocimientos. |
| Estado lógico | PATCH /estado y campo Activo en PUT. No elimina el registro. |
| Consulta operacional | GET de Módulos de un Sistema activo; solo Módulos activos, ordenados por nombre. |
| Relación con Sistema | ManyToOne; un Sistema puede tener múltiples Módulos. |
| Seguridad | Administración solo ADMINISTRADOR en Backend y Angular. TECNICO conserva lectura operacional. |
| Preservación histórica | Las referencias de conocimientos no se eliminan al desactivar el Módulo. |
| Reindexación | Guardar/cambiar estado reindexa conocimientos referenciados no eliminados. |

Arquitectura existente: Controller → Application Service → Port → Persistence Adapter. No hace falta crear otro CRUD.

## 3. Funcionalidades faltantes y oportunidades

| Hallazgo | Clasificación | Propuesta pendiente de aprobación | Complejidad |
|---|---|---|---|
| Sin enlace lateral a Módulos | Mejora de navegación | Añadir enlace ADMINISTRADOR a la ruta existente. | Baja |
| Sin filtros por Sistema/estado ni búsqueda local | Mejora UX | Filtrar la colección ya cargada; mantener estado Todos/Activos/Inactivos. | Baja |
| Columna Sistema llamada Detalle | Mejora UX | Etiqueta específica para Módulos y estado del Sistema padre cuando sea relevante. | Baja |
| Sistema inactivo elegido originalmente pierde posibilidad de revertir al cambiar selector | Bug UX verificable por condición de plantilla; no reproducido en navegador en esta revisión | Usar sistemaId inicial para permitir conservar/revertir referencia original, sin habilitar nuevas asociaciones inactivas. | Media |
| Módulo bajo Sistema inactivo puede seguir marcado activo | Dependencia de negocio que la UI no explica | Mostrar aviso; no desactivar ni reasignar automáticamente. | Baja |
| Reactivación bajo Sistema inactivo se ofrece pero falla en Backend | Mejora UX | Explicar la restricción antes de confirmar; mantener validación Backend. | Baja |
| Cambio de Sistema de Módulo utilizado se ofrece y termina en 409 | Mejora UX | Informar de la restricción/uso; evitar prometer que puede trasladarse. | Media |
| Desactivación sin información de conocimientos relacionados | Mejora UX | Proponer aviso de impacto sin bloqueo adicional ni cambios automáticos. | Media |
| Descripción de Módulo no implementada | Nueva funcionalidad, no bug demostrado | Confirmar necesidad antes de ampliar modelo/API/BD. No añadir columna por su presencia en DTO genérico. | Media |
| Sin cierre en cabecera de modal de Módulos | Mejora UX | Evaluar consistencia con Hardware, conservando Cancelar. | Baja |
| Sin confirmación de descarte | Mejora UX opcional | Decidir si se requiere antes de introducirla. | Media |
| Cobertura específica de reglas de Módulos limitada | Deuda de pruebas | Añadir matriz de casos antes de corregir comportamiento. | Media |

## 4. Endpoints existentes y HTTP

| Método | Ruta | Uso / respuesta exitosa |
|---|---|---|
| GET | /api/admin/catalogos/modulos | Colección completa; 200. |
| GET | /api/admin/catalogos/modulos/{id} | Detalle; 200. |
| POST | /api/admin/catalogos/modulos | Crear; 201. |
| PUT | /api/admin/catalogos/modulos/{id} | Editar; 200. |
| PATCH | /api/admin/catalogos/modulos/{id}/estado | Activar/desactivar con {"activo":boolean}; 200. |
| GET | /api/sistemas/{id}/modulos | Módulos activos de Sistema activo; 200. |

No existe controlador operacional `/api/modulos` independiente; no se necesita duplicarlo para administrar. No existe DELETE administrativo: el test MVC verifica 405 para administrador. Un rol no administrador es rechazado antes por seguridad.

Respuestas previstas en el código:

- 401 sin autenticación.
- 403 para TECNICO/SOPORTE/USUARIO al administrar `/api/admin/**`.
- 400 para nombre vacío/mayor de 120, sistemaId ausente/no positivo, estado ausente, asociación/reactivación no permitida con Sistema inactivo.
- 404 para Módulo/Sistema inexistente; Sistema inactivo también no se encuentra en la consulta operacional.
- 409 para duplicado por Sistema, cambio de Sistema de Módulo utilizado y conflictos físicos de integridad detectados por MySQL.

El GET operacional `/api/sistemas/{id}/modulos` está permitido a ADMINISTRADOR, TECNICO, SOPORTE y USUARIO autenticados por la configuración actual. No se propone alterar esos permisos.

DTO operacional ModuloResponse: id, sistemaId y nombre, sin estado porque la consulta solo devuelve activos. DTO administrativo: id, nombre, activo, sistemaId y campos genéricos de catálogo; descripcion es null para Módulos.

## 5. Archivos involucrados

Raíz Backend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend`.

Bajo `src/main/java/cl/casol/backend/`:

- catalogo/domain/Modulo.java
- catalogo/domain/TipoCatalogo.java
- catalogo/domain/CatalogoAdministrativo.java
- catalogo/infrastructure/persistence/entity/ModuloEntity.java
- catalogo/infrastructure/persistence/entity/SistemaEntity.java
- catalogo/infrastructure/persistence/repository/ModuloJpaRepository.java
- catalogo/infrastructure/persistence/adapter/ModuloRepositoryAdapter.java
- catalogo/infrastructure/persistence/adapter/CatalogoAdministrativoRepositoryAdapter.java
- catalogo/infrastructure/persistence/mapper/CatalogoMapper.java
- catalogo/application/port/out/ModuloRepository.java
- catalogo/application/port/out/CatalogoAdministrativoRepository.java
- catalogo/application/service/AdministrarCatalogosService.java
- catalogo/application/service/ConsultarSistemasService.java
- catalogo/infrastructure/web/AdminCatalogoController.java
- catalogo/infrastructure/web/SistemaController.java
- catalogo/infrastructure/web/dto/GuardarCatalogoRequest.java
- catalogo/infrastructure/web/dto/CambiarEstadoCatalogoRequest.java
- catalogo/infrastructure/web/dto/CatalogoAdministrativoResponse.java
- catalogo/infrastructure/web/dto/ModuloResponse.java
- conocimiento/infrastructure/persistence/entity/ConocimientoEntity.java
- conocimiento/application/service/MantenerConocimientoService.java
- shared/infrastructure/security/SecurityConfig.java
- shared/infrastructure/web/GlobalExceptionHandler.java

SQL disponibles bajo `src/main/resources/db/migration/`:

- V20260925_01__agregar_estado_eliminado_conocimiento.sql: enum del conocimiento.
- V20261013_01__ampliar_solucion.sql: ampliación QA-009; pendiente, no ejecutada.

Ninguno define cl_modulo, FK de Módulos o su unicidad.

Raíz Angular: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug`.

- src/app/features/admin/admin.component.ts y .html
- src/app/features/admin/admin-catalogo.component.ts, .html y .css
- src/app/core/services/admin-catalogos-api.service.ts
- src/app/core/services/clasificacion.service.ts
- src/app/features/conocimiento/conocimiento-form.component.ts
- src/app/shared/layout/shell-layout.component.html
- src/app/shared/ui/catalogo-page.css
- src/app/app.routes.ts
- src/app/core/guards/auth.guard.ts

Solo como referencia visual solicitada: Departamentos utiliza catalogo-page.css y su propio componente de consulta; no se auditó ni se propone modificar su funcionalidad en QA-011.

## 6. Reglas de negocio actuales

### Nombre y descripción

Nombre obligatorio, trim en servidor y máximo 120 caracteres. La búsqueda de duplicados aplica trim y equalsIgnoreCase sobre TODOS los Módulos, también inactivos. Excluye el ID que se está editando y exige coincidencia de sistemaId para declarar duplicado.

Por aplicación: " Caja " y "caja" en el mismo Sistema colisionan; Caja en Sistemas distintos se permite. No se normalizan espacios interiores ni se define equivalencia de acentos. La collation/índices reales pueden aplicar reglas diferentes; no se comprobó MySQL.

No hay modulo_descripcion mapeado. La validación @Size(255) de descripcion pertenece al DTO genérico y no demuestra persistencia de ese dato en Módulos.

### Sistema y estado

| Situación | Resultado actual |
|---|---|
| Alta sin Sistema | Rechazo 400. |
| Alta con Sistema inexistente | Rechazo 404. |
| Alta con Sistema inactivo, incluso Módulo inicialmente inactivo | Rechazo 400. |
| Cambiar a otro Sistema inactivo | Rechazo 400. |
| Editar nombre conservando Sistema actualmente inactivo | Permitido, si no implica reactivar un Módulo inactivo. |
| Desactivar Módulo bajo Sistema inactivo | Permitido. |
| Reactivar Módulo bajo Sistema inactivo | Rechazo 400. |
| Cambiar Sistema sin referencias en conocimientos | Permitido si destino activo y nombre no colisiona. |
| Cambiar Sistema con referencias en conocimientos | Rechazo 409. Incluye conocimientos ELIMINADOS. |
| Desactivar Módulo utilizado | Permitido; no altera referencias ni elimina conocimientos. |

`moduloReferenciado` consulta co_conocimiento sin filtro de estado. El bloqueo de traslado protege también referencias históricas. La desactivación no comprueba uso, porque conservar esas referencias está permitido en el comportamiento actual.

Los cambios administrativos toman bloqueos pesimistas primero sobre Sistemas y después sobre Módulos. La estrategia serializa las escrituras de catálogos, pero no equivale a una prueba física de concurrencia de toda la clasificación.

### Conocimientos

Crear/cambiar clasificación exige un Módulo activo perteneciente al Sistema seleccionado. Al editar un conocimiento con la misma clasificación completa, el servicio conserva sus referencias aunque estén inactivas. Angular incluye referencias existentes que no llegan en los catálogos activos.

Si cambia cualquier atributo de clasificación, se vuelve a validar todo el conjunto; una referencia inactiva conservada hasta ese momento puede entonces producir rechazo. La desactivación de un Módulo no retira automáticamente el conocimiento publicado ni su contenido de búsqueda: no existe una regla que lo exija.

## 7. Esquema previsto y riesgos de integridad

| Restricción | Evidencia JPA/SQL local | Confirmación física MySQL |
|---|---|---|
| PK modulo_id | @Id y generación IDENTITY. | No comprobada. |
| FK sistema_id → cl_sistema | @ManyToOne y @JoinColumn. | No comprobada; ON DELETE desconocido. |
| Sistema obligatorio en BD | No declarado: falta nullable=false/optional=false en la relación. El servicio sí exige Sistema. | No comprobada. |
| Nombre NOT NULL, longitud 120 | @Column(nullable=false,length=120). | No comprobada. |
| Unicidad (sistema_id,nombre) | No hay @UniqueConstraint ni DDL local. La aplica el servicio. | No comprobada. |
| Campo modulo_estado | Boolean, @Column(nullable=false). No default SQL declarado. | No comprobada. |
| Conocimiento → Módulo | ConocimientoEntity tiene modulo_id y sistema_id separados. | FK/acciones físicas no comprobadas. |

No existe cascada de borrado declarada en las relaciones revisadas ni endpoint DELETE de Módulos. Que JpaRepository disponga técnicamente de delete no significa que exista un caso de uso autorizado/expuesto.

Riesgos:

1. Índice físico global por nombre, si existiera, podría contradecir la regla de repetición entre Sistemas. No afirmar su existencia o ausencia sin SHOW CREATE TABLE.
2. Relación Sistema nullable en el mapeo puede permitir datos heredados sin padre si la BD no lo impide. Auditar antes de proponer NOT NULL.
3. Las FK individuales no garantizan que co_conocimiento.sistema_id coincida con el Sistema del Módulo. La protección actual reside en servicios.
4. El conteo de referencias para traslado puede competir con una creación/cambio de clasificación de conocimiento: esa operación no toma el mismo bloqueo de catálogo de Módulos. Riesgo técnico por verificar, no fallo reproducido. No cambiar el protocolo de bloqueo sin pruebas y aprobación.
5. Un Sistema inactivo puede conservar Módulos con estado activo; evitar confundir estado propio con disponibilidad operacional.
6. La reindexación usa el estado actual; no hay evidencia de versionado del nombre del Módulo. Renombrar no elimina referencias, pero los registros antiguos pueden mostrar el nombre actualizado.
7. Filtros/cachés desde otra sesión pueden quedar temporalmente desactualizados; Backend debe seguir siendo la autoridad de validación.

No se justifica migración para añadir acceso lateral o filtros. Descripción, unicidad física o nulabilidad requieren verificación previa y aprobación separada.

## 8. Interfaz y navegación

La ruta `/admin/catalogo/:tipo` usa adminGuard; el panel Administrador incluye modulos mediante TIPOS_CATALOGO_ADMIN. La fila genera Gestionar → `/admin/catalogo/modulos`. No existe enlace lateral equivalente al incorporado para Sistemas en QA-010.

El listado muestra Nombre, Detalle (nombre del Sistema) y Estado, con Editar y Activar/Desactivar. Carga todos los Sistemas administrativos, incluidos inactivos, para resolver nombres. No hay filtros ni ordenamiento interactivo.

Formulario: nombre, selector obligatorio de Sistema y switch Activo. No hay descripción de Módulo. Ofrece Cancelar y Guardar, conserva datos al recibir error del servidor y recarga después del éxito.

El selector deshabilita un Sistema inactivo salvo que su ID sea el VALOR ACTUAL del control. Por eso cambiar desde un Sistema inactivo a uno activo deshabilita la opción anterior: no permite revertir esa decisión dentro de la misma edición, aunque el Backend admite conservar el Sistema original. Es análogo al problema corregido en checkboxes de Hardware, pero esa corrección QA-010 no aplica al selector de Módulos.

Hay confirmación para el botón dedicado Activar/Desactivar. Cambiar el switch Activo dentro del formulario guarda el estado por PUT sin esa confirmación. Ante fallo del PATCH se cierra el diálogo y se muestra el mensaje arriba del listado. Estos comportamientos requieren decidir consistencia UX, no modificar permisos.

Consistencia: Sistemas y Módulos comparten exactamente componente y estilos; Hardware añade sección/asociaciones y cierre en cabecera solo para tipo hardware. Departamentos comparte estilos base pero tiene otra presentación de consulta. La revisión de contraste, foco y móvil necesita UAT visual; no se reprodujo en navegador en este diagnóstico.

## 9. Pruebas existentes y faltantes

| Caso | Cobertura existente inspeccionada | Prueba faltante recomendada |
|---|---|---|
| Crear Módulo válido | AdminCatalogoSecurityIntegrationTest recorre todos los tipos, con servicio simulado. | Servicio + persistencia de alta de Módulo y formulario Angular. |
| Nombre vacío/120/121 | DTO compartido; test de vacío actualmente para Hardware. | Casos específicos de Módulos, espacios y límites. |
| Duplicados | Test genérico de duplicado en Frecuencias. | Mismo Sistema, otro Sistema, inactivos, edición del mismo ID y collation real. |
| Sistema activo | Consulta de Módulos probada en ConsultarSistemasServiceTest. | Alta/edición con Sistema activo. |
| Sistema inactivo | AdministrarCatalogosServiceTest.moduloNecesitaSistemaActivo. | Cambio a inactivo, conservación original, reactivación y cambio de Sistema padre. |
| Sistema nulo/inexistente | Validaciones del código; cobertura compartida parcial. | Servicio y MVC específicos. |
| Cambiar Sistema utilizado | AdministrarCatalogosServiceTest.noMueveModuloReferenciado. | Referencias PUBLICADO/BORRADOR/ELIMINADO y persistencia del rechazo. |
| Cambiar Sistema sin uso | No caso específico localizado. | Traslado válido y duplicado en destino. |
| Desactivar/reactivar | MVC genérico para cuatro catálogos y desactivación. | Reglas reales de reactivación y conocimientos conservados. |
| Relación conocimiento–Módulo | MantenerConocimientoServiceTest valida caso completo y Módulo de otro Sistema. | Desactivar y seguir consultando conocimiento; edición con clasificación idéntica. |
| ADMINISTRADOR/TECNICO | AdminCatalogoSecurityIntegrationTest permite admin/rechaza técnico; CatalogoSecurityIntegrationTest permite GET operacional al técnico. | Navegación y controles Módulos según rol, sin duplicar pruebas generales innecesarias. |
| Angular CRUD | admin-catalogo.component.spec.ts actualmente orientado a Hardware; servicio API/guards compartidos probados. | Alta/edición/estado/filtros y selector de Módulos, mensajes 400/404/409. |
| Concurrencia y BD | No prueba física específica de Módulos localizada. | Duplicados concurrentes, traslado frente a alta de conocimiento, FK/unicidad/nulabilidad en MySQL aislado autorizado. |

Archivos de pruebas inspeccionados:

- Backend: catalogo/application/service/AdministrarCatalogosServiceTest.java, catalogo/application/service/ConsultarSistemasServiceTest.java, AdminCatalogoSecurityIntegrationTest.java, CatalogoSecurityIntegrationTest.java y conocimiento/application/service/MantenerConocimientoServiceTest.java, bajo src/test/java/cl/casol/backend/.
- Angular: src/app/features/admin/admin-catalogo.component.spec.ts, src/app/core/services/admin-catalogos-api.service.spec.ts y src/app/core/guards/auth.guard.spec.ts.

No se ejecutaron pruebas en esta etapa. Los resultados anteriores de QA-010 (552 Backend/74 Angular) pertenecen a aquella ejecución y no se presentan como una validación nueva de QA-011.

## 10. Propuesta por etapas, pendiente de autorización

1. Aprobar el alcance: acceso lateral, filtros, claridad del Sistema padre, comportamiento del selector original y mensajes. Confirmar si se necesita descripción propia; no asumirla.
2. Añadir casos específicos de Módulos y conservar la regresión QA-009/QA-010.
3. Angular: reutilizar AdminCatalogoComponent y ruta; cambios condicionados a tipo modulos para evitar regresiones en otros catálogos. Añadir navegación solo ADMINISTRADOR, filtros locales y aviso del estado del Sistema.
4. Backend: mantener endpoints y reglas existentes; mejorar errores/cobertura. Evaluar por separado detección de impacto y coordinación de traslado con escrituras de conocimientos, antes de cambiar bloqueo o reglas.
5. MySQL: confirmar restricciones físicas mediante consultas de solo lectura en un entorno autorizado. No añadir SQL sin justificarlo con esa evidencia; no ejecutar migraciones durante QA-011 diagnóstico.
6. Validación posterior autorizada: pruebas de servicio/MVC/Angular, build y UAT móvil/teclado; persistencia/concurrencia físicas en entorno aislado. No desplegar automáticamente.

Prioridad: navegación/filtros/claridad y pruebas específicas primero; integridad/concurrencia antes de ampliar traslados; descripción y posibles cambios de esquema después de aprobación explícita. No avanzar a otro módulo sin autorización.
