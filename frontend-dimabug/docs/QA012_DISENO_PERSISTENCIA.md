# QA-012 — Diseño de persistencia de Departamentos y Contactos

Fecha: 2026-10-13, America/Santiago. Propuesta técnica local, pendiente de decisiones funcionales y autorización de implementación.

Se revisó QA012_DIAGNOSTICO_DEPARTAMENTOS.md, entidades, puertos, consultas, asignaciones y almacenamiento Angular. Solo se creó este documento. No se modificó código, no se ejecutaron pruebas, SQL o migraciones, no se accedió a STAGING/PRODUCCIÓN ni se realizaron commits u operaciones destructivas. QA-009, QA-010 y QA-011 se preservan.

## 1. Modelo actual

| Tabla | Campos mapeados | Restricciones conocidas |
|---|---|---|
| u_departamento | departamento_id, departamento_nombre, departamento_estado | PK IDENTITY; nombre/estado no nulos. Sin unicidad explícita en JPA. |
| u_departamento_contacto | contacto_id, departamento_id, contacto_tipo, contacto_valor, contacto_estado | PK IDENTITY; padre/tipo/valor/estado no nulos; tipo máximo 50, valor máximo 100. |
| u_responsable | responsable_id, departamento_id, responsable_nombre, responsable_cargo, responsable_contacto, responsable_estado | PK IDENTITY; padre/nombre/estado no nulos; cargo/contacto opcionales. |

Cada Contacto y Responsable pertenece a un solo Departamento. Un Departamento puede tener múltiples Contactos y Responsables. departamento_id en hijos es un Integer mapeado como columna; no una relación @ManyToOne. No existe N:M ni enlace entre responsable_contacto y las filas de Contactos del Departamento.

co_solucion_asignacion referencia Departamento y/o Responsable mediante relaciones JPA opcionales, y a Solución de forma obligatoria. Es la relación indirecta con conocimientos/derivaciones. Contactos no están referenciados directamente desde esa asignación en el modelo inspeccionado.

Las consultas actuales solo devuelven activos. GET de hijos exige padre activo. No hay escritura organizacional en servicios/puertos ni endpoints. Las PK/FK, ON DELETE, índices, longitud y collation físicas de MySQL permanecen SIN confirmar: los SQL disponibles no contienen DDL base de estas tablas.

No se propone cambiar el modelo de pertenencia ni añadir descripción.

## 2. Contrato REST propuesto

Reutilizar /api/admin/**, que ya exige ADMINISTRADOR. Conservar las consultas operacionales /api/departamentos y sus permisos de ADMINISTRADOR/TECNICO/SOPORTE.

### Departamentos

| Método | Endpoint nuevo propuesto | Resultado |
|---|---|---|
| GET | /api/admin/departamentos | Todos, incluidos inactivos; 200. |
| GET | /api/admin/departamentos/{id} | Detalle administrativo; 200/404. |
| POST | /api/admin/departamentos | Crear; 201 con Location. |
| PUT | /api/admin/departamentos/{id} | Editar nombre; 200. |
| PATCH | /api/admin/departamentos/{id}/estado | Desactivar/reactivar; 200. |

No implementar DELETE. El estado se modifica mediante PATCH, evitando que editar el nombre sobrescriba un cambio administrativo concurrente.

Contratos orientativos, con control optimista recomendado aún pendiente de aprobación del esquema:

```json
// POST
{"nombre":"Soporte TI"}
// PUT
{"nombre":"Soporte Técnico","version":3}
// PATCH /estado
{"activo":false,"version":3}
// Respuesta
{"id":7,"nombre":"Soporte TI","activo":true,"version":3}
```

Crear activo por defecto es una propuesta funcional. Si se aprueba alta inicialmente inactiva, definir un atributo explícito de creación sin reutilizar el PUT de edición.

### Contactos anidados

| Método | Endpoint nuevo propuesto | Resultado |
|---|---|---|
| GET | /api/admin/departamentos/{departamentoId}/contactos | Todos los Contactos propios, incluidos inactivos; 200. |
| GET | /api/admin/departamentos/{departamentoId}/contactos/{id} | Detalle, validando pertenencia; 200/404. |
| POST | /api/admin/departamentos/{departamentoId}/contactos | Crear en ese padre; 201 con Location. |
| PUT | /api/admin/departamentos/{departamentoId}/contactos/{id} | Editar tipo/valor; 200. |
| PATCH | /api/admin/departamentos/{departamentoId}/contactos/{id}/estado | Cambiar estado; 200. |

```json
// POST
{"tipo":"Anexo","valor":"618"}
// PUT
{"tipo":"Anexo","valor":"619","version":2}
// PATCH /estado
{"activo":false,"version":2}
// Respuesta administrativa
{"id":12,"departamentoId":7,"tipo":"Anexo","valor":"618","activo":true,
 "departamentoActivo":false,"disponible":false,"version":2}
```

El padre viene exclusivamente de la URL; no admitir reasignación por body. Tipo y valor permanecen strings: valor no es necesariamente número. MVP sin traslado de Contactos ni eliminación física.

Los GET administrativos de hijos deben funcionar con Departamento inactivo, a diferencia de las consultas operacionales actuales. No confundir la lectura administrativa completa con duplicación del endpoint operacional, que tiene otro alcance.

Respuestas:

- 400: campos vacíos, límites o formatos acordados, versión ausente/inválida.
- 401/403: autenticación/rol.
- 404: padre/hijo inexistente o Contacto ajeno al padre de la URL.
- 409: duplicado, transición de negocio prohibida o versión obsoleta.
- 200 en cambio sin efecto con precondición vigente; no modificar hijos por repetir un comando.

### Responsables: evaluación separada

Recomendación MVP: aplazar CRUD de Responsables, mantener consultas/selección existentes y corregir coherencia de disponibilidad si se aprueba. Un Departamento nuevo puede usarse como destino sin Responsable porque la asignación actual admite departamentoId solo; no es imprescindible crear personas para cada Departamento nuevo.

El CRUD será necesario si UAT exige altas/cambios frecuentes de personas sin intervención externa. En ese caso requiere aprobación separada de identidad/duplicados, traslado, histórico y auditoría. No basta copiar el CRUD de Contactos: un traslado puede dejar incoherente el Departamento almacenado en asignaciones antiguas.

No crear relaciones N:M ni endpoints de Responsables por anticipación. Evaluar una lectura administrativa de Responsables incluidos inactivos solo si se necesita para administrar impacto/históricos.

## 3. Reglas propuestas y decisiones funcionales pendientes

Todas las reglas nuevas siguientes requieren aprobación; no se presentan como implementadas.

| Decisión | Recomendación | Razón / efecto |
|---|---|---|
| Unicidad de Departamentos | Global, incluyendo inactivos, con trim y comparación sin distinción de mayúsculas. | Evita duplicar una entidad desactivada; reactivar la existente. Definir tratamiento de acentos y espacios interiores acorde a collation. |
| Longitud de nombre | Obligatorio; límite explícito compatible con el esquema verificado. | JPA no fija un máximo específico distinto del predeterminado; no inventar 100/120 sin aprobación/verificación. |
| Duplicados de Contactos | Por Departamento + tipo + valor normalizados; incluir inactivos. | Permitir mismo valor en otro Departamento como registro independiente. Definir normalización por tipo sin alterar teléfonos/anexos/correos indiscriminadamente. |
| Tipos de Contacto | Mantener texto hasta aprobar lista de tipos y validaciones específicas. | No deducir enum ni exigir formato telefónico para todo valor. |
| Desactivar Departamento | Conservar filas y estados propios de hijos, asignaciones e históricos. | Los hijos pueden seguir marcados activos pero quedan no disponibles operacionalmente. No cascada automática. |
| Reactivar Departamento | Recuperar disponibilidad de hijos que ya estaban activos; no reactivar los inactivos. | Mostrar advertencia de ese efecto y preservar decisiones individuales. |
| Crear/reactivar Contacto bajo padre inactivo | Rechazar con 409; pedir reactivación del padre. | No ofrecer un destino no disponible. La lectura administrativa sigue permitida. |
| Editar un Contacto existente bajo padre inactivo | Permitir datos, sin activación implícita, si funcionalmente se aprueba. | Facilita mantenimiento administrativo sin hacerlo disponible. |
| Desactivar Contacto | Permitido aun bajo padre inactivo; conservar registro. | Exclusión operacional sin borrar relaciones. |
| Responsable activo bajo padre inactivo | No ofrecer para nuevas asignaciones, tampoco por API enviando solo responsableId. | Cierra la brecha actual; no revoca permisos de roles. |
| Referencias históricas | Preservar asignaciones y permitir lectura según permisos actuales. | No exigir vigencia actual para leer pasado. Decidir si mostrar datos actuales o snapshots; snapshots implican otro alcance. |
| Departamento utilizado | Permitir desactivar con aviso de impacto, sin mover asignaciones. | No hay RN equivalente a última solución que justifique un bloqueo. Debe aprobarse explícitamente. |
| Reasignar Contacto/Responsable | No permitir traslados en este MVP. | El padre del Contacto es identidad contextual y el Responsable tiene referencias adicionales. |
| Auditoría | Definir actor/fecha/motivo y retención antes de añadir campos/tablas. | El modelo actual no contiene auditoría de organización; version no la sustituye. |

Separar estado propio de disponibilidad:

```text
Contacto disponible = Departamento.activo AND Contacto.activo
Responsable disponible = Departamento propietario.activo AND Responsable.activo
```

Un hijo activo bajo padre inactivo no es una violación del modelo propuesto: es un registro activo temporalmente no disponible. La restricción es sobre ofrecerlo o crear/reactivar bajo ese padre, no sobre borrar o cambiar masivamente sus flags.

Los PATCH deben preservar datos no relacionados. Los PUT de nombre/tipo/valor no admiten estado ni departamentoId; rechazar campos ajenos localmente al DTO, siguiendo el patrón de QA-009, sin cambiar configuración global JSON.

### Asignaciones y búsquedas

Validar Departamento activo cuando se informa departamentoId. Cuando solo llega responsableId, resolver su Departamento y comprobar también que esté activo. Si llegan ambos, mantener validación de pertenencia. Conservar RN-16 de QA-009: soluciones inactivas no reciben cambios de asignaciones.

Las lecturas históricas conservan referencias aunque sus entidades estén inactivas. La elegibilidad de futuras asignaciones no se aplica retroactivamente al historial.

Desactivar/renombrar Departamento puede afectar el índice de conocimientos referenciados por Departamento o por cualquiera de sus Responsables, incluyendo asignaciones que solo guardan responsableId. Contactos no participan directamente en el índice actual: no prometer reindexación de un dato que no se indexa. Si se aprueba la regla del padre activo, aplicar también la misma disponibilidad a Responsable en el índice operacional.

## 4. Actualizaciones concurrentes

### Recomendación

- Bloqueo pesimista del Departamento padre para comandos de hijos y transiciones de estado; bloquear padre antes de Contacto, con orden estable.
- Control optimista @Version independiente en Departamento y Contacto para rechazar ediciones obsoletas, no solo serializar transacciones.
- DTO devuelve version; PUT/PATCH exige versión leída por el cliente. Compararla dentro de la transacción y confiar además en @Version al hacer flush. Conflicto → 409 y UI conserva el formulario para recargar/comparar.
- No confiar en un conteo de duplicados con bloqueo de filas existentes: un catálogo vacío no aporta una fila de exclusión. Para unicidad concurrente, recomendación de índice UNIQUE físico alineado con la regla/normalización aprobada; traducir conflicto a 409.
- No incrementar artificialmente versión si el comando no cambia nada y su versión sigue vigente. Una repetición con versión antigua puede responder 409 por la precondición; no produce cambios adicionales.

Añadir @Version requiere columnas nuevas y autorización SQL separada. Un ETag/hash de los campos actuales podría detectar diferencias de contenido sin columna, pero no detecta todos los ciclos de cambio y retorno al mismo contenido. Un bloqueo por sí solo tampoco impide que un PUT obsoleto sobrescriba datos después de esperar. Si no se aprueban versiones/índices, documentar explícitamente la garantía reducida; no afirmar que todas las carreras están resueltas.

### Coordinación con conocimientos (QA-009)

Los comandos actuales de asignación bloquean primero el conocimiento. No introducir el orden inverso Departamento → Conocimiento, que puede generar deadlocks al reindexar o validar FK.

Diseño a verificar mediante tests físicos:

1. Identificar conocimientos afectados y bloquearlos en orden de ID, antes del Departamento cuando la operación necesita coordinar ambas áreas.
2. Bloquear Departamento y luego Contactos/Responsables necesarios.
3. Releer referencias con lectura vigente: si apareció una referencia adicional que exige otro bloqueo, hacer rollback y reintento acotado de la operación, no adquirir nuevos locks en orden inverso.
4. Validar versión/disponibilidad, persistir y actualizar índice con el protocolo acordado.
5. Una nueva asignación concurrente debe volver a comprobar disponibilidad bajo el bloqueo de padre antes de guardar.

Para catálogos grandes, evaluar coste de bloquear muchos conocimientos y una estrategia de reindexación posterior con recuperación explícita; no introducir silenciosamente asincronía que deje búsqueda obsoleta sin seguimiento. El protocolo definitivo se aprueba con evidencia de MySQL/InnoDB, no solo mocks.

## 5. Cambios Backend previstos

Raíz: C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend.

Bajo src/main/java/cl/casol/backend/:

Existentes a ampliar:

- identidad/domain/Departamento.java y DepartamentoContacto.java: contratos administrativos/version, si aprobado, manteniendo lecturas actuales.
- identidad/infrastructure/persistence/entity/DepartamentoEntity.java y DepartamentoContactoEntity.java: construcción/edición controlada; @Version solo con migración autorizada.
- identidad/application/port/out/DepartamentoRepository.java y DepartamentoContactoRepository.java: lectura de todos, detalle por ID, guardar y bloqueo; sin exponer JPA.
- identidad/infrastructure/persistence/adapter/DepartamentoRepositoryAdapter.java y DepartamentoContactoRepositoryAdapter.java: implementación de puertos, validación de pertenencia vía servicio y flush.
- identidad/infrastructure/persistence/repository/DepartamentoJpaRepository.java y DepartamentoContactoJpaRepository.java: consultas administrativas, duplicados y locks.
- identidad/infrastructure/persistence/mapper/OrganizacionMapper.java: datos administrativos sin perder compatibilidad operacional.
- conocimiento/application/service/GestionarAsignacionSolucionService.java: disponibilidad del padre de Responsable y coordinación de locks, si se aprueba.
- conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java: regla de disponibilidad aprobada en contenido de búsqueda.
- shared/infrastructure/web/GlobalExceptionHandler.java: errores claros de validación, duplicados, versión y estados.

Nuevos propuestos:

- identidad/application/service/AdministrarDepartamentosService.java
- identidad/application/service/AdministrarContactosDepartamentoService.java
- identidad/infrastructure/web/AdminDepartamentoController.java
- identidad/infrastructure/web/AdminDepartamentoContactoController.java
- identidad/infrastructure/web/dto/GuardarDepartamentoRequest.java
- identidad/infrastructure/web/dto/GuardarDepartamentoContactoRequest.java
- identidad/infrastructure/web/dto/CambiarEstadoDepartamentoRequest.java
- identidad/infrastructure/web/dto/CambiarEstadoContactoRequest.java
- identidad/infrastructure/web/dto/DepartamentoAdminResponse.java
- identidad/infrastructure/web/dto/DepartamentoContactoAdminResponse.java
- Excepciones de dominio específicas de duplicado, estado y conflicto de edición cuando no exista una reutilizable apropiada.

No duplicar DepartamentoController/ConsultarOrganizacionService operacionales, ni ampliar TipoCatalogo de Hardware/Sistemas/Módulos por conveniencia. Organización permanece en identidad, respetando Controller → Application Service → Port → Persistence Adapter. Reutilizar la política existente /api/admin/**; no cambiar autenticación ni permisos operacionales.

## 6. Migración Angular y datos locales

Raíz: C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug.

### Inventario de operaciones/almacenamiento actual

| Operación | Dato local | Sustitución propuesta |
|---|---|---|
| DepartamentosComponent.confirmarEstado → marcarDepartamentoActivo | Estado en memoria y dimabug.departamentos.detalle.v1, sessionStorage/localStorage. | PATCH administrativo; cambiar UI solo tras éxito y recargar. |
| confirmarEliminar → ocultarDepartamento | IDs en dimabug.departamentos.ocultos.v1 (sessionStorage), filtrado local y caché detalle. | Retirar Eliminar; ofrecer Desactivar persistente solo a administrador. |
| departamentosEnSesion/readDepartamentosSession/writeDepartamentosSession | Copia de listado, estado reconstruido y datos hijos. | Estado autoritativo HTTP; caché solo de lectura si se diseña con invalidación y nunca como fuente de estado. |
| listarDepartamentosDetalle | shareReplay, datos stale, filtrado ocultos y enriquecimiento parcial. | Separar listado operacional/admin; no convertir errores de hijos en vacío sin indicar fallo. |
| responsablesDeDepartamento | responsablesCache en memoria. | Invalidar tras comandos organizacionales y volver a consultar disponibilidad; no usar caché para validar Backend. |
| CatalogoService.departamentos/guardarDepartamento/eliminarDepartamento | Colección antigua en localStorage dimabug.catalogos.v2, IDs generados con nextId y datos seed. | No usar para el CRUD nuevo; retirar/deprecar solo rama de Departamentos tras comprobar consumidores. |

No se localizaron consumidores actuales de guardarDepartamento/eliminarDepartamento/departamentos() de CatalogoService fuera del propio servicio. Es código heredado, no otro CRUD activo. dimabug.catalogos.v2 también contiene otras colecciones; NO borrar toda esa clave ni modificar Pruebas/Errores/Soluciones en esta tarea.

### Política de datos locales existentes

- Nunca importar IDs ocultos, flags locales o registros seed a MySQL automáticamente.
- No emparejar IDs locales con IDs MySQL: pueden colisionar y representar otra entidad.
- Primero consultar servidor y usar sus IDs/estados. Ignorar las antiguas preferencias en el nuevo flujo.
- Si se requiere conservar información local aportada por usuarios, ofrecer exportación local opcional y revisión manual explícita; no prometer sincronización automática ni modificar BD.
- Una vez verificado el cambio, limpiar únicamente dimabug.departamentos.detalle.v1 de ambos stores y dimabug.departamentos.ocultos.v1 donde exista. Nunca limpiar todo localStorage/sessionStorage.
- En dimabug.catalogos.v2, deshabilitar consumo de la rama Departamentos o migrar solo esa propiedad conservando el resto; hacerlo tras aprobación de la política de datos locales. No tocarla durante el diseño.
- Mostrar una explicación breve si procede: los estados locales anteriores no eran cambios guardados en el sistema. No presentar una carga fallida como catálogo vacío.

### Archivos y experiencia prevista

- Reutilizar features/departamentos/departamentos.component.ts/.html/.css para presentación, con administración diferenciada por rol.
- Añadir core/services/admin-organizacion-api.service.ts y modelos administrativos con id, departamentoId, tipo, valor, activo, disponibilidad y versión.
- Ampliar core/models/catalogo.model.ts o crear modelos específicos de organización sin cambiar contratos de otras pantallas.
- Adaptar core/services/organizacion.service.ts preservando selectores operacionales; retirar métodos de escritura local de Departamentos y su filtro de ocultación.
- Reutilizar modal/estilos base y patrones de formularios existentes; conservar IDs de Contactos y Responsables, no reducirlos a strings como modelo de edición.
- Si se añade ruta administrativa, reutilizar pantalla con modo administrativo y adminGuard; mantener /departamentos operacional/staffGuard. No duplicar componentes CRUD.
- Añadir acceso desde panel Administrador y controles solo ADMINISTRADOR. El técnico conserva lectura/selección.
- En Conocimiento, actualizar selectores después de cambios y mostrar referencias históricas separadas de las opciones elegibles, sin hacerlas seleccionables como nuevas asignaciones.
- Actualizar catálogo y detalle después del éxito HTTP. Ante 409 de versión, no sobrescribir ni cerrar formulario: explicar conflicto y permitir revisar datos actuales.

No modificar AuthService para conceder permisos o interpretar estados. La limpieza de claves de organización pertenece al flujo de migración/caché específico, no a un borrado global de sesión.

## 7. Necesidad de migraciones SQL

El CRUD básico puede reutilizar campos de nombre/tipo/valor/estado ya mapeados: no necesita N:M, descripción ni nuevas tablas por sí mismo. Esto está condicionado a que el esquema real coincida con JPA.

La recomendación completa de concurrencia puede requerir:

- Columna de versión en Departamento y Contacto para @Version, inicializada de forma aditiva para registros existentes.
- Índices UNIQUE de nombres y Contactos según la normalización/collation aprobada.
- FK o restricciones de nulabilidad solo si falta protección física y se justifica tras inspección.

No se generan ni ejecutan migraciones en esta etapa. Antes de cualquier propuesta SQL: revisar SHOW CREATE TABLE de las tablas y asignaciones, duplicados bajo la regla elegida, huérfanos, longitud real y collation. No corregir, fusionar o borrar datos automáticamente para hacer pasar una restricción. Los duplicados históricos pueden bloquear un índice; requieren decisión y plan de saneamiento autorizado por separado.

No introducir Flyway/Liquibase o dependencias. Respaldo y aplicación SQL requieren autorización independiente. La migración pendiente QA-009 sigue siendo un prerrequisito del futuro despliegue consolidado.

## 8. Riesgos

- Estado local confundido con persistencia y preferencias compartidas entre cuentas del navegador.
- Unicidad de aplicación y MySQL con reglas distintas, o carrera de alta en tabla vacía.
- Nuevas asignaciones a Responsable de un Departamento inactivo si solo se valida Responsable.
- Actualizaciones obsoletas, inversión de locks y búsquedas desactualizadas.
- Reactivar padre hace disponibles hijos que conservaban flag activo: avisar, no reactivar masivamente hijos inactivos.
- Cambios de nombres/valores se reflejan en referencias históricas sin snapshots. Decidir necesidad de auditoría/versionado histórico separadamente.
- Alterar otras ramas de CatalogoService u otras cachés al retirar organización local.
- Ausencia de FK física confirmada para los IDs escalares de hijos.
- Ampliar CRUD de Responsables sin resolver identidad y traslados puede dañar coherencia de asignaciones.

## 9. Plan de pruebas

| Nivel | Casos |
|---|---|
| Unitarias dominio/aplicación | Nombre vacío/límites/normalización; duplicados activos e inactivos; edición propia; padre inexistente; pertenencia de Contacto; defaults; estado propio/disponibilidad; reactiva padre sin alterar flags de hijos; versiones obsoletas. |
| Integración REST | GET administrativos incluye inactivos; GET operacionales conserva alcance; POST/PUT/PATCH y códigos 200/201/400/404/409; Location; campos exclusivos rechazados; no DELETE. |
| Seguridad | ADMINISTRADOR administra; TECNICO/SOPORTE/USUARIO no escriben ni acceden al listado administrativo; roles actuales conservan lecturas operacionales; 401 sin token; acceso por ID cruzado no permitido. |
| Persistencia MySQL | ID/relaciones/estado tras nueva sesión; FK y unicidad físicas; versiones; rollback; ningún borrado; índices de búsqueda actualizados. Solo entorno aislado autorizado. |
| Historial | Asignaciones previas conservadas tras desactivar padre/hijo; lectura según permisos actuales; no modificar referencias; contacto de Responsable independiente; nuevas asignaciones rechazadas según disponibilidad aprobada. |
| Concurrencia | Altas duplicadas simultáneas, incluida tabla vacía; dos PUT con misma versión (uno gana/otro 409); desactivar padre frente a activar/crear Contacto o asignar Responsable; orden de locks y reintentos acotados; cambio de nombre frente a cambio de estado sin pérdida accidental. |
| Angular | Métodos HTTP reales, roles, identificadores estables, actualiza después de éxito; errores parciales visibles; 409 conserva formulario; limpiar solo claves propias; preferencias antiguas ignoradas; datos locales no importados; otras ramas intactas. |
| UAT | Dos sesiones/navegadores; crear/editar/desactivar/reactivar; padre/hijos; inactivos visibles en administración y no ofrecidos operacionalmente; historial; filtros; fallo de red; móvil/teclado; comparación de cambios concurrentes. |

Ejecutar después de implementar la regresión completa de QA-009/QA-010/QA-011 y compilación. No se ejecutaron pruebas en este diseño; los tests con mocks no sustituyen MySQL/InnoDB real.

## 10. Etapas y aprobación

1. Aprobar reglas funcionales de la tabla de decisiones, el alcance de Responsables y la política de datos locales.
2. Confirmar esquema físico en entorno autorizado de lectura y decidir versiones/índices. Si se requieren SQL, preparar propuesta aditiva y pruebas aisladas; no ejecutarla sin autorización.
3. Implementar Backend administrativo y pruebas, preservando contratos operacionales y RN-16. Validar protocolo de concurrencia.
4. Integrar Angular por HTTP, mantener lectura técnica y retirar preferencias locales solo en organización. No importar registros sin revisión.
5. Ejecutar regresión/UAT y comprobar persistencia/concurrencia reales donde se autorice.
6. Preparar despliegue consolidado: esquema aprobado antes del Backend que lo requiera, luego Angular, con rollback que no restaure acciones locales ni elimine datos. No desplegar automáticamente.

**Estado: diseño preparado. Todas las rutas y reglas nuevas son propuestas, no implementación. Esperar autorización antes de programar.**
