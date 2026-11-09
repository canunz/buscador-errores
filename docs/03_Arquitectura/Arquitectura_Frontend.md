# Arquitectura frontend

Angular 19 standalone: `features` contiene pantallas; `core` modelos, servicios, HTTP/guards; `shared` UI y layouts. RxJS y signals se usan según el servicio existente.

El shell privado mantiene authGuard/adminGuard/staffGuard. El portal público tiene layout propio sin menú administrativo. `/chatbot`, `/autoservicio` y `/autoservicio/soluciones/:id` no requieren sesión; `/autoservicio/:id` se conserva como alias.

`api.ts` reconoce el origen de backend y las rutas públicas. El interceptor adjunta JWT solo a peticiones privadas del backend autorizado; no lo agrega a peticiones públicas ni a recursos externos. Tokens OAuth no se exponen en Angular.

Las ejecuciones reutilizan IDs históricos y servicios de materiales. Los formularios compartidos de correo/materiales se reutilizan según el flujo, sin duplicar lógica de negocio. La base NFC es configuración pública del frontend o preferencia local del administrador; no contiene secretos.

La sesión JWT sigue en localStorage. Favoritos/avisos y otras cachés locales no constituyen un servicio remoto completo; reportes y Nuevo error conservan sus limitaciones. Guards y controles ocultos nunca sustituyen autorización backend.

[Estado global y evidencia](../README.md).
