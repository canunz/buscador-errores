📋 DimaBug — Resumen de implementación y pendientes
Skill 4.5: QA Funcional, UAT y Estabilización del MVP
Fecha: 18 de noviembre de 2026

Después de todas las pruebas realizadas, podemos distinguir claramente lo que ya funciona, lo que está implementado pero falta validar y lo que todavía debemos completar.
1. ✅ Funcionalidades implementadas
Componente	Implementación	Estado
Backend Spring Boot	Receptor de webhooks de WhatsApp	✅ Implementado
Integración Meta	Aplicación configurada para WhatsApp Cloud API	✅ Configurada
Webhook HTTPS	Recepción mediante túnel temporal Cloudflare	✅ Verificado
Verify Token	Verificación GET mediante hub.challenge	✅ Funciona
Seguridad HMAC-SHA256	Validación criptográfica de solicitudes	✅ Probada
Autorización WABA	Validación de cuenta empresarial autorizada	✅ Implementada
Autorización Phone Number ID	Validación del número autorizado	✅ Implementada
Autorización de participantes	Validación mediante teléfono y BSUID	✅ Implementada
Deduplicación	Control para evitar mensajes duplicados	✅ Implementada
Persistencia MySQL	Mecanismo de recepción durable	✅ Implementado
Diagnóstico HTTP	Registro de solicitudes, respuestas y rechazos	✅ Implementado
Pruebas automatizadas	18 pruebas satisfactorias del JAR actualizado	✅ Completadas
Cierre automático	Detención programada del entorno QA	✅ Implementado


Importante: que una funcionalidad esté implementada no significa necesariamente que ya haya sido validada con mensajes reales.
2. 🟡 Funcionalidades pendientes de validación
Funcionalidad	Situación actual
Recepción de mensajes reales	No se ha confirmado una recepción exitosa
Suscripción messages v27.0	Existe evidencia anterior; falta confirmar su estado actual
Autorización de mensajes reales	No validada satisfactoriamente de extremo a extremo
Persistencia MySQL QA	0 eventos registrados
Deduplicación real	No comprobada con mensajes persistidos
Respuesta HTTP 200 a un POST válido	Pendiente
Recuperación ante reintentos	Pendiente de validación funcional


¿Cuál es el problema actual?
El flujo esperado es:

WhatsApp → Meta → Cloudflare → Spring Boot → MySQL

Hasta ahora:
- ✅ Meta puede verificar el webhook mediante GET.
- ✅ Spring Boot ha recibido solicitudes POST anteriores.
- ✅ La validación HMAC ha funcionado.
- ❌ No se ha confirmado la entrega del último mensaje.
- ❌ No tenemos ningún mensaje persistido en MySQL QA.
El bloqueo actual está antes de Spring Boot, porque no se registró un POST correspondiente al último mensaje.

Esto no demuestra todavía que el problema sea la suscripción, pero sí convierte su verificación en la prioridad inmediata.
3. ⬜ Funcionalidades que faltan activar o completar
Estas funcionalidades permanecen desactivadas deliberadamente durante las pruebas:
Funcionalidad	Estado
Procesamiento automático de mensajes	🔒 Desactivado
Búsqueda de soluciones en DimaBug	🔒 Desactivada
OCR para imágenes y documentos	🔒 Desactivado
Procesamiento de archivos multimedia	🔒 Desactivado
Envío de respuestas por WhatsApp	🔒 Desactivado
Integración completa WhatsApp → conocimiento → respuesta	⬜ Pendiente


No todas requieren desarrollo desde cero: debemos revisar cuáles ya están implementadas y cuáles necesitan desarrollo o integración adicional.
4. 🎯 ¿Qué falta para terminar la integración WhatsApp?
Propongo dividirlo en cuatro objetivos concretos.
Orden	Objetivo	Resultado esperado
1	Confirmar suscripción messages en Meta	Meta entrega un POST real
2	Validar recepción y autorización	Spring Boot responde 200
3	Comprobar persistencia	Primer mensaje registrado en MySQL
4	Activar progresivamente el procesamiento	DimaBug consulta conocimientos y prepara respuestas


Nuestra prioridad absoluta es completar los objetivos 1, 2 y 3.

No tiene sentido activar OCR, búsqueda o respuestas mientras no consigamos recibir correctamente un mensaje.
5. 📊 Estado general de la Skill 4.5
Etapa	Estado
Preparación del entorno QA	✅ Completada
Configuración de Meta	🟡 Requiere comprobación
Seguridad del webhook	✅ Implementada y parcialmente validada
Recepción de mensajes reales	🔵 Actual
Persistencia y deduplicación	⬜ Pendiente de validación
Procesamiento y respuestas	⬜ Pendiente
Pruebas UAT completas	⬜ Pendiente
Estabilización del MVP	⬜ Pendiente


Mi conclusión
No estamos empezando desde cero. Tenemos implementada buena parte de la infraestructura de recepción, seguridad y persistencia.

Lo que todavía no hemos conseguido es demostrar el funcionamiento completo mediante un mensaje real.

Por eso, mi recomendación es no seguir modificando Spring Boot ni MySQL por ahora.

El siguiente paso debe ser exclusivamente comprobar en Meta que el campo messages v27.0 esté suscrito en la aplicación asociada al número de prueba.

Una vez confirmada esa condición, podremos concentrarnos en completar la recepción y persistencia, sin repetir todas las verificaciones anteriores.

6. 🆕 Autoatención WhatsApp + OCR + tickets (18-11-2026, implementado, desactivado por defecto)
Flujo: "Hola" → "¿En qué puedo ayudarte?" → foto del error → OCR → solución de la base de conocimiento (ej. "Reiniciar el POS de venta") → "¿Se solucionó? SÍ/NO" → NO o sin solución → ticket en DimaBug (TK-AAAA-NNNN).
Componente	Estado
Reglas de conversación (AutoatencionWhatsAppService)	✅ Implementado + 12 pruebas
Descarga de fotos desde Meta (GraphMetaCliente)	✅ Implementado, falta probar con Meta real
OCR de fotos reales por stdin (PNG/JPEG/WEBP)	✅ Probado con PNG y JPEG
Envío real de respuestas (EnvioMetaGraphAdapter → estado ACEPTADA)	✅ Implementado, falta probar con Meta real
Creación de tickets (TicketWhatsAppAdapter)	✅ Implementado, falta usuario de servicio Soporte
Guardián: conexión/envío real solo con autoatención + graph-token + ticket-actor-email	✅ Probado

Para activarlo se necesita: token Graph (whatsapp_business_messaging), usuario de servicio con rol Soporte, teléfono del participante con entrada tel: en la lista QA y un conocimiento público para el error del POS.
Detalle: docs/implementacion/WhatsApp_autoatencion_OCR_tickets_2026-11-18.md
Sigue vigente la prioridad: confirmar primero la recepción real (objetivos 1–3) antes de activar estas banderas.

7. ✅ Primera recepción real persistida (18-11-2026, 21:37:28 Santiago)
Objetivos 1, 2 y 3: CUMPLIDOS. Flujo WhatsApp → Meta → Cloudflare → Spring Boot → MySQL QA validado: POST HTTP 200, HMAC y autorización superadas, 1 evento TEXTO/PENDIENTE cifrado, 0 intentos y 0 respuestas.
Causa del bloqueo anterior (dos problemas en Meta, ninguno en Spring Boot):
Problema	Corrección
App "Soporte" (1146725537528140) en modo desarrollo: Meta solo entrega webhooks de prueba del panel	Se publicó la app con la política de privacidad https://carolina-cheuquepil.github.io/dimabug-privacidad/
La cuenta WhatsApp 1769031874200718 solo tenía suscrita la app interna "WA DevX Webhook Events 1P App"	POST /1769031874200718/subscribed_apps; GET confirma "Soporte" suscrita
Nota: el número de prueba +1 555 185 8006 pertenece a la app "Soporte", no a la app "DimaBug".
Pendiente en Meta: completar la "comprobación del uso de datos" cuando aparezca en Acciones requeridas.
Duplicación de tickets por reintento: CONTROLADA (idempotencia por mensaje de WhatsApp, 24 h; TicketWhatsAppAdapterTest).
Siguiente: token de usuario del sistema, usuario de servicio Soporte, conocimiento público del POS, bsuid en participantes; luego activar procesamiento por etapas.

8. ✅ Base QA completa para autoatención (19-11-2026)
Validación APROBADA en MySQL QA real:
Elemento	Resultado
Base nueva	wa_durable_b8fedc24e152 (47 tablas, incluidas wa_conversacion, wa_evento y wa_respuesta)
Esquema	qa/mysql/schema/20_schema_final.sql + V20261115 + V20261116 + tablas wa_*
Solicitante de tickets	soporte@qa.dimabug.test (fixture mvp-qa-v2, rol SOPORTE, sin credencial utilizable)
Conocimiento	"Error de conexión del POS de venta: caja sin conexión" + solución pública "Reiniciar el POS de venta"
Idempotencia del seed	Ejecutado dos veces, sin duplicados
JAR completo	Compilado; Spring Boot arrancó con ddl-auto=validate
Estado final	Servicios QA detenidos; autoatención, procesamiento, medios y envío desactivados
Generador: qa/whatsapp/build_autoatencion_base_qa.py (escribe en qa/runtime/autoatencion, no ejecuta). Datos: qa/whatsapp/autoatencion-datos-qa.sql.
El JAR anterior es solo de recepción: para la autoatención debe usarse el JAR completo.

9. 🎯 Plan de activación por etapas
Configuración privada previa: JDBC a wa_durable_b8fedc24e152; ticket-actor-email=soporte@qa.dimabug.test; graph-token del usuario del sistema dimabug-whatsapp (vence aprox. 17-01-2027); participantes tel:<TELEFONO_AUTORIZADO> y bsuid:CL.<BSUID_PRIVADO> con el mismo alias.
Etapa	Banderas	Prueba	Resultado esperado
A. Texto simulado	recepcion, procesamiento, envio, autoatencion.habilitada = true; envio-real, medios, conexion-meta = false	"Hola" → "El POS no tiene conexión con la caja" → "No"	wa_evento TERMINADO (SALUDO, RESULTADOS, TICKET_CREADO); wa_respuesta SIMULADA ×3; 1 ticket con "Referencia WhatsApp:"
B. Envío real de texto	+ envio-real = true	"Hola"	Respuesta en el celular; wa_respuesta ACEPTADA
C. Fotos	+ medios, conexion-meta = true	Foto del error del POS → "No"	Solución "Reiniciar el POS de venta"; ticket con código enviado por WhatsApp
Si una etapa falla: detener y revisar estado y códigos de wa_evento / wa_respuesta (sin contenido).

10. ✅ P0 Arranque de Spring Boot en DigitalOcean: RESUELTO (21-11-2026)
Causa raíz: estrangulamiento de recursos del servicio QA, no un bloqueo de la aplicación. Pico de 393,1 MiB con MemoryHigh=384 MiB (el kernel frena sin OOM ni error) y CPUQuota=50 %; el primer intento falló además por timeout de conexión TLS con MySQL durante el freno.
Corrección: drop-in exclusivo de QA /etc/systemd/system/dimabug-wa-pilot.service.d/80-recursos-arranque.conf (MemoryHigh=640M, MemoryMax=768M, CPUQuota=100 %, JAVA_TOOL_OPTIONS=-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k, MALLOC_ARENA_MAX=2). Sin cambios en ExecStart, timeout de arranque, DNS, certificados ni otros servicios. Hash sha256 563624e4…98d7c verificado en origen y destino.
Criterio de aceptación	Antes	Después (startup_check.py, 03:25 UTC)
Disponible en ≤180 s	No (>215 s, terminado por systemd)	✅ 40,85 s (Started BackendApplication en 37,65 s)
HTTP 200 en comprobación	No	✅ 200, estable en 3 lecturas
Memoria disponible ≥1 GiB	—	✅ mínimo 1754 MiB
Pico de memoria del servicio	393,1 MiB (sobre MemoryHigh)	376,7 MiB (bajo MemoryHigh 640 MiB)
OOM / reinicios	—	✅ ninguno / 0
JPA ddl-auto=validate	—	✅ aprobado
Staging, MySQL y Nginx	—	✅ sin cambios
Cierre	—	✅ QA detenido y cuenta wa_qa_pilot bloqueada (Y)
Observación: systemd marca "failed" al detener QA porque Java termina con 143 (SIGTERM). Es esperado en una parada solicitada; puede declararse SuccessExitStatus=143 más adelante.
No se capturaron memory.events ni cpu.stat durante esta ejecución; la confirmación del estrangulamiento es comparativa (mismo JAR y configuración: >215 s con pico sobre MemoryHigh frente a 41 s con holgura).
Decisión: GO para el piloto funcional de 20 eventos sintéticos (pendiente de autorización expresa). NO-GO todavía para despliegue permanente (DNS, HTTPS y webhook pendientes).