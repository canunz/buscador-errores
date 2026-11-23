# QA — Meta, preparación local 3.2-A

## Resultado

Implementación y validación exclusivamente locales. No se conectó Meta, no se
utilizaron credenciales reales, no se descargaron medios, no se enviaron mensajes,
no se crearon túneles ni se publicaron puertos. No hubo despliegues ni commits.

| Ejecución | Total declarado | Ejecutadas correctas | Omitidas | Fallos/errores |
|---|---:|---:|---:|---:|
| Regresión WhatsApp, OCR y búsqueda pública | 184 | 182 | 2 | 0 |
| Cierre de configuración y privacidad con DEBUG | 15 | 15 | 0 | 0 |

La segunda ejecución repite casos de la primera con aserciones reforzadas; no
son 197 casos distintos. Las dos omisiones son métodos históricos exclusivos
del lanzador de reinicios MySQL, deshabilitados en H2. No se ejecutó Docker ni
MySQL en esta etapa; tampoco la suite completa de módulos ajenos ni Angular.

## Evidencias

- `qa/runtime/meta32a/baseline.json`: hashes de 1.188 archivos previos y estado Git.
- `qa/runtime/meta32a/primeras-pruebas/`: primera validación, 26 casos correctos.
- `qa/runtime/meta32a/regresion/maven-test.log` y `junit/`: 18 informes de regresión.
- `qa/runtime/meta32a/cierre/maven-test.log` y `junit/`: cierre de 15 casos.
- `qa/runtime/meta32a/manifiesto-final.json`: fuentes y documentación con SHA-256.

El SQL `qa/whatsapp/mysql-stage22a/schema.sql` conserva su hash original. La
comparación con el baseline no encontró cambios de archivos previos fuera del
módulo WhatsApp y sus pruebas. No se alteraron los cambios locales anteriores,
el frontend, el núcleo OCR, el buscador, autenticación ni configuraciones de
staging. Git continúa en `integracion-final-uat`, con trabajo sin commit.

## Casos nuevos verificados

- Ausencia del almacén durable: fallo al construir el contexto Meta QA; no hay
  procesador alternativo en memoria.
- WABA, número receptor, participante desconocido y alias duales discordantes:
  rechazo antes de efectos persistentes; validación completa del lote.
- Formato por teléfono, BSUID y combinación de ambos con identidad canónica
  explícita. Continuidad de conversación entre imagen por teléfono y confirmación
  por BSUID.
- Firma sobre bytes originales, firma inválida antes de parsear o revisar pausa,
  claves duplicadas, JSON adicional, tamaño excesivo y errores controlados.
- Textos de 4.096 caracteres y lotes mayores de diez eventos; recepción durable
  sin relajar el máximo de 300 de la consulta. Texto largo no dispara búsqueda.
- Recepción de referencias de imágenes sin descargarlas y tipos desconocidos
  sin interpretar adjuntos como consultas.
- Lotes mixtos de mensajes y estados en una operación atómica, estados cifrados,
  deduplicación, reversión del lote por conflicto y purga con conversación nula.
- Estados fuera de los trabajadores de consultas: no OCR ni respuestas.
- Cuatro valores iniciales apagados comprobados al arrancar; recepción pausada
  devuelve 503 sin guardar; procesamiento, medios y salida simulada independientes.
- Imágenes pausadas permanecen pendientes; al habilitar medios se ejercita el
  OCR simulado de la prueba. El OCR real local sigue cubierto por la regresión.
- Mantenimiento operativo con todos los controles apagados y métrica ante fallo.
- Fallo **simulado** del almacén: HTTP 503, sin ACK falso ni información SQL.
- GET exacto, métodos no permitidos, remoto bloqueado aunque envíe Forwarded y
  protección de endpoints ajenos al webhook.
- Perfiles incompatibles, bind no local, Forwarded, base no autorizada o DDL
  peligroso: rechazo del guardián. `envio-real=true` y `conexion-meta=true` fallan.
- Ausencia de teléfonos, IDs, textos, firmas, tokens y secretos en logs capturados,
  incluso con DispatcherServlet en DEBUG; métricas de cardinalidad cerrada.
- Las reglas ArchUnit y los filtros publicados/activo/autoservicio anteriores
  continúan pasando.

## Cambios revisables

Se añadieron `EstadoEntregaCanal`, `ControlProcesamientoWhatsAppPort`, el paquete
`whatsapp/infrastructure/meta`, `PerfilesWhatsApp`, el perfil de propiedades y
cuatro clases de prueba nuevas. Se ampliaron los contratos de recepción y las
pruebas de persistencia. El registro JPA y los repositorios se habilitan también
en el perfil nuevo; se conserva el SQL existente.

Se reutilizan HMAC y cadena loopback; el límite del webhook anterior sigue siendo
16 KiB. Los trabajadores reciben los nuevos controles y métricas y distinguen
reservas por token/cercado. Se conserva la salida simulada; ningún cambio incluye
un cliente Graph API ni un adaptador de envío o descarga real.

Guía de configuración, interruptores, retención y límites:
`docs/implementacion/WhatsApp_Meta_QA_etapa_3_2_A.md`.

## Límites pendientes

La fase no valida recepción desde Meta, redes públicas, TLS, permisos efectivos,
cuentas, entrega real, descarga de medios ni capacidad productiva. Las pruebas
de caída del almacén son sintéticas, no una caída real de MySQL.

Los estados se retienen sin correlación con envíos. El destino y contexto para
una futura salida real, ventanas/plantillas, rotación de claves, exportadores de
métricas y controles operativos externos siguen pendientes. Los contadores y
controles actuales son locales a cada JVM; no existe endpoint administrativo.
Pausar conserva registros, pero no congela sus vencimientos. Todo ello requiere
una fase y autorización posteriores.
