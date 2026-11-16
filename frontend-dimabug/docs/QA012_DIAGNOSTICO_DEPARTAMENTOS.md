# QA-012 — Diagnóstico de Departamentos, Contactos y Responsables

Fecha: 2026-10-13, America/Santiago. DimaBug, estabilización local del MVP.

## Resumen ejecutivo

El Backend implementa consultas de Departamentos activos y de sus Contactos/Responsables activos. No se encontraron casos de uso ni endpoints para crear, editar, desactivar o reactivar ninguna de estas tres entidades. No existe CRUD administrativo persistente de organización.

Angular muestra Departamentos y un detalle con Contactos/Responsables. Sus botones Activar/Desactivar y Eliminar no llaman al Backend: modifican caché/almacenamiento del navegador. El estado local puede sobrevivir a cerrar sesión porque se guarda también en localStorage y no es un estado persistente compartido del sistema. Las listas operacionales usadas para asignar soluciones siguen consultando el estado real del Backend.

El modelo no es N:M: cada Contacto y cada Responsable tiene un único departamentoId obligatorio. Un Departamento admite múltiples filas de Contactos y Responsables. Un mismo valor de contacto o persona podría repetirse en filas diferentes, pero eso no comparte la misma entidad entre Departamentos.

Prioridad: resolver acciones engañosas y diseñar administración persistente con autorización Backend, sin duplicar las consultas operacionales ni cambiar permisos actuales. La política de estados e impacto histórico debe aprobarse antes de implementar.

Solo se creó este informe. No se modificó código, no se ejecutaron pruebas, compilaciones, migraciones ni consultas de BD. QA-009/QA-010/QA-011 se preservaron. No hubo commits u operaciones destructivas, acceso a STAGING/PRODUCCIÓN ni avance a otros módulos.

## 1. Funcionalidades implementadas y ausentes

| Funcionalidad | Backend | Angular |
|---|---|---|
| Listar Departamentos activos | Implementado. | Implementado con caché y enriquecimiento progresivo. |
| Consultar Contactos activos del Departamento | Implementado, valida padre activo. | Implementado, mostrado como chips/detalle. |
| Consultar Responsables activos del Departamento | Implementado, valida padre activo. | Implementado en listado/detalle y selector de asignaciones. |
| Consultar Departamentos inactivos administrativamente | No encontrado. | No existe listado fiable de inactivos; estado reconstruido localmente. |
| Crear/editar Departamento | No encontrado. | No hay formularios de alta/edición. |
| Desactivar/reactivar Departamento | No encontrado. | Acción local, sin petición HTTP. |
| CRUD/estado de Contactos | No encontrado. | Solo lectura; sin formulario. |
| CRUD/estado de Responsables | No encontrado. | Solo lectura/selección; sin administración. |
| Eliminar Departamento | No existe caso de uso ni endpoint físico. | Oculta localmente, presentado como Eliminar. |
| Búsqueda/filtros | Sin endpoint específico para ello. | No hay controles de búsqueda o filtros en Departamentos. |
| Asignar Departamento/Responsable a solución | GET/POST/PUT de asignaciones existentes. | Modal en Conocimiento; no crea ni edita la entidad organizacional. |

La herencia de save/delete desde JpaRepository no equivale a casos de uso CRUD implementados. Los puertos de organización inspeccionados solo exponen lecturas.

## 2. Entidades, tablas y relaciones reales

| Entidad/tabla | Campos mapeados relevantes | Restricciones conocidas por JPA |
|---|---|---|
| DepartamentoEntity / u_departamento | departamento_id, departamento_nombre, departamento_estado | PK IDENTITY; nombre/estado no nulos; sin unicidad explícita. |
| DepartamentoContactoEntity / u_departamento_contacto | contacto_id, departamento_id, contacto_tipo, contacto_valor, contacto_estado | PK IDENTITY; departamentoId/tipo/valor/estado no nulos; tipo 50, valor 100. |
| ResponsableEntity / u_responsable | responsable_id, departamento_id, responsable_nombre, responsable_cargo, responsable_contacto, responsable_estado | PK IDENTITY; departamentoId/nombre/estado no nulos; cargo/contacto opcionales. |

Contactos y Responsables mapean departamento_id como Integer, no como @ManyToOne. Es una relación de pertenencia por ID utilizada por las consultas; no demuestra una FK física generada por JPA. No hay tablas de unión para compartir Contactos o Responsables entre Departamentos.

No hay restricciones @UniqueConstraint en estas entidades ni validaciones de duplicados en servicios de escritura, porque esos servicios no existen. Los límites no declarados explícitamente utilizan valores por defecto de JPA; no se confirmó el esquema físico ni su collation.

u_responsable.responsable_contacto es un texto propio, separado de las filas de u_departamento_contacto: no hay enlace mapeado entre ambos. No se debe asumir que modificar uno actualiza el otro.

co_solucion_asignacion tiene referencias JPA ManyToOne opcionales a Departamento y Responsable y una referencia obligatoria a Solución. Las asignaciones relacionan indirectamente estas entidades con conocimientos/derivaciones; no se encontró un departamentoId directo en ConocimientoEntity.

Las consultas devuelven varios Contactos y Responsables por Departamento. Un mismo Responsable puede ser usado en varias asignaciones de soluciones, sin que eso lo vincule como entidad a múltiples Departamentos.

### Base de datos: límite de evidencia

Solo están disponibles las migraciones del estado ELIMINADO de conocimientos y la ampliación de soluciones QA-009. No hay DDL base de las tres tablas de organización.

No se confirmó físicamente PK, FK, unicidad, tipos, defaults ni ON DELETE en MySQL. Consultas de lectura para una revisión futura autorizada, no ejecutadas:

```sql
SHOW CREATE TABLE u_departamento;
SHOW CREATE TABLE u_departamento_contacto;
SHOW CREATE TABLE u_responsable;
SHOW CREATE TABLE co_solucion_asignacion;
```

No se justifica proponer una migración a N:M o nuevas tablas a partir de este diagnóstico.

## 3. Endpoints y permisos

| Método/ruta existente | Resultado |
|---|---|
| GET /api/departamentos | Departamentos activos ordenados por nombre; 200. |
| GET /api/departamentos/{id}/contactos | Contactos activos ordenados por ID; 200; padre inexistente/inactivo → 404. |
| GET /api/departamentos/{id}/responsables | Responsables activos ordenados por nombre; 200; padre inexistente/inactivo → 404. |

No se encontró GET /api/departamentos/{id} de detalle general, POST, PUT, PATCH de estado ni DELETE. Tampoco endpoints de escritura independientes de Contactos o Responsables.

DTO actuales son operacionales:

- DepartamentoResponse: id, nombre.
- DepartamentoContactoResponse: id, tipo, valor.
- ResponsableResponse: id, nombre, cargo, contacto.

No incluyen estado ni departamentoId de los hijos porque el padre va en la URL y las consultas filtran activos. No sirven por sí solos para administrar históricos/inactivos.

SecurityConfig concede consultas de /api/departamentos/** a ADMINISTRADOR, TECNICO y SOPORTE; sin JWT → 401, roles no autorizados → 403. USUARIO no está en esa regla de organización. La ruta Angular /departamentos usa staffGuard y está en Catálogos para staff.

El prefijo /api/admin/** ya está restringido a ADMINISTRADOR, pero no existe actualmente administración de Departamentos bajo ese prefijo. El panel Administrador declara Departamentos/Contactos/Responsables pendientes, y el enum/API genérico de catálogos solo contempla Hardware, Sistemas, Módulos y Frecuencias.

Los controles locales de activar/eliminar no están limitados adicionalmente por isAdmin: los técnicos que acceden a la pantalla también pueden ejecutar esas acciones locales. Eso no cambia datos Backend, pero comunica una capacidad administrativa inexistente.

## 4. Estados y reglas de negocio actuales

### Organización

- Un Departamento puede tener varios Contactos y Responsables según los repositorios.
- Cada Contacto/Responsable tiene un solo departamentoId. Compartir el mismo ID entre Departamentos no está modelado.
- Consultas operacionales filtran cada entidad activa y exigen Departamento activo antes de consultar hijos.
- Contactos inactivos no llegan al endpoint operacional. Angular no puede administrarlos ni reactivarlos mediante ese contrato.
- Un Departamento inactivo deja de aparecer en la lista; sus endpoints de hijos responden 404. Esto no demuestra que sus hijos se hayan desactivado o eliminado.
- No hay servicio de desactivación real que defina cascada, preservación de estados de hijos o bloqueo por uso. No asumir ninguna de esas reglas.

### Asignaciones de soluciones

GestionarAsignacionSolucionService exige al menos Departamento o Responsable. Si se informan ambos, el Responsable debe pertenecer al Departamento. Las referencias seleccionadas deben estar activas. QA-009 impide crear/modificar asignaciones sobre una solución inactiva, conservando las lecturas históricas.

Existe una brecha de coherencia a evaluar: si se envía SOLO responsableId, el servicio comprueba que el Responsable esté activo, pero no consulta el estado de su Departamento propietario. Un Responsable activo bajo Departamento inactivo podría por tanto asignarse a través de API. No se probó esa combinación en runtime; se identifica por la rama del código. Corregirla requiere aprobar la regla, sin retirar los permisos operacionales existentes.

Leer asignaciones existentes no exige que Departamento/Responsable sigan activos. El mapper conserva IDs y datos de las referencias actuales; no existe evidencia de snapshot/versionado de nombres y contactos. Un cambio futuro de esos datos puede modificar su presentación en históricos.

El índice de búsqueda incorpora nombre de Departamento solo si está activo y nombre/cargo de Responsable si este está activo. Para un Responsable no comprueba también el estado de su Departamento. Una futura desactivación necesitará reindexar los conocimientos afectados; actualmente no hay comando organizacional que dispare esa actualización.

## 5. Frontend: comportamiento y problemas

### Acceso y presentación

Departamentos aparece en Catálogos del menú lateral y abre /departamentos. Comparte catalogo-page.css con Hardware y otros catálogos, pero utiliza DepartamentosComponent, no el CRUD administrativo de Sistemas/Módulos.

Hay listado con nombre, Contactos, Responsables y estado, modal de detalle, confirmaciones de estado y eliminación. No hay formularios de crear/editar, gestión de Contactos/Responsables ni filtros/búsqueda. El cierre del modal de detalle usa símbolo correcto.

### Acciones locales que deben corregirse

- confirmarEstado llama a marcarDepartamentoActivo, modifica la lista en memoria y guarda dimabug.departamentos.detalle.v1 en sessionStorage y localStorage. No hay PATCH HTTP.
- confirmarEliminar llama a ocultarDepartamento, escribe dimabug.departamentos.ocultos.v1 en sessionStorage y filtra la lista. No hay DELETE HTTP ni desactivación persistente.
- El DTO DepartamentoResponse no incluye estado; Angular reconstruye activo desde la caché o asume true, aunque el listado base ya filtra por activos.
- Los selectores de asignación usan listarDepartamentos/responsablesDeDepartamento, no los indicadores de estado/ocultación locales. Un Departamento desactivado visualmente puede seguir ofreciéndose para una solución.
- Las claves de organización no están segmentadas por usuario. AuthService.logout elimina la sesión de autenticación, no esta caché de Departamentos. Puede haber inconsistencias entre cuentas en el mismo navegador, además de entre navegadores.

### Datos de Contactos/Responsables

Angular descarta el ID del Contacto y transforma valor en numero, incluso si se trata de correo u otro tipo. El listado reduce Responsables a strings de nombre; pierde ID/cargo/contacto en esa representación. Es insuficiente para un CRUD y para distinguir homónimos.

Los bucles usan track c.numero y track r. Si hay valores o nombres repetidos dentro del mismo Departamento, pueden colisionar claves DOM. No hay unicidad confirmada que evite esos casos; riesgo de UI, no fallo reproducido en esta etapa.

Aunque el detalle contempla etiquetas de Contacto inactivo, el endpoint operacional solo devuelve activos. Mostrar algún inactivo desde caché no sustituye una consulta administrativa fiable.

### Carga y errores

El enriquecimiento captura fallos de Contactos/Responsables y los transforma en listas vacías. Puede mostrar Sin contactos/Sin responsables cuando falló la petición. Con caché previa, el servicio puede reutilizar hijos sin consultarlos de nuevo y devolver datos antiguos si falla la carga base.

En la edición de asignaciones, responsablesDeDepartamento se carga para el Departamento elegido y el selector muestra solo nombre. Cambiar Departamento reinicia Responsable. Hay manejo de error durante el cambio normal, pero la carga al abrir una asignación existente no proporciona un callback error equivalente. Referencias inactivas históricas pueden no estar entre las opciones del catálogo activo.

Los mensajes de confirmación afirman cambios de estado o eliminación pese a no haber persistencia. Esto es el problema de mayor prioridad, por encima de los ajustes visuales.

## 6. Riesgos de integridad y decisiones necesarias

1. Distinguir desactivación de ocultamiento de navegador; no convertir preferencias locales en cambios persistentes autorizados.
2. Confirmar FK físicas de departamentoId en hijos; el mapeo scalar no las garantiza.
3. Definir duplicados: nombre global de Departamento; Contacto por Departamento/tipo/valor; identidad del Responsable. No introducir reglas sin aprobación.
4. Aprobar política de Departamento inactivo: recomendable conservar hijos y sus estados, impedir nuevas selecciones y permitir reactivación sin alteración masiva automática. Es propuesta, no regla implementada.
5. Decidir qué hacer con Responsable activo cuyo Departamento está inactivo, en altas y edición de asignaciones.
6. Definir si Contactos inactivos solo dejan de ofrecerse operacionalmente o también afectan datos mostrados en derivaciones; responsable_contacto y Contactos de Departamento son independientes.
7. Impedir que trasladar un Responsable cambie la coherencia de asignaciones históricas que guardan también Departamento. El CRUD aún no existe; no hay protección implementada de traslado.
8. Preservar registros y asignaciones al desactivar; sin eliminación física ni cascadas no evaluadas. JpaRepository.delete heredado no está expuesto como caso de uso.
9. Diseñar actualización del índice y cachés tras comandos persistentes; considerar ediciones concurrentes y estados de padre/hijos.
10. Los campos de organización no incluyen auditoría de modificación en el modelo revisado. Definir trazabilidad requerida antes de ampliar administración.

## 7. Pruebas existentes y faltantes

| Área | Existentes inspeccionadas | Faltantes recomendadas |
|---|---|---|
| Consulta organizacional | ConsultarOrganizacionServiceTest: padre activo, vacío, inexistente/inactivo, Contactos. | Lista Departamentos, Responsables positivos, exclusión de hijos inactivos en persistencia real. |
| Contactos/adaptador | DepartamentoContactoRepositoryAdapterTest verifica consulta activa y mapeo. | IDs/estados y restricciones físicas; múltiples Contactos/homónimos/valores repetidos. |
| Seguridad GET | CatalogosOrganizacionSecurityIntegrationTest: admin/técnico, 401/403 y 404. | USUARIO explícito y futura administración ADMINISTRADOR frente a TECNICO. |
| Asignaciones | GestionarAsignacionSolucionServiceTest y SolucionSecurityIntegrationTest: destinos activos, ambos nulos, no pertenencia, edición, RN-16 e históricos de soluciones inactivas. | Responsable activo con Departamento inactivo; preservación tras desactivar organización; traslado y referencias históricas. |
| CRUD organizacional | No hay CRUD; no existen pruebas de comandos correspondientes. | Alta/edición/estado, defaults, campos requeridos, duplicados y persistencia de las tres entidades una vez aprobados. |
| Angular organización | No se localizaron specs específicas de Departamentos/OrganizacionService. | Peticiones reales, ausencia de ocultamiento local, roles, errores parciales, cachés, selección histórica, filtros y refresco tras éxito. |
| Integración/UAT | No ejecutadas en diagnóstico. | Persistencia entre sesiones, árbol padre/hijos, histórico, móvil/teclado y concurrencia en MySQL aislado autorizado. |

No se ejecutaron suites durante este diagnóstico. Los resultados de QA-011 anteriores no se presentan como una ejecución nueva.

## 8. Archivos involucrados

Raíz Backend: C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend.

Bajo src/main/java/cl/casol/backend/:

- identidad/domain/Departamento.java, DepartamentoContacto.java y Responsable.java
- identidad/infrastructure/persistence/entity/DepartamentoEntity.java, DepartamentoContactoEntity.java y ResponsableEntity.java
- identidad/infrastructure/persistence/repository/DepartamentoJpaRepository.java, DepartamentoContactoJpaRepository.java y ResponsableJpaRepository.java
- identidad/infrastructure/persistence/adapter/DepartamentoRepositoryAdapter.java, DepartamentoContactoRepositoryAdapter.java y ResponsableRepositoryAdapter.java
- identidad/infrastructure/persistence/mapper/OrganizacionMapper.java
- identidad/application/port/out/DepartamentoRepository.java, DepartamentoContactoRepository.java y ResponsableRepository.java
- identidad/application/service/ConsultarOrganizacionService.java
- identidad/infrastructure/web/DepartamentoController.java
- identidad/infrastructure/web/dto/DepartamentoResponse.java, DepartamentoContactoResponse.java y ResponsableResponse.java
- conocimiento/application/service/GestionarAsignacionSolucionService.java
- conocimiento/infrastructure/web/SolucionAsignacionController.java
- conocimiento/infrastructure/persistence/entity/SolucionAsignacionEntity.java
- conocimiento/infrastructure/persistence/mapper/SolucionMapper.java
- conocimiento/infrastructure/persistence/adapter/BusquedaConocimientoJdbcAdapter.java
- shared/infrastructure/security/SecurityConfig.java

Pruebas Backend, bajo src/test/java/cl/casol/backend/:

- CatalogosOrganizacionSecurityIntegrationTest.java
- identidad/application/service/ConsultarOrganizacionServiceTest.java
- identidad/infrastructure/persistence/adapter/DepartamentoContactoRepositoryAdapterTest.java
- conocimiento/application/service/GestionarAsignacionSolucionServiceTest.java
- SolucionSecurityIntegrationTest.java

Raíz Angular: C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug.

- src/app/features/departamentos/departamentos.component.ts, .html y .css
- src/app/core/services/organizacion.service.ts
- src/app/core/models/catalogo.model.ts y conocimiento.model.ts
- src/app/features/conocimiento/conocimiento-detalle.component.ts y .html
- src/app/core/services/conocimiento.service.ts (asignaciones)
- src/app/features/admin/admin.component.html
- src/app/core/services/admin-catalogos-api.service.ts
- src/app/shared/layout/shell-layout.component.html
- src/app/shared/ui/catalogo-page.css
- src/app/app.routes.ts
- src/app/core/guards/auth.guard.ts
- src/app/core/services/auth.service.ts (solo revisión de limpieza de sesión, no cambios propuestos de autenticación)

## 9. Propuesta de correcciones priorizadas, pendiente de aprobación

### P0 — Coherencia funcional

Corregir o retirar las acciones engañosas de estado/eliminación local. No simular éxito. Distinguir falta de datos de fallo de carga. Preservar permisos de consulta y evitar que cachés antiguas definan el estado real.

### P1 — Administración persistente

Aprobar primero reglas de nombres, duplicados, estados padre/hijos, referencias y auditoría. Implementar en el módulo identidad, respetando Controller → Application Service → Port → Persistence Adapter. Reutilizar consultas operacionales actuales sin cambiar sus permisos.

Una superficie administrativa separada puede ofrecer GET (incluidos inactivos), POST, PUT y PATCH /estado de Departamentos, y recursos anidados de Contactos/Responsables bajo /api/admin/departamentos. No duplicar el CRUD de catálogos ni mezclar nuevos permisos de escritura con /api/departamentos operacional. Estas rutas son una propuesta: no están implementadas.

No requiere asumir nuevas columnas para estado: ya hay campos propios en el modelo. La necesidad de migración solo podrá decidirse tras verificar el esquema físico; no introducir dependencias ni ejecutar SQL en esta etapa.

### P2 — Angular administrativo

Reutilizar estilo y patrones existentes, incorporando modelos con IDs, estado y pertenencia. Crear/editar Departamentos y gestionar hijos en contexto. Cambiar Eliminar por desactivación persistente cuando exista el comando; mostrar éxito tras respuesta HTTP, mantener formulario al fallar y refrescar catálogo/selectores. Controles de escritura solo ADMINISTRADOR; Backend autoritativo.

Añadir búsqueda/filtros y avisos de impacto aprobados. No convertir Contactos o Responsables en N:M ni inferir eliminación en cascada.

### P3 — Integridad y validación

Probar reglas de padre inactivo, asignaciones históricas, traslado de Responsable, actualización del índice y cachés. Revisar MySQL en entorno aislado autorizado y hacer UAT. Mantener regresión completa QA-009/QA-010/QA-011.

No implementar bloqueo/desactivación automática de entidades relacionadas sin aprobación expresa. Detenerse en QA-012 y esperar autorización; no avanzar a otros módulos.
