# Edición administrativa de procedimientos

## Uso
1. Un administrador abre /procedimientos/{id} y pulsa **Editar procedimiento**, junto a Iniciar procedimiento.
2. Si el procedimiento es un borrador sin ejecuciones, se edita directamente. Si está publicado, se prepara una revisión en borrador con sus mismos datos, modalidades, configuración y materiales. La nueva revisión tiene su propio ID; el original permanece intacto. Pulsar Editar otra vez retoma el borrador pendiente de la misma familia.
3. El formulario permite editar título, descripción, sistema, módulo, información complementaria y estado. El módulo debe pertenecer al sistema elegido y ambos deben estar activos.
4. **Administrar pasos y materiales** abre el detalle del borrador. Cada paso tiene Editar, Eliminar, Arriba y Abajo; agregar pasos y materiales reutiliza los modales existentes. Editar un paso incluye instrucciones, criticidad, modalidad, correos, evidencias y condiciones de aprobación.
5. Eliminar requiere confirmación. Reordenar conserva el paso seleccionado y valida que el orden esperado no haya cambiado en otra sesión.
6. Publicar requiere al menos un paso y marca la revisión anterior como no vigente para el listado general. Los enlaces históricos y las ejecuciones conservan su versión original. La revisión nueva muestra un enlace al procedimiento original.

## Protección del historial
La ejecución guarda referencias al procedimiento y pasos de su versión, además de los contratos ya existentes. No se cambian esas referencias. Las instrucciones, títulos, materiales y evidencias de la revisión publicada anterior permanecen disponibles incluso para ejecuciones legadas sin contrato de paso.

Los archivos locales de apoyo se copian a referencias independientes al preparar la revisión. Eliminar un archivo de la copia no elimina el original. Si falla la transacción de preparación, se compensan las copias nuevas. El procedimiento publicado no admite escritura directa en datos, pasos, materiales o configuración de automatización: se debe trabajar sobre el borrador.

Un borrador que ya tiene ejecuciones también está protegido y debe revisarse mediante una copia. No se eliminan procedimientos publicados ni ejecuciones. Al eliminar un paso del borrador se limpian sus referencias de material; sus bytes no se purgan automáticamente, para evitar pérdida accidental. La limpieza de archivos que ya no estén referenciados queda fuera de esta operación.

## Permisos
- Administrador: edición y auditoría.
- Técnico/soporte: consulta e inicio de ejecuciones, sin acciones de edición de procedimientos.
- La autorización se comprueba en Spring Security para POST/PUT/PATCH/DELETE del módulo, con una excepción explícita para POST /api/procedimientos/{id}/ejecuciones, que conserva el acceso de técnicos y soporte.
- Las rutas Angular de creación/edición usan adminGuard. El detalle mantiene Iniciar procedimiento para técnicos y muestra Editar procedimiento solo a administradores.
- Preparación de revisiones y auditoría verifican además el administrador activo en el servicio. No se aceptan IDs de actor enviados por el cliente.

## Auditoría
La tabla pr_auditoria_procedimiento registra procedimiento, usuario responsable, instante del servidor, operación y valores antes/después. La vista **Historial de cambios** es exclusiva de administradores. Las entradas son inmutables y se guardan dentro de la transacción de la modificación. Se auditan altas, datos, pasos, orden, materiales, automatización, preparación y publicación de las revisiones del editor.

La serialización de procedimiento usa una lista explícita de campos: no incluye objetos Usuario ni hashes de contraseñas. Se conservan también los registros de configuración de pasos anteriores. No se inventan entradas históricas para acciones anteriores a la incorporación de la auditoría.

## Persistencia
La migración 06_edicion_administrativa_procedimientos.sql añade datos_editoriales_json a pr_procedimiento y crea pr_auditoria_procedimiento. No actualiza filas existentes. Los registros anteriores se interpretan como revisión inicial vigente. El esquema fue aplicado por la usuaria y verificado mediante lecturas Hibernate de procedimientos, auditoría y entidades de ejecución.

La revisión editorial no exige alterar ejecuciones ni migrar fotos históricas. Se reutilizan pr_procedimiento, pr_paso, materiales, almacenamiento y servicios actuales. La preparación y publicación se serializan con bloqueo de la raíz de la familia y aislamiento READ_COMMITTED. La publicación de datos y el retiro de la versión anterior se confirman en la misma transacción.

## Archivos principales
Backend:
- DatosEditorialesProcedimiento, Procedimiento, ProcedimientoEntity/Mapper y DTO de procedimiento.
- EditarProcedimientoAdminService, AuditoriaProcedimientoService y AuditoriaProcedimientoEntity.
- MantenerProcedimientoService, MantenerPasoService, PasoRepository/Adapter y EdicionProcedimiento.
- ProcedimientoController, PasoController, MaterialPasoController, MaterialPasoArchivoController y ConfigurarPasoController/Service.
- MaterialPasoArchivoService, MantenerMaterialPasoService y SecurityConfig.

Frontend:
- procedimiento-detalle.component.ts/.html/.css.
- procedimiento-form.component.ts/.html/.css.
- procedimiento-lista.component.ts, procedimiento.service.ts, procedimiento.model.ts y app.routes.ts.

## Validación
- Backend: 1026 pruebas aprobadas; cero fallos y errores.
- Frontend: 369 pruebas aprobadas; compilación correcta.
- Prueba HTTP/JWT persistida EdicionProcedimientoAdminHttpTest: administrador edita/reordena/elimina/configura/publica; otros roles reciben 403; técnicos conservan el inicio de ejecuciones.
- Se comprobaron ejecuciones finalizadas, en curso y legadas sin contrato; sus instrucciones, criticidad y fotografías no cambian.
- Se descargó el PDF original después de eliminar la copia de la revisión para comprobar independencia de archivos.
- Dos solicitudes concurrentes de preparación devolvieron el mismo borrador.
- Publicar sin pasos revierte también los cambios generales; guardar y publicar conserva todos los campos.
- Auditoría con actor y fecha, sin hashes de contraseña.
- Pruebas de interfaz para visibilidad de botones por rol, controles accesibles, movimiento sin duplicados y confirmación de eliminación.
- Las pruebas usan H2 y almacenamiento aislados. No crearon revisiones ni ejecuciones en staging ni enviaron correos reales.

## Límites
Los borradores son compartidos por los administradores de la familia; el orden rechaza modificaciones concurrentes obsoletas. Los enlaces de versiones anteriores siguen consultando esa versión, mientras el listado general muestra las vigentes. No se fusionan ni eliminan borradores históricos creados por funciones anteriores.

La compilación conserva advertencias conocidas de presupuesto de bundle y CSS. Las pruebas de DOM y permisos no sustituyen una revisión visual completa o una certificación de accesibilidad.

## Disponibilidad local confirmada
Backend actualizado: PID 42244, puerto 9090, salud 200. Angular: PID 38276, puerto 4200, respuesta 200; salud del backend por proxy 200. El historial sin sesión devuelve 401. No se crearon revisiones ni ejecuciones de demostración en staging; las pruebas funcionales se hicieron en H2 aislada. La comprobación visual en la sesión real del administrador no se ha realizado por automatización.
