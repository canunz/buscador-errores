# WhatsApp: catálogo público principal por HTTP local

## Alcance

Primera etapa: búsqueda y preparación pública de soluciones. La base durable sigue siendo `wa_durable_0eb3331f3918`; no se cambia el datasource, las tablas, credenciales, cifrado, deduplicación, OCR Linux ni autenticación. Los tickets siguen creándose mediante el adaptador local existente, en QA. Esta etapa no unifica tickets.

Flujo: WhatsApp → BuscarSolucionesCanalPort → HTTP loopback al backend principal → servicios públicos actuales → catálogo principal. Antes de enviar: PrepararRespuestaPublicaPort → GET público de cada solución → contenido vigente y autorizado. Se mantienen filtros de conocimiento publicado, solución activa y autoservicio; no se piden materiales privados ni campos internos. No se duplican reglas del buscador ni del extractor OCR.

## Configuración preparada (no aplicada en DigitalOcean)

`WhatsAppDurableConfig` importa `CatalogoWhatsAppConfig` y recibe ambos puertos explícitamente. El modo predeterminado `local` conserva el comportamiento anterior. Para una futura activación autorizada:

```properties
dimabug.whatsapp.catalogo.modo=http-principal
dimabug.whatsapp.catalogo.url-base=http://127.0.0.1:8081
dimabug.whatsapp.catalogo.conexion-ms=1000
dimabug.whatsapp.catalogo.respuesta-ms=3000
```

El cliente solo acepta IPv4 loopback con puerto explícito, sin ruta, credenciales, query ni fragmento. Rechaza el puerto propio de WhatsApp. No usa el proxy Graph ni sigue redirecciones. No necesita token porque consume los endpoints públicos existentes. El HTTP sin TLS queda limitado al mismo host; un destino remoto exigiría otro diseño autorizado.

POST `/api/publico/chatbot/consultas` con `consulta` (1–300 caracteres). GET `/api/publico/soluciones/{id}`. Solo se conservan identificador, título e instrucciones de la solución. Límite de respuesta 256 KiB, cinco resultados, plazos configurables entre 1 y 5000 ms. El plazo de respuesta incluye la recepción completa del cuerpo.

## Errores y reintentos

Cada reserva durable hace un solo intento HTTP; no hay esperas bloqueantes ni reintentos internos que multipliquen solicitudes. Timeouts, conexión fallida, contrato inválido, 408, 429 y 5xx usan el reintento durable existente (máximo cinco intentos, espera exponencial con variación). `Retry-After` válido se respeta como mínimo, hasta 24 horas y sujeto al vencimiento original del evento/respuesta. Otros códigos HTTP cierran el intento como error permanente. No se interpreta una indisponibilidad como catálogo vacío ni se crea un ticket por ese fallo. No existe fallback al catálogo QA.

404 en el detalle significa solución retirada y la omite; 404 en búsqueda es un error técnico. Una búsqueda válida con lista vacía conserva el flujo de autoatención actual. La limitación pública por IP/global sigue vigente: todos los accesos loopback comparten presupuesto; no se falsifica la IP del participante. Antes de activar se debe comprobar que ese presupuesto cubra el volumen previsto.

No se registran consultas, cuerpos, tokens ni excepciones de transporte con datos externos. Los errores propagados usan un mensaje técnico fijo. HMAC de Meta, autorización de participantes y envío no cambian.

## Protección de planes antiguos

Cada solución incorpora `catalogo` en el plan ya cifrado. Las nuevas soluciones HTTP llevan procedencia principal ligada al puerto; las locales llevan `qa-local-v1`. El JSON histórico sin campo sigue descifrándose. El preparador HTTP no consulta identificadores sin procedencia principal coincidente: descarta las instrucciones y devuelve un aviso para repetir la consulta. El preparador local rechaza de igual forma planes marcados como principales. No hay migración SQL ni actualización masiva de filas.

Reversión segura: cambiar a modo `local` usando esta versión conserva el guardián. No volver a un binario anterior a esta protección mientras existan planes principales pendientes: ese binario podría ignorar `catalogo`. Antes de una reversión a versión antigua, detener consumidores y resolver los pendientes con un procedimiento autorizado; no reinterpretar ni copiar identificadores.

No se actualizan el estado conversacional ni los tickets históricos para apuntarlos a otro catálogo. Las respuestas ya enviadas no se reenvían.

## Propuesta de segunda etapa: tickets (sin implementar)

Mantener `CrearTicketCanalPort` y sustituir únicamente su adaptador por HTTP hacia un endpoint interno autenticado del backend principal, que delegue en su fachada/caso de uso actual. No exponer la creación administrativa mediante el endpoint público ni otorgar acceso SQL cruzado. Actor de servicio y permisos definidos en el principal; no aceptar un actor arbitrario enviado por WhatsApp.

La creación debe usar una clave de idempotencia estable asociada al evento durable. El principal debe persistir su relación con el ticket y devolver la misma referencia ante reintentos. Ante timeout posterior al alta, consultar/repetir con esa clave en vez de crear otro ticket. Unicidad y persistencia de esa clave podrían requerir una migración propia, pendiente de diseño y autorización. Conservar outbox/reservas en QA y consistencia eventual, sin transacciones distribuidas ni credenciales compartidas.

Validar autorización, mínimo de datos personales, altas repetidas, respuesta perdida, reinicios y referencia final del ticket. Ninguno de esos cambios se ejecuta en esta etapa.

## Validación

Pruebas locales con datos sintéticos, HTTP loopback y H2 aisladas: contrato, filtros públicos reales, revocación antes de envío, procedencia histórica, cifrado, configuración, errores, plazos, tamaño máximo y programación durable de reintentos. No requieren Meta, MySQL existente ni servicios de DigitalOcean. Los resultados de ejecución se informan en la entrega.

### Resultados registrados el 22 de noviembre de 2026

- Compilación Java 21 correcta, sin dependencias nuevas.
- 44 pruebas nuevas aprobadas: 32 de cliente/adaptadores HTTP, cinco de configuración/procedencia local, dos de trabajadores, una de HTTP público real con H2 y cuatro adicionales de persistencia durable con H2.
- Conjunto WhatsApp: 221 pruebas, 208 aprobadas y 13 omitidas (11 de proxy HTTPS condicionado y dos de recuperación condicionada). Sin fallos ni errores. La integración HTTP del principal es adicional a ese conjunto.
- Suite backend completa: 1488 pruebas; 1407 aprobadas, tres fallos de aserciones, 65 errores y 13 omitidas. No se declara la suite completa aprobada.
- Los 65 errores pertenecen a CopiarSolucionJpaConcurrencyTest (9), MaterialesSinOsivJpaTest (5), AutomatizacionFlujoJpaTest (44) y EjecucionContratoJpaTest (7); H2 informa una restricción de u_usuario inválida y una sesión/base cerrada. Se reprodujeron también separando JVM y contra el código original de HEAD, exportado a un directorio temporal ignorado.
- Los tres fallos pertenecen a CorreoCodigoIntegracionTest: diferencias 429/200, concurrencia y 404/202. Los mismos tres se reprodujeron en el commit original, sin esta implementación. No se modificaron esos módulos ni sus pruebas.
- `git diff --check` correcto; revisión de patrones de credenciales en los 23 archivos cambiados sin hallazgos. Esto no sustituye una auditoría de secretos del repositorio completo.

Comandos ejecutados desde `dima-bug-backend`:

```powershell
.\mvnw.cmd -B '-Dmaven.test.skip=true' compile
.\mvnw.cmd -B '-Dtest=CatalogoPublicoHttpTest,CatalogoWhatsAppConfigTest,CatalogoPrincipalHttpIntegrationTest' test
.\mvnw.cmd -B test
.\mvnw.cmd -B '-Dtest=CopiarSolucionJpaConcurrencyTest,MaterialesSinOsivJpaTest,AutomatizacionFlujoJpaTest,EjecucionContratoJpaTest,CorreoCodigoIntegracionTest' '-DreuseForks=false' test
```

La suite completa incluyó las cuatro pruebas nuevas de persistencia y las dos de trabajadores. Los contrastes del commit original ejecutaron las mismas cinco clases fallidas, en la copia temporal. Evidencias ignoradas por Git: `qa/runtime/catalogo-http-compile.log`, `catalogo-http-tests.log`, `catalogo-backend-regresion.log`, `catalogo-regresion-aislada.log`, `catalogo-correo-baseline.log` y `catalogo-jpa-baseline.log` (estos últimos bajo `qa/runtime`). No se conectó con Meta, MySQL existente ni DigitalOcean.

Pendiente de autorización posterior: despliegue y activación de `http-principal`, comprobación operativa del presupuesto público de solicitudes y prueba controlada real. Los fallos preexistentes de la suite general se dejan visibles para revisión, sin ampliar esta etapa a correo/procedimientos. No hubo commits ni push.
