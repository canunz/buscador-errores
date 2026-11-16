# Arquitectura backend

Spring Boot 4.1.1, Java 21, JPA y JDBC. Se mantienen los servicios/puertos/adaptadores existentes; Tickets tiene contratos específicos de casos de uso. No se afirma una separación hexagonal uniforme de todos los módulos.

- Identidad: JWT/roles; SecurityConfig protege administración y operaciones.
- Conocimiento: soluciones, publicación y búsqueda SQL. La API pública proyecta DTO mínimos y verifica elegibilidad en listado, detalle y materiales.
- Procedimientos: revisión administrativa con auditoría; el publicado no se edita retroactivamente; materiales ligados a IDs de pasos de esa revisión.
- Ejecución: contrato capturado, estados, control de versiones, pasos críticos y solicitudes de evidencia. La recepción manual normaliza imágenes y exige aprobación explícita cuando corresponde.
- Correo: SIMULADO, SMTP y Gmail API. OAuth usa gmail.send, state/PKCE, cifrado AES-256-GCM y renovación; la reserva/idempotencia y el resultado se auditan. Aceptación del proveedor no acredita entrega en bandeja ni evidencia recibida.
- Archivos: validación de contenido y límites, filesystem privado, compensación y descarga autorizada.

La consulta del estado de automatización no debe enviar correo ni esperar a Gmail. Los transportes externos no deben prolongar una transacción de negocio. Los límites públicos son locales por instancia y no equivalen a un control distribuido de tráfico.

Consultar las guías de implementación para límites concretos y evidencia; los hallazgos históricos de otros módulos no se declaran cerrados por esta consolidación.

[Estado global y evidencia](../README.md).
