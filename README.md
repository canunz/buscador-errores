# DimaBug

Aplicación de soporte técnico: conocimiento y soluciones, tickets, procedimientos guiados y autoservicio público. Proyecto APT / Capstone, Duoc UC.

**Estado consolidado: 2026-11-09.** [Documentación y matriz IMPLEMENTADO / VALIDADO / PENDIENTE](docs/README.md).

## Arquitectura

- Frontend Angular 19 standalone, TypeScript y RxJS; guards y rutas públicas separadas.
- Backend Spring Boot 4.1.1 / Java 21, monolito modular con servicios, puertos y adaptadores, JPA/JDBC.
- MySQL y almacenamiento privado de adjuntos. H2 se utiliza únicamente en pruebas aisladas.
- JWT para funcionalidades privadas; OAuth 2.0 de Google autoriza una cuenta de correo y no reemplaza JWT.

## Funcionalidades

- Administración de conocimientos/soluciones, organización, catálogos y tickets.
- Edición administrativa de procedimientos mediante revisiones; asociación opcional a hardware; ejecuciones con contrato de pasos, validación crítica e historial.
- Solicitud y recepción manual de evidencias, revisión/aprobación y conservación de rondas.
- Correo SIMULADO, SMTP y Gmail API. OAuth con gmail.send, PKCE/state y tokens cifrados; comunicaciones auditadas e idempotentes.
- Materiales por paso y solución, descargas autorizadas y publicación individual de materiales públicos.
- Portal `/autoservicio`, chatbot de búsqueda autorizado y enlaces individuales `/autoservicio/soluciones/:id` para futuro NFC/QR.

Los envíos reales están deshabilitados por defecto en la configuración versionada. No se publican ni reenvían automáticamente solicitudes simuladas. Los enlaces NFC aún no constituyen programación o escaneo físico de etiquetas.

## Instalación y ejecución

Requisitos: Java 21, Node/npm compatibles con Angular 19, MySQL con esquema existente aprobado y directorios privados de adjuntos. Las migraciones son manuales; el repositorio no incluye un DDL base completo ni un runner que reconstruya una base desde cero.

1. Seguir [instalación local](docs/08_Despliegue/Instalacion_Local.md) y [variables de entorno](docs/08_Despliegue/Variables_Entorno.md). No guardar credenciales en el repositorio.
2. Revisar [migraciones](docs/04_Base_Datos/Migraciones.md) con la cuenta administradora correspondiente. No aplicar todos los SQL en bloque.
3. Backend, desde `dima-bug-backend`: `mvnw.cmd spring-boot:run`, con perfil y variables externas configurados.
4. Frontend, desde `frontend-dimabug`: `npm ci` para instalar el lockfile y `npm start` para desarrollo.

Backend local: 9090. Frontend: 4200 con proxy `/api` al backend. [Gmail/OAuth](docs/implementacion/automatizacion/GUIA_GMAIL_API_OAUTH.md) · [Portal público](docs/implementacion/automatizacion/PORTAL_PUBLICO_AUTOSERVICIO.md) · [NFC/QR y base configurable](docs/implementacion/automatizacion/NFC_SOLUCIONES_PUBLICAS.md).

## Validación

- Backend: `mvnw.cmd package` (compilación, pruebas y empaquetado).
- Frontend: `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build`.

[Resultados de consolidación](docs/07_Pruebas/Resultados_Pruebas.md). Las pruebas usan H2 y mocks/transportes de ensayo, sin enviar correos reales ni escribir en staging. No equivalen a UAT, pentest o certificación de producción.

## Seguridad y límites

API pública con DTO mínimos, condiciones de publicación, protección de archivos y límites de tráfico por instancia. Administración protegida en backend, además de los guards. Gmail Client ID/Secret, tokens, claves AES/JWT, credenciales MySQL y configuraciones privadas se suministran externamente.

Pendientes: dominio y despliegue definitivo, etiquetas/escaneos NFC físicos, validación completa de producción y restauración, recuperación de contraseña backend, reportes operativos reales y sincronización de las funciones aún locales. El chatbot es búsqueda SQL, no una IA generativa externa. [Límites detallados](docs/README.md#estado-actual).

`target/`, `dist/`, logs, uploads privados, credenciales y paquetes de entrega generados están excluidos de Git. La publicación remota requiere revisión y autorización independiente de `git push`.
