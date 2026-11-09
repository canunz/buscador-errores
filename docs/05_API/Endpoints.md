# API vigente y separación de acceso

Referencia 2026-11-09. Los controllers son la fuente definitiva de métodos/DTO. No se reutiliza el conteo de 124 mappings de la auditoría inicial como inventario actual.

## API pública de autoservicio

| Método/ruta | Comportamiento |
|---|---|
| GET /api/publico/soluciones?q=... | Hasta 50 soluciones activas, autoservicio y padre publicado |
| GET /api/publico/soluciones/{id} | Detalle mínimo; privado/inactivo/inexistente: 404 |
| GET /api/publico/soluciones/{id}/materiales | Solo materiales autorizados de un padre elegible |
| GET /api/publico/soluciones/{id}/materiales/{material}/archivo | Revalida padre/material antes de servir bytes |
| POST /api/publico/chatbot/consultas | Búsqueda SQL de contenido autorizado, sin JWT |

Las restantes escrituras bajo /api/publico se deniegan. No se abre anónimamente la API privada existente de conocimientos. La ruta frontend NFC reutiliza este detalle, sin crear otro backend duplicado.

## API privada relevante

- `/api/auth/login` y `/api/auth/me`: autenticación/identidad existentes.
- `/api/conocimientos/**`, `/api/tickets/**`, catálogos y organización: permisos definidos por rol/caso de uso.
- `/api/admin/conocimientos/{conocimientoId}/soluciones`: catálogo administrativo; PATCH `/{solucionId}/estado` y `/{solucionId}/autoservicio`.
- `/api/admin/soluciones/{id}/materiales`: gestión/autorizar publicación y descarga privada; solo administrador.
- `/api/procedimientos/**`: consultas, edición y revisiones; las mutaciones requieren administrador.
- POST `/api/procedimientos/{id}/ejecuciones`: inicio autorizado; las operaciones posteriores usan `/api/ejecuciones/{id}/...` y comprueban pertenencia/estado.
- GET `/api/ejecuciones/{id}/automatizacion`: solo lectura, sin enviar correo.
- POST `/api/ejecuciones/{id}/pasos/{paso}/correo`: envío explícito, con reserva/auditoría.
- POST `/api/ejecuciones/{id}/solicitudes-evidencia/{solicitud}/evidencias`: recepción manual multipart.
- POST `.../decision` y `.../nueva-solicitud`: aprobación/rechazo y nueva ronda explícita; las rutas simuladas conservan su restricción.
- GET `.../fotos/{foto}`: bytes solo para usuario autorizado de esa ejecución.

## Gmail administrativo

GET `/api/admin/gmail`, POST `/api/admin/gmail/autorizacion`, POST `/api/admin/gmail/revocar`, GET/POST `/api/admin/gmail/prueba`. JWT de administrador; no se devuelven tokens.

GET `/login/oauth2/code/google` es el callback de vinculación; valida estado de un uso, cookie y PKCE. No es un login de usuario ni un bypass de administración.

La recuperación de contraseña mostrada por el frontend no se documenta como API backend operativa.

[Índice vigente](../README.md).
