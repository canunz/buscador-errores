# Recepción manual y aprobación de fotografías

Fecha: 2026-11-08.

## Diagnóstico confirmado antes de implementar

El formulario de archivos en `validacion-externa.component.html` estaba dentro de `s.simulado && enCurso`. Además, tanto `recibirSimulada()` como `decidir()` exigían transporte SIMULADO. Cambiar a Gmail API habilitaba enviar correos, pero no ofrecía un camino de recepción ni permitía aprobar fotografías. No se trata de una demora de Gmail: el permiso gmail.send no permite leer respuestas o descargar adjuntos.

Ya existían normalización JPEG/PNG, almacenamiento de fotografías por ronda, descarga protegida por JWT y pertenencia a la ejecución, estados de recepción y revisión, bloqueo de pasos críticos y auditoría con usuario/fecha. Se reutilizan.

## Comportamiento

1. En una solicitud ESPERANDO_EVIDENCIA y una ejecución EN_CURSO aparece **Evidencias recibidas**, tanto con correo real como simulado.
2. El técnico selecciona una fotografía por categoría configurada. Puede visualizarla y quitarla antes de **Registrar recepción**.
3. Registrar recepción valida todas las categorías y archivos en el backend, normaliza imágenes y elimina metadatos. No consulta Gmail ni envía correo. El registro es manual por usuario autenticado, no una identidad del remitente verificada por Google.
4. La solicitud queda EVIDENCIA_RECIBIDA. Los pasos críticos requieren aprobación explícita y todas las categorías adjuntas. La interfaz exige cargar las fotos antes de habilitar la aprobación.
5. **Aprobar fotografías y continuar** completa el paso después de confirmar la decisión en el backend. Un error no emite un avance.
6. **Rechazar fotografías** exige motivo y conserva los archivos; en el nuevo formulario no envía otra solicitud. Después aparece **Solicitar nuevamente**, acción explícita e idempotente que crea una nueva ronda y utiliza el transporte existente. Un doble clic o reintento de la ronda anterior no crea otra ronda adicional.
7. Las rondas y fotos registradas se conservan en el historial. No hay borrado físico de evidencias: quitar un archivo se aplica a la selección local anterior al registro; una evidencia ya registrada se rechaza y conserva.

Se conserva el contrato HTTP anterior: si un cliente omite `solicitarNuevamente` en `/decision`, mantiene el comportamiento previo de rechazo con nueva ronda. El formulario actualizado envía false para evidencias; las demás modalidades conservan su comportamiento.

## API

- POST `/api/ejecuciones/{id}/solicitudes-evidencia/{solicitud}/evidencias`: multipart con categorías existentes, remitente contextual y UUID de recepción. Reutiliza las mismas validaciones del flujo anterior sin depender del transporte.
- POST `/api/ejecuciones/{id}/solicitudes-evidencia/{solicitud}/decision`: parámetro opcional `solicitarNuevamente` para separar rechazo y nuevo envío.
- POST `/api/ejecuciones/{id}/solicitudes-evidencia/{solicitud}/nueva-solicitud`: versión requerida; solo ronda rechazada, ejecución activa y usuario autorizado. La creación está serializada con el bloqueo existente de ejecución.
- Se conservan las rutas simuladas y la descarga autenticada de fotografías.

## Archivos modificados en esta tarea

Backend:
- `dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/application/service/AutomatizarEvidenciaService.java`
- `dima-bug-backend/src/main/java/cl/casol/backend/ejecucion/infrastructure/web/AutomatizacionEjecucionController.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/ejecucion/infrastructure/persistence/AutomatizacionFlujoJpaTest.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/AutomatizacionHttpPersistenciaTest.java`
- `dima-bug-backend/src/test/java/cl/casol/backend/AutomatizacionSecurityIntegrationTest.java`

Angular:
- `frontend-dimabug/src/app/core/services/automatizacion-ejecucion.service.ts`
- `frontend-dimabug/src/app/features/ejecucion/validacion-externa.component.ts`
- `frontend-dimabug/src/app/features/ejecucion/validacion-externa.component.html`
- `frontend-dimabug/src/app/features/ejecucion/validacion-externa.component.css`
- `frontend-dimabug/src/app/features/ejecucion/validacion-externa.component.spec.ts`

## Validación

Pruebas Java sobre H2 efímero, imágenes sintéticas y transporte simulado/mock: recepción con flag de transporte real sin enviar, aprobación, rechazo, nueva ronda explícita, idempotencia, ausencia de envíos durante recepción/rechazo, propiedad de ejecución, archivos inválidos con rollback, JWT/roles, carga multipart por HTTP, descarga y conservación de historial. Pruebas de Angular con servicios mock, incluido renderizado del formulario tras envío real, validaciones, quitar selección, errores de aprobación y doble clic. Build de producción Angular.

Resultados finales: 57 pruebas Java seleccionadas aprobadas (43 de flujo JPA/H2, 9 de seguridad HTTP, 1 de flujo HTTP/JWT completo y 4 de validación de imágenes/correo simulado); suite Angular completa: 429 aprobadas; build Angular correcto, con advertencias de presupuestos CSS ya existentes. `git diff --check` sin errores.

No se ejecutaron migraciones ni cambios de datos de staging. No se modificó OAuth, JWT, credenciales, transporte Gmail ni el modo de envío. No se enviaron correos reales para validar. Para usar la nueva API, el backend debe ejecutar la compilación actualizada; no se reinició la instancia local durante las pruebas para evitar disparar seguimientos existentes. La comprobación manual en la ejecución real queda pendiente después de su reinicio habitual y recarga de Angular.
