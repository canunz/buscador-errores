# Resolución del merge para UAT — 9 de noviembre de 2026

## Alcance y referencias

- Rama de trabajo: `integracion-final-uat` (sin cambiar de rama).
- HEAD inicial: `925dd6b77281e722ce9ffa9142cc7f0302a019fe`.
- Commit entrante: `584a8c85c2eeaa8e14a40d0a61f535f8939990d8` (`codex/integracion-mvp`).
- Base común: `62d29350a7190828e858549994ce6b82395baf55`.
- 21 conflictos: 16 archivos añadidos por ambos lados y 5 modificados por ambos lados.
- Se compararon las versiones del índice 1/base, 2/ours y 3/theirs. Los conflictos add/add no tienen versión base. Se conservaron copias de diagnóstico en `dima-bug-backend/target/merge-review` (ignorado por Git).

## Decisiones

| Archivos en conflicto | Resolución |
| --- | --- |
| `ConsultarChatbotService.java`, `AutoservicioPublicoController.java`, `ChatbotResponse.java` | Incorporar el ID de solución al contrato y mantener el filtrado público y la ocultación del nombre interno del conocimiento. |
| `AutoservicioPublicoHttpTest.java`, `ChatbotSecurityIntegrationTest.java`, `ConsultarChatbotServiceTest.java` | Conservar las comprobaciones de privacidad, roles y contrato con ID. |
| `core/config/api.ts` | Mantener validación de origen/prefijo, rutas públicas de autoservicio y endpoints públicos del ingreso por código. |
| `core/models/chatbot.model.ts` | Añadir ID opcional para conservar compatibilidad con respuestas anteriores. |
| `admin-catalogo.component.html`, `admin-catalogo.component.css` | Mantener diseño actual, estados, asociación por ID, recuperación de creación y advertencias de sistemas compartidos; incorporar accesos globales a Sistemas y Módulos. |
| `login.component.html` | Conservar formulario de ingreso por código compatible con el componente, temporizadores y acceso público; no restaurar el formulario antiguo de contraseña. |
| `portal-layout.component.ts` | Combinar diseño y navegación según sesión con el logotipo oficial existente. |
| `portal-publico.component.spec.ts` | Conservar pruebas de logotipo, seguridad, rutas antiguas/NFC y responsive; agregar regresiones de navegación según sesión. |
| `portal-soluciones.component.ts`, `.html`, `.css` | Conservar diseño público, estados de búsqueda, esqueletos, tipos y enlaces; delegar materiales al componente compartido para evitar duplicación. |
| `chatbot.component.ts`, `.html`, `.spec.ts` | Incorporar el componente plegable de materiales públicos y pruebas de continuidad de conversación. |
| `hardware-sistemas-navegacion.spec.ts` | Conservar pruebas por ID y validar salidas globales utilizando enlaces reales. |
| `hardware.component.html` | Conservar consulta técnica, estados, sistemas por ID y controles administrativos; añadir acceso global a Sistemas. |

## Integración automática revisada

- Los dos archivos nuevos `application-gmail.properties` y `application-smtp.properties` son perfiles de configuración con variables de entorno; no se cambiaron sus valores, credenciales ni se activaron perfiles de envío real.
- `materiales-publicos.component.ts` y su prueba reutilizan el endpoint público existente, filtran publicación, validan enlaces y cancelan consultas al destruirse.
- Procedimientos, ejecuciones y las rutas generales no cambian respecto de HEAD; se conserva su versionado y sus enlaces NFC/QR.
- Permanecen las implementaciones de ingreso por código, administración, reportes y navegación presentes en HEAD.

## Incompatibilidades detectadas al ejecutar todas las pruebas

- Cinco fixtures HTTP seguían usando `POST /api/auth/login` aunque HEAD incorpora autenticación por código. Ahora recorren solicitud y verificación de código y capturan el código únicamente en un mock de `EnvioCodigoAccesoPort`; conservan generación JWT y validación HTTP reales. Se comparte el helper `LoginCodigoTestSupport` y no se reintroduce el endpoint de contraseña.
- El flujo real de código reveló que el esquema H2 generado por JPA no incluye `usuario_ultimo_ingreso`, actualizada con SQL nativo. El helper reproduce esa columna de la migración existente exclusivamente tras verificar H2 en memoria y comprueba que se registre la fecha de ingreso; no cambia entidades, migraciones ni staging.
- La prueba HU01 del endpoint retirado esperaba 401; se corrige a 404, manteniendo las pruebas de JWT, roles e ingreso por código.
- La regla ArchUnit de Tickets detectó que `AvisoTicketCriticoCorreoAdapter` dependía de la API de Identidad fuera del paquete permitido. Se reubica en `tickets.infrastructure.integracion`, sin cambiar envío, transacciones ni destinatarios y sin debilitar la regla. Se retiró únicamente su bytecode obsoleto de `target/classes` para evitar duplicar el bean en esta compilación incremental.

## Validación

- Angular: **481 pruebas aprobadas**, incluyendo seis regresiones adicionales sobre login por código y navegación según sesión. Se ejecutó la suite completa con `ng test --watch=false --browsers=ChromeHeadless`.
- Frontend: compilación de producción correcta con `ng build --configuration production`. Persisten advertencias de presupuesto CSS en estilos existentes; no bloquean la compilación.
- Backend: **1132 pruebas aprobadas**, 0 fallos, 0 errores, 0 omitidas (110 suites), en la ejecución final de `mvnw.cmd -q package`; JAR generado correctamente. Las 81 regresiones de los siete escenarios afectados también pasaron por separado.
- Índice Git sin entradas no fusionadas; los 21 conflictos quedaron resueltos. HEAD y MERGE_HEAD permanecen intactos.
- Marcadores de conflicto ausentes del código; comprobación de espacios del índice correcta.

Las pruebas utilizan mocks/HTTP de pruebas y bases de prueba; no se han ejecutado migraciones ni acciones sobre staging. No se han enviado correos reales. La lectura de enlaces NFC/QR se valida por rutas, sin programar etiquetas ni comprobar un teléfono físico.

## Estado de entrega

El merge queda con los 21 conflictos resueltos y los archivos preparados en el índice. No se crea el commit de integración, no se hace push y no se despliega. La revisión y autorización del commit corresponden a la usuaria.
