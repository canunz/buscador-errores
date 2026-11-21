# Portal Mis tickets — implementación local

## Diagnóstico y reutilización
El componente anterior usaba una plantilla en línea con espaciado manual, campos sin agrupación visual, prioridad elegible y una lista mínima. El layout básico añadía otro encabezado. Reutilizados: logo existente, tipografía Inter, paleta y clases de tickets-comun.css (franja, tarjetas, campos y botones), TicketService y autenticación/guards actuales. No se incorporó una biblioteca visual.

Existían ticket_adjunto, repositorio de adjuntos, almacenamiento filesystem con UUID y descarga autenticada. El filtro de alcance básico bloqueaba todos los adjuntos y su proyección los eliminaba. No se necesita migración ni nueva tabla.

## Cambios
- Cabecera única, formulario vertical y tarjetas de historial, estados legibles, fechas, paginación, vacío, carga, éxito y errores. Dos columnas en escritorio; una en tablet y móvil. Conserva foco visible, labels y regiones de estado.
- Sin prioridad, solicitante, clasificación ni asignación en el formulario. CrearTicketService fija MEDIA para alcance básico incluso si se manipula el JSON antiguo. La nueva creación multipart también fija MEDIA.
- Fotografías JPG/PNG/WEBP, selección múltiple, preview, eliminación y revocación de Object URLs. Envío bloqueado mientras existe una solicitud en curso.
- POST /api/tickets/solicitudes: solo título, descripción y fotos. Una transacción para ticket y metadatos; validación previa a escritura y compensación de archivos tras rollback. No hay subidas posteriores independientes desde el portal básico.
- GET /api/tickets/solicitudes/limites: devuelve los límites efectivos autenticados. Por defecto dimabug.solicitudes.max-fotos=5 y dimabug.solicitudes.max-bytes=5242880. El constructor rechaza configuraciones fuera de 1–10 fotos / 1–25 MB. Los límites multipart y del proxy también deben ser compatibles.
- Verificación por bytes y decodificación real, límite de 20 millones de píxeles por foto y decodificación en memoria. WEBP usa imageio-webp 3.15.0 de TwelveMonkeys mediante ImageIO. JPG/PNG usan el JDK. Documentación primaria: https://github.com/haraldk/TwelveMonkeys (complementos ImageIO).
- Descarga autorizada GET /api/tickets/{id}/adjuntos/{adjuntoId}. El usuario básico solo ve fotografías que él adjuntó al ticket, sin comentarios asociados; se rechazan fotos internas, de otros autores y tickets ajenos. La proyección omite autor y referencia física.
- Nombres físicos UUID, nombres de presentación saneados, archivos nuevos 0600 en sistemas POSIX, sin caché HTTP de imágenes. La caché en memoria de TicketService ahora incluye la cuenta y su alcance.
- Se mantienen las rutas internas bloqueadas. Sin cambios de roles, Gmail, OAuth, SMTP o automatizaciones.

## Pruebas y evidencias
160 pruebas backend, cero fallos/errores/omitidas: creación, prioridad forzada, formatos reales/falsificados, límites, rollback simulado y transacción H2 real, resultado UNKNOWN, proyección privada, descarga ajena, contratos HTTP/JWT, regresiones de tickets y arquitectura. 12 pruebas Angular/guards satisfactorias: doble clic, errores, validación, previews, historial, rutas protegidas y ausencia de selector de prioridad.
Compilación Angular production satisfactoria. Advertencias previas de presupuestos CSS de otras pantallas y CommonJS de tesseract; no se alteraron esas funcionalidades.
Capturas del Angular compilado con API local sintética: 1440, 768 y 390 px, sin desbordamiento horizontal; captura móvil adicional con preview. Sin datos ni tokens reales y sin contacto con staging. Los archivos verification.json, source-hashes.json y los logs están en qa/artifacts/portal-solicitudes.

## Riesgos y comprobaciones antes de desplegar
1. Falta E2E multipart con el backend real, filesystem y MySQL QA; aquí se ejecutaron mocks, MVC y H2 en memoria. No se modificó ninguna base MySQL.
2. Confirmar permisos/ACL de la carpeta privada existente y que no se publique por Nginx; comprobar espacio libre y límites de carga Nginx/multipart (por defecto la selección máxima suma 25 MB). Los archivos nuevos son 0600 en POSIX; Windows conserva las ACL heredadas.
3. Un cierre abrupto del proceso o un fallo de borrado puede dejar archivos que requieran reconciliación. No se borran automáticamente ante resultado transaccional UNKNOWN para evitar perder archivos de una transacción potencialmente confirmada. No hay atomicidad distribuida entre MySQL y filesystem.
4. El bloqueo de doble clic evita solicitudes simultáneas en el formulario; no constituye idempotencia persistente entre pestañas o tras pérdida de respuesta. Ante error ambiguo se solicita revisar el historial antes de reenviar.
5. No se probaron dispositivos físicos. Las capturas son tamaños responsive en Chrome de escritorio.
6. Antes de generar un release, aislar estos cambios de los cambios locales previos ajenos al portal. El build local completo sirve de verificación, no es un artefacto aprobado para desplegar.
7. Revisar la nueva dependencia WEBP en el empaquetado y ejecutar la misma regresión sobre el candidato final.

## Archivos de producto y pruebas tocados en esta tarea
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.ts
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.html
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.css
- frontend-dimabug/src/app/features/tickets/solicitudes-basicas.component.spec.ts
- frontend-dimabug/src/app/core/services/ticket.service.ts
- frontend-dimabug/src/app/shared/layout/shell-layout.component.html
- dima-bug-backend/pom.xml
- dima-bug-backend/src/main/java/cl/casol/backend/identidad/infrastructure/security/AlcanceBasicoFilter.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/service/CrearSolicitudService.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/service/CrearTicketService.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/service/ContextoTicketService.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/service/AdjuntarArchivoTicketService.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/application/port/in/TicketVista.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/SolicitudController.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/web/AdjuntoTicketController.java
- dima-bug-backend/src/main/java/cl/casol/backend/tickets/infrastructure/almacenamiento/AlmacenAdjuntosFilesystemAdapter.java
- dima-bug-backend/src/test/java/cl/casol/backend/SolicitudPortalHttpTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/tickets/application/service/CrearSolicitudServiceTest.java
- dima-bug-backend/src/test/resources/tickets/foto.jpg
- dima-bug-backend/src/test/resources/tickets/foto.png
- dima-bug-backend/src/test/resources/tickets/foto.webp

Herramientas y evidencia local: qa/artifacts/portal-solicitudes/. No se realizaron commits ni cambios remotos.
