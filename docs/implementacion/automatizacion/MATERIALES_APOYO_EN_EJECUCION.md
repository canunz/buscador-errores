# Material de apoyo durante la ejecución

Fecha: 2026-11-08.

## Diagnóstico previo a los cambios

La vista de procedimiento consulta `ProcedimientoService.listarMateriales(procedimientoId,pasoId)` y pinta el resultado en su plantilla. La vista de ejecución no consultaba ese endpoint ni tenía una sección para materiales. El DTO de pasos de ejecución contiene los IDs de la definición original y el contrato de ejecución, pero no una lista de materiales. Los materiales se ofrecen por un endpoint separado ya existente. El problema era una omisión de integración en Angular, no ausencia de datos o fallo del backend.

Comprobación de solo lectura en staging: ejecución 33 EN_CURSO, procedimiento 15 «Configurar PDA», origen 2, revisión 2. El paso 5 «Instalar Mocha» es el paso de definición 78 y la instancia 190; está completado. El material 5 «Configuración Login», VIDEO, está asociado al paso 78 tanto en el procedimiento como en la ejecución. No se abrió ni descargó el recurso privado de Drive durante el diagnóstico.

## Solución

Se añadió un lector de materiales en el panel del paso seleccionado y en cada paso del resumen de ejecuciones terminadas/canceladas. No depende del cumplimiento del paso y no emite acciones de progreso.

Utiliza exclusivamente `ejecucion.procedimientoId` y `item.pasoId`, nunca la raíz ni la revisión vigente de la familia. El versionado existente clona pasos/materiales a IDs nuevos y conserva la versión publicada original. Por eso puede reutilizarse el endpoint existente sin migración ni nuevas tablas/snapshots.

Se reutilizan `ProcedimientoService.listarMateriales`, el endpoint GET `/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales`, `descargarMaterial`, el validador de descarga autenticada y los botones/estilos de DimaBug. La aplicación tenía un formulario compartido de edición de materiales, pero no un componente de lectura reutilizable; reutilizar ese formulario habría expuesto controles de edición innecesarios. El lector añadido es independiente y de solo lectura.

Cada recurso conserva nombre y tipo. Los enlaces HTTP/HTTPS sin credenciales se abren con `target=_blank` y `rel=noopener noreferrer`. No se usan iframes, HTML inyectado ni funciones que omitan el sanitizado de Angular. Los esquemas peligrosos, rutas internas y URL inválidas no producen enlaces. Los archivos privados conservan su descarga HTTP autenticada y las comprobaciones backend existentes; no se exponen rutas `file:` ni se cambian permisos de Drive.

La consulta tiene estados de carga, vacío y error con reintento. Cambiar de paso cancela la suscripción anterior y limpia los materiales; refrescar la misma vista no duplica consultas. Consultar el material no escribe observaciones, completa pasos ni inicia/cancela ejecuciones.

## Archivos de esta tarea

- `frontend-dimabug/src/app/features/ejecucion/ejecucion-checklist.component.ts`: importa el lector.
- `frontend-dimabug/src/app/features/ejecucion/ejecucion-checklist.component.html`: muestra materiales en paso activo y resumen.
- `frontend-dimabug/src/app/features/ejecucion/materiales-ejecucion.component.ts`: consulta por IDs históricos, validación de enlaces y descarga existente.
- `frontend-dimabug/src/app/features/ejecucion/materiales-ejecucion.component.html`: presentación accesible de recursos.
- `frontend-dimabug/src/app/features/ejecucion/materiales-ejecucion.component.css`: estilo adaptable a contenedores estrechos.
- `frontend-dimabug/src/app/features/ejecucion/materiales-ejecucion.component.spec.ts`: pruebas del lector.
- `frontend-dimabug/src/app/features/ejecucion/ejecucion-progreso-layout.component.spec.ts`: integración de materiales en pasos completados/resumen y mock actualizado; conserva pruebas responsive.
- `frontend-dimabug/src/app/features/ejecucion/ejecucion-conectividad.component.spec.ts`: mock de materiales para la vista integrada.
- `dima-bug-backend/src/test/java/cl/casol/backend/EdicionProcedimientoAdminHttpTest.java`: refuerza la conservación del material original tras publicar una revisión distinta.
- Este informe.

No se modificó código productivo de backend, DTO, endpoint, configuración Gmail ni datos de progreso. No hay migración o reinicio de backend requerido.

## QA y resultado

- Backend H2 aislado: 23 pruebas aprobadas (7 de edición/versionado y 16 de seguridad de archivos). Se confirmó la asociación de la ejecución a su versión original y la disponibilidad de sus materiales después de modificar/publicar otra revisión.
- Angular: suite completa de 441 pruebas aprobadas, sin fallos. `git diff --check` sin errores.
- Build Angular correcto; advertencias de presupuestos CSS existentes.
- Las pruebas incluyen materiales múltiples/vacíos, cambios de paso/revisión, cancelación de consulta anterior, URL peligrosas, escape de HTML, descarga autenticada, errores/reintento/timeout y no desbordamiento. La integración comprueba el material en un paso completado y en resumen sin alterar progreso, más layout a 360 y 1280 px.
- No se enviaron correos ni se escribieron datos de staging.

Al recargar `/ejecuciones/33` y abrir el paso 5 debe aparecer «Apoyo para este paso» → «Configuración Login» · Video → «Abrir enlace». El paso permanece completado. La vista `/procedimientos/15` mantiene su comportamiento. La comprobación manual autenticada en la sesión del usuario y la apertura de su recurso de Drive no se ejecutaron en estas pruebas.
