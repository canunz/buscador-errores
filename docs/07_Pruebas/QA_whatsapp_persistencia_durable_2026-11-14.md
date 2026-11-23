# QA de persistencia durable WhatsApp — 2026-11-14

## Resultado final

Implementación local validada, con salida simulada y arquitectura hexagonal.
No se conectó con Meta, no se desplegó y no se realizaron commits. No se modificó
el frontend ni se aplicaron tablas a bases existentes de DimaBug.

| Ejecución | Ejecutadas y correctas | Fallos/errores | Omisiones |
|---|---:|---:|---:|
| Regresión H2, HTTP, OCR real, búsqueda pública y arquitectura | 143 | 0 | 2 |
| Persistencia JPA sobre MySQL 8.0.46 exclusivo | 17 | 0 | 2 |
| Primera JVM: reserva persistida para reinicio | 1 | 0 | 0 |
| Segunda JVM tras reiniciar MySQL: recuperación | 1 | 0 | 0 |

Las dos omisiones corresponden exclusivamente a métodos de reinicio habilitados
por variable de entorno. Se ejecutaron individualmente y pasaron en sus fases
MySQL correspondientes. No se ejecutó la suite completa de otros módulos del
backend ni Angular: el alcance fue WhatsApp y la búsqueda pública reutilizada.

## Cobertura

- Firma original inválida sin efectos, validación completa antes de persistir,
  ACK durable sin esperar OCR y deduplicación HTTP.
- Recepción concurrente de un mismo evento, colisiones de ID con contenido
  diferente y reversión de un lote completo.
- Reserva exclusiva, orden por conversación, independencia entre conversaciones,
  recuperación de reservas vencidas y rechazo del trabajador con cercado antiguo.
- Conversación reconstruida desde persistencia, corrección con referencia nueva,
  cancelación sin búsqueda y consumo único de confirmación con distintos mensajes.
- Nueva imagen mientras existe OCR en curso: resultado anterior descartado.
- Reintentos hasta máximo, sin respuesta prematura, errores de preparación y
  ausencia de reenvío automático cuando un envío queda incierto.
- Atomicidad conversación/evento/respuesta ante una restricción de salida.
- Cifrado autenticado, rechazo de contexto/carga alterados y contenido corrupto
  que agota intentos sin bloquear indefinidamente la conversación o su salida.
- Flujo integral con trabajadores reales, OCR Node/Tesseract local real, servicio
  público de búsqueda, confirmación y salida simulada. Exclusión de borradores,
  soluciones inactivas o sin autoservicio, campos internos y soluciones revocadas
  antes de preparar una respuesta.
- Comprobación de que fuera del perfil durable no aparecen el almacén ni las
  tres tablas. Reglas ArchUnit de dominio y aplicación sin infraestructura.

## Evidencias

Regresión final:

- `qa/runtime/whatsapp-durable-regresion-cierre/maven-test.log`
- `qa/runtime/whatsapp-durable-regresion-cierre/junit/` (14 informes XML).
- `qa/runtime/whatsapp-durable-regresion-cierre/manifiesto-fuentes.json` (44
  fuentes/configuraciones/lanzadores con SHA-256, verificados sin cambios al cierre).

MySQL final:

- `qa/runtime/dimabug-wa-jpa-69aae2490e/reintento-9672fe93/informe.json`
- Subcarpetas `jpa-mysql`, `antes-reinicio`, `despues-reinicio`: log Maven e informes
  JUnit de cada JVM. Todos finalizaron con código 0.
- Contenedor `dimabug-wa-jpa-69aae2490e`, base `wa_durable_69aae2490e`, volumen
  `dimabug-wa-jpa-69aae2490e-data`. Tres tablas exclusivamente, usuario de aplicación
  limitado a DML, puerto publicado solo en loopback, 2 CPU y 2 GiB.
- Imagen local fijada: `sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b`.
- SHA-256 del SQL aprobado sin modificaciones:
  `2e931e9fc34024c2944373905835b282311daeae85d2bf22b2072adf46a9b415`.

Los tres contenedores creados por esta tarea quedaron detenidos con salida 0.
Los dos primeros conservan evidencias de incidencias del lanzador; no se borraron
sus volúmenes. No se iniciaron ni modificaron contenedores anteriores de DimaBug.

## Incidencias corregidas

Las validaciones detectaron y corrigieron diferencias JPA de tipos BINARY/CHAR/
DATETIME, traducción de excepciones de conflicto por Spring, interpretación del
reloj SQL con zona JVM, y el puerto efímero que Docker reasigna al reiniciar.
Se corrigieron también el entorno mínimo/ruta del lanzador y los reintentos de
cargas cifradas ilegibles. Las evidencias anteriores se conservan; la referencia
válida de cierre es la indicada arriba.

## Archivos y alcance del cambio

- Nuevos contratos: `EstadoConversacion`, `AlmacenWhatsAppPort`,
  `PrepararRespuestaPublicaPort` y `ProcesarReservaWhatsAppService`.
- Reutilización de `ProcesarMensajeService` mediante restauración/instantánea;
  no se copian reglas de extracción, comandos o búsqueda.
- Nuevo paquete `whatsapp/infrastructure/persistence`: tres entidades, tres
  repositorios, adaptador transaccional conjunto, cifrado, configuración,
  trabajadores y guardián del perfil.
- Nuevo `PrepararRespuestaPublicaAdapter`, registro `META-INF/whatsapp.orm.xml`
  y perfil `application-qa-whatsapp-durable.properties`.
- Integración optativa en `EntradaWebhookHttpAdapter` y `WebhookHttpLocalConfig`.
  El controlador y sus filtros de seguridad mantienen ruta/verificación/firma.
- Nuevos `PersistenciaWhatsAppTest`, `WebhookDurableIntegracionTest`, y comprobación
  de ausencia de tablas/perfil en `BusquedaPublicaCanalTest`.
- Lanzadores `Run-PruebasDurables.ps1`, `Run-PruebasPersistenciaMySql.py` y guía
  `docs/implementacion/WhatsApp_persistencia_durable.md`.

Se revisó Git en la rama `integracion-final-uat` y se preservó el trabajo previo.
Los 55 archivos ya modificados que Git seguía previamente mantienen ese estado;
el módulo WhatsApp y los nuevos archivos continúan sin seguimiento como antes.

## Límites y trabajo posterior

Solo se habilita QA local explícito. El guardián no permite usar MySQL de staging,
producción ni una base que no sea exclusiva de estas pruebas. Para otra base se
requiere una migración aprobada y revisión de configuración. Siguen pendientes
el adaptador de descarga/envío Meta, persistencia/correlación de estados de
entrega, rotación de claves, alarmas operativas y QA con medios reales. Los
webhooks de estados se validan y contabilizan, sin persistirlos ni procesarlos
como mensajes. No se promete envío real ni ejecución física exactamente una vez.
