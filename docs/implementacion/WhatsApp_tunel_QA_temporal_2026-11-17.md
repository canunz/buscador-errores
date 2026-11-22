# WhatsApp QA — Quick Tunnel HTTPS temporal

Fecha: 17 de noviembre de 2026. Opción A solicitada y preferencias confirmadas
por el usuario: **Cloudflare Quick Tunnel, máximo 20 minutos, solo eventos
sintéticos y sin conexión con Meta**. No es despliegue en Ubuntu ni autorización
de certificados/DNS, envío WhatsApp o recepción de datos reales.

## Alcance y proveedor

Quick Tunnels crea un hostname aleatorio trycloudflare.com sin cuenta/dominio
propio; es una herramienta de pruebas, no el despliegue productivo propuesto.
Referencia: [documentación oficial](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).
No se ejecuta login, se usa token Cloudflare del usuario o se instala un servicio.

Herramienta local: cloudflared Windows **2026.11.1**, obtenida del release oficial
GitHub y verificada contra su digest publicado:
`341deda3db5ec498a0482254d6c38c0b7cf01a30c37bb5dd95d2216d08955e35`.
Binario bajo qa/runtime/tools, ignorado por Git. No hay nuevas dependencias del
backend/frontend ni instalaciones en Ubuntu. Perfil cloudflared privado por ejecución.

## Recorrido y controles

Cliente HTTPS público -> Cloudflare -> cloudflared local -> HTTPS 127.0.0.1
en puerto aleatorio -> Nginx frontal -> HTTPS verificado Nginx interno ->
Spring **127.0.0.1:8082** -> MySQL QA exclusivo. El frontend Nginx es un proxy
separado del namespace backend; solo su TLS se publica en loopback del host.
MySQL y Spring no publican puertos y permanecen en red Docker interna.

- Autenticación Basic adicional, con contraseña aleatoria temporal y hash SHA-512
  crypt. Es del gateway de ensayo; no cambia autenticación de DimaBug.
- HMAC SHA-256 sobre los bytes originales, secreto nuevo sintético. Sin Basic
  no se alcanza el receptor; con Basic pero sin HMAC válido no se acepta un POST.
- GET challenge requiere además verify_token. Ningún secreto en URL compartida,
  argumentos del cliente, salida pública o documentos.
- Solo GET/POST a /webhooks/whatsapp; API/Actuator/Swagger y otras rutas cerradas.
  Rate 2/s, burst 4, ocho conexiones, cuerpo 3 MiB y timeouts conservados.
- Gate de X-Forwarded-Proto https antes de autenticación. Cloudflare termina el
  TLS público y puede observar las solicitudes; solo se usa contenido/credenciales
  sintéticos. No se afirma cifrado que oculte el contenido al proveedor.
- Origen y salto entre gateways verifican CA/SAN/nombre; no se usa no-tls-verify.
  [Parámetros oficiales de origen](https://developers.cloudflare.com/tunnel/reference/origin-parameters/).
- Authorization se elimina antes de Spring; firma y cuerpo se preservan. Logs
  técnicos sin query, cuerpos o credenciales; cloudflared en INFO, nunca DEBUG.
- No se reusa configuración/cuenta cloudflared real del usuario. Métricas del
  conector solo en loopback; sin rutas de diagnóstico de gestión habilitadas.
- El JAR sigue excluyendo OCR, búsqueda, trabajadores y envío. QA WABA/número y
  participantes son ficticios; la única actividad de negocio es recepción durable.

La red del gateway frontal no tiene el mismo bloqueo de salida que la del backend.
Su configuración de solo lectura y upstream fijo delimitan el proxy; no se declara
implementada la política de egress Ubuntu pendiente en C.5. Basic en esta prueba
no presupone compatibilidad con webhooks reales de Meta: eso requiere otra etapa.

## Activación segura y duración

Se comprueban **antes de crear URL pública**: nginx -t, puertos publicados exactos,
red backend interna, TLS de origen, Basic requerido, API interna bloqueada y
challenge local. Tras conectar se comprueban en el hostname público Basic, HMAC,
alteración de cuerpo, firma ausente y duplicados concurrentes; se contrasta una
fila SQL. Solo entonces tunnel-status.json pasa a ACTIVE.

El supervisor corre oculto en Windows, con carpeta privada y sin tarea programada.
El máximo de 20 minutos se cuenta desde que arranca cloudflared, no desde que el
usuario abre el enlace. Timer y comprobación del reloj cierran el conector al
vencer; un Windows Job Object KILL_ON_JOB_CLOSE lo termina también si cae el
supervisor. La terminación del conector corta la publicación antes de la limpieza
de contenedores. Cinco tests locales verificaron controles del cliente, comando
TLS y cierre real de un hijo de prueba sin red al cerrar su Job Object.

Una disponibilidad STARTING o cierre por fallo no es una prueba pública aprobada.
Estado/plazo/URL y resultado: qa/runtime/wa-tunnel-ID/tunnel-status.json.
Pruebas: tunnel-public-tests.json. Tras terminar: informe.json y estados de recursos.
No se afirma cierre ya observado cuando la sesión todavía está ACTIVE.

## Uso sin mostrar secretos

Desde PowerShell en el proyecto, sustituyendo ID por la ejecución ACTIVE:

```powershell
python qa/whatsapp/local-isolation/tunnel_client.py --run qa/runtime/wa-tunnel-ID --self-test
```

El cliente lee las credenciales privadas, añade Basic/HMAC y solo imprime estados,
conteos y latencia. Sin --self-test envía un único mensaje sintético. No recibe
imágenes, números reales o tokens como argumentos. La URL en navegador por sí sola
no confirma el challenge: el gateway exige autenticación.

Cierre anticipado, sin tocar procesos ajenos:

```powershell
python qa/whatsapp/local-isolation/tunnel_client.py --run qa/runtime/wa-tunnel-ID --stop
```

El marcador solicita cierre al supervisor. No es un kill global de Python/Docker.
Credenciales en private/client.json y logs crudos bajo ACL usuario/SYSTEM; no
pegarlos al chat ni al repositorio. Volúmenes, claves y redes de prueba se conservan
para auditoría; no hay limpieza global o borrado de bases existentes. Credenciales
generadas no se reusan en otra ejecución. La vigencia efectiva termina al cerrar
el gateway/conector; custodiar y retirar artefactos privados según revisión posterior.

## Incidencias y validación de esta preparación

- Se separó el frontal al no ser accesible desde Windows el publish sobre red
  Docker interna. No se abrió MySQL/Spring para resolverlo.
- Se diagnosticó localmente autenticación con credencial ficticia y se usa
  OpenSSL nativo/entrada binaria para generar la credencial. No se debilitó Basic.
- Un conector registrado no demostró disponibilidad pública dentro de la primera
  espera; se cerró. La espera de propagación ahora es acotada y registra solo
  códigos/tipos de error, manteniendo la ventana máxima de 20 minutos.
- **23 pruebas locales correctas** (18 de preparación previa y cinco del túnel).
  No se ejecutó baseline de 24 h, modificó Certbot, creó DNS o tocó Ubuntu.

Los resultados de la URL activa se registrarán aparte, sin secretos y sin tomar
los intentos fallidos como aprobados. No se entrega un enlace como aprobado hasta
pasar las comprobaciones; si falla un control, el supervisor cierra el conector.

## Resultado observado de la sesión autorizada

Ejecución **wa-tunnel-a190176ef1**, estado **ACTIVE** al verificar este informe.
URL y estado en `qa/runtime/wa-tunnel-a190176ef1/tunnel-status.json`; no hay tokens
en ese archivo. Cierre máximo: **17/11/2026 15:36:50 America/Santiago**
(18:36:50 UTC), contado desde el arranque de cloudflared. No se declara cierre
ya observado: el supervisor sigue prestando la ventana temporal al usuario.

| Caso público probado | Resultado |
|---|---|
| Webhook sin Basic | 401 |
| API, Actuator, Swagger y ruta codificada del ensayo | 403 |
| Challenge con credenciales sintéticas | 200, conserva exactamente 000123 |
| Cuerpo alterado con firma anterior | 403 |
| POST sin firma | 403 |
| Cuatro envíos concurrentes del mismo evento | Una aceptación, tres duplicados; una fila SQL contrastada |
| HTTP claro sin credenciales, incluso con X-Forwarded-Proto enviado por el cliente | 301 hacia HTTPS del mismo hostname; no se siguió redirect con secretos |
| Cliente PowerShell/Python de prueba, mensaje nuevo | 200, una aceptación, 656 ms |
| Auditoría durante ACTIVE | Cero coincidencias de secretos/marcadores en siete streams (contenedores y cloudflared), stdout y stderr |

Las cuatro respuestas del grupo duplicado tardaron 2406–2594 ms. Son muestras
de un ensayo público, no p95/p99 ni prueba de capacidad prolongada. Los registros
de resultado saneados están en tunnel-public-tests.json, public-http-check.json
y public-log-privacy.json. No se enviaron eventos Meta, imágenes o datos reales.

La espera inicial presentó errores de resolución del hostname recién creado;
con la espera acotada posterior quedó disponible y se completaron las pruebas.
No se cambió DNS de Windows/Ubuntu/Cloudflare ni se deshabilitó validación TLS.
Los intentos anteriores terminaron cerrados y sus contenedores detenidos; no
equivalen a ejecuciones públicas aprobadas. Un diagnóstico de DNS público separado
no completó su consulta, y no se usó como prueba de disponibilidad ni como bypass.

La red del backend y sus puertos siguen sin publicación. Solo el HTTPS del
gateway frontal se publica en 127.0.0.1 para el conector. Basic no es autenticación
de usuarios DimaBug y sus credenciales son exclusivas de esta sesión. El cliente
lee private/client.json sin mostrarlo; no compartir ese archivo.

Después del plazo, comprobar CLOSED y el informe final si se necesita evidencia
del cierre efectivo. Para cierre anticipado, usar el cliente con --stop. Se
conserva el gate NO APTO de C.5 para desplegar en Ubuntu: este experimento local
no lo cierra ni autoriza otro túnel, extensión de plazo o conexión con Meta.

## Prueba manual del usuario desde PowerShell

El usuario ejecutó tunnel_client.py --self-test para la ejecución
wa-tunnel-a190176ef1 y compartió este resultado saneado:

- Sin autenticación: **401**; rutas internas: **403**.
- Challenge exacto: true; cuerpo alterado y firma ausente: **403**.
- Cuatro envíos del mismo evento: **una aceptación y tres duplicados**.
- Latencias individuales: **407, 360, 437 y 359 ms** (rango 359–437 ms).

Es una verificación manual adicional sobre la URL pública y el cliente local,
no un percentil ni un ensayo sostenido de capacidad. No amplía el plazo del
túnel ni autoriza Meta/despliegue. Se conserva el cierre automático original.
