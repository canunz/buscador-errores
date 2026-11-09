# Hardware opcional en procedimientos

## Cambio implementado

Los formularios compartidos de creación y edición muestran «Hardware asociado (opcional)» y «Sin hardware». Reutilizan el catálogo existente; no crean registros. Se retiraron los controles de sistema y módulo, conservando sus datos y compatibilidad en el backend.

La asociación se persiste mediante `pr_procedimiento.hardware_id`, nullable y con clave foránea hacia `cl_hardware`. Se valida existencia y un identificador positivo. Los cambios siguen sujetos a autorización administrativa y auditoría.

Para clientes anteriores, omitir `hardwareId` conserva el valor al editar; enviar `null` lo elimina. La edición desde el nuevo formulario conserva los datos legados de sistema y módulo. Un hardware heredado fuera del catálogo activo se conserva y aparece identificado por su ID.

Las revisiones copian el hardware de su versión de origen. Modificar o publicar una revisión no modifica la asociación de la versión anterior ni las ejecuciones que apuntan a ella.

## Migración y activación

Aplicar una sola vez `07_hardware_procedimientos.sql` con una cuenta administradora sobre `dima_bug_staging`, mediante `127.0.0.1:3307`. Añade columna nullable y FK; no actualiza registros ni elimina relaciones. Los procedimientos previos quedan sin hardware.

La cuenta de la aplicación no tiene ALTER. Confirmar la columna antes de iniciar el backend actualizado. No ejecutar en producción. Tras la confirmación del usuario se verificó el mapeo Hibernate contra staging, incluida la consulta de procedimientos con hardware; se reinició exclusivamente el backend en 9090, conservando el perfil staging y correo SIMULADO.

## Validación ejecutada

- Maven: 1.038 pruebas, cero fallos, errores u omisiones. Incluye 12 casos HTTP/JPA nuevos con base H2 aislada: creación con y sin hardware, lectura y edición, omisión frente a null, IDs inválidos, datos legados, hardware inactivo, revisión publicada y roles administrativos.
- Angular: 377 pruebas correctas. Incluye formulario, selector, recuperación de hardware y contrato HTTP.
- Build de Angular correcto. Persisten advertencias de presupuesto CSS en pantallas de inicio y conocimiento ajenas a este cambio.
- No se crearon procedimientos, ejecuciones ni correos de prueba en staging.
- Verificación posterior a la migración: mapeos Hibernate correctos y 96 filas de ejecución comprobadas; HTTP 200 en backend `/api/health`, Angular `http://localhost:4200` y `/api/health` a través del proxy. Angular escucha en `::1`, por lo que se verificó con `localhost`. No se realizó una edición manual autenticada en staging.

## Archivos principales

- Frontend: `procedimiento-form.component.ts`, su HTML y spec; `procedimiento.model.ts`; `procedimiento.service.ts`; `hardware-procedimiento.service.spec.ts`.
- Backend: `Procedimiento`, `ProcedimientoEntity`, `ProcedimientoMapper`, `ProcedimientoResponse`, `GuardarProcedimientoRequest`, `ProcedimientoController`, `EditarProcedimientoAdminService`, `MantenerProcedimientoService`, `PrepararVersionEvidenciaService`, `AuditoriaProcedimientoService`, `HardwareRepository` y su adaptador.
- Prueba de integración: `HardwareProcedimientoHttpTest`.
- SQL: `07_hardware_procedimientos.sql`.
