# Corrección del 404 al registrar recepción — ejecución 34

## Diagnóstico

- Botón: `ValidacionExternaComponent.recibir(false)`.
- Servicio: `AutomatizacionEjecucionService.subir()` usa POST multipart a `/api/ejecuciones/{id}/solicitudes-evidencia/{solicitudId}/evidencias`.
- Payload: `remitente`, `mensajeId` (UUID idempotente) y archivos por categoría; el navegador genera el boundary multipart.
- Backend: `AutomatizacionEjecucionController.recibirFotos()` ya publica exactamente ese POST con `consumes=multipart/form-data`. Reutiliza `AutomatizarEvidenciaService.recibir()` y el validador existente.
- La instancia anterior (PID 47144) arrancó antes de compilar el controlador con el endpoint: su JVM conservaba las clases anteriores. Arranque anterior: 21:41:26; controlador compilado: 22:00:26; nueva instancia: 22:13:50 (hora de Chile, 2026-11-08). No hay recarga automática de clases por recompilar con Maven. Se trata de un desfase entre frontend actualizado y backend en memoria, no de una ruta que deba duplicarse.
- Consulta de solo lectura a `dima_bug_staging`: ejecución 34 EN_CURSO; solicitud `eff77cfb-92a5-3add-8e20-2f8903903a71`, paso 193, ronda 1, ESPERANDO_EVIDENCIA, 0 fotografías; pertenece a esa ejecución. No hay relaciones con un paso ajeno.
- Solicitud ajena o inexistente se protege con 403; ejecución inexistente produce `EjecucionNoEncontradaException`/404. Los permisos operacionales y la pertenencia se comprueban en backend. Los estados incompatibles producen conflicto. En este caso los registros existen y el estado permite recepción.

## Corrección mínima

Se reinició únicamente la instancia local identificada de BackendApplication en 9090, cargando las clases ya implementadas. Se conservaron argumentos y entorno originales en memoria, incluyendo perfiles staging,gmail y modo GMAIL_API. No se imprimieron ni persistieron credenciales. No se cambió la configuración Gmail, JWT, puertos o datos de negocio. No se añadió otro controlador o endpoint.

Se amplió el helper local con `--preserve-configuration`: prepara el comando antes de detener el proceso y permite conservar la configuración original. El comportamiento anterior del helper sin esta opción permanece igual. Se añadió una utilidad diagnóstica limitada a lecturas de la ejecución 34 y conteos de cola.

La instancia nueva (PID 7832) respondió 200 en `/api/health`; el proxy Angular en `http://localhost:4200/api/health` también respondió 200. Una segunda consulta confirmó que la ejecución 34 seguía sin fotografías y sin cambios de estado antes de la validación manual. Cero envíos pendientes y cero seguimientos reales vencidos durante las verificaciones.

## Archivos modificados en esta corrección

- `docs/implementacion/automatizacion/herramientas/reiniciar_backend_staging_local.py`: opción de reinicio conservando configuración.
- `docs/implementacion/automatizacion/herramientas/diagnosticar_recepcion_local.py`: diagnóstico local sin escritura ni secretos.
- `dima-bug-backend/src/test/java/cl/casol/backend/ejecucion/infrastructure/persistence/AutomatizacionFlujoJpaTest.java`: caso explícito de ejecución inexistente, solicitud inexistente y solicitud de otra ejecución, sin escrituras ni envíos.
- Este informe.

No se modificó código de producción Angular/Spring, ni se realizaron migraciones.

## QA

Validación aislada sobre H2 y transporte simulado/mock: endpoint multipart, fotos almacenadas por categoría, solicitud/ejecución inexistentes, pertenencia, JWT y roles, imágenes inválidas y rollback, duplicados idempotentes, aprobación explícita de paso crítico, rechazo, rondas e historial. No se usó la ejecución 34 para cargar archivos sintéticos ni se enviaron correos reales.

Resultado final de QA: 58 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas: 44 de flujo JPA/H2, 9 de seguridad HTTP, 1 de flujo HTTP/JWT/multipart completo y 4 de imágenes/correo simulado. `git diff --check` sin errores. Utilidades Python verificadas mediante py_compile.

La comprobación funcional autenticada en la ejecución 34 se solicitó al usuario: recargar esa ejecución y registrar sus fotografías. Hasta recibir su resultado no se afirma haber registrado fotos en staging. El resultado esperado es HTTP 200, fotografías visibles para revisión y el paso crítico bloqueado hasta su aprobación explícita.
