# Skill 4.5 — Primera recepción real desde Meta: preparación

Miércoles 18 de noviembre de 2026, America/Santiago.
**Estado: pendiente de completar datos privados y de autorización pública expresa.**
Se aplica Pareto: se reutilizan JAR, límites, imágenes, HMAC, política de activos,
persistencia, deduplicación y supervisor de 20 minutos ya probados. No se repiten
auditorías de Ubuntu/Certbot o carga prolongada. No se inicia otra etapa.

## 1. Estado comprobado y URL

- Túnel anterior wa-tunnel-a190176ef1: CLOSED por expiración el 17/11 a las
  **15:36:50 de Santiago**; informe final PASS y todos sus contenedores exited.
  Hoy no se encontró cloudflared activo; Docker confirma que no hay contenedores
  activos con la etiqueta de esa sesión. No reutilizar su URL.
- Se inició Docker Desktop localmente y se ejecutó **solo** un smoke privado de
  arranque, esquema y recepción pausada: wa-reception-prep-0f7d9f0a6b, PASS.
- Spring, MySQL exclusivo y Nginx local arrancan; GET de readiness correcto,
  POST HMAC válido -> 503 controlado con recepción pausada; cero eventos SQL.
  Sus seis contenedores quedaron detenidos después de la comprobación.
- Certificado QA existente montado en lectura; vigencia comprobada para la
  ventana. No se generó, renovó o modificó certificado alguno. cloudflared
  2026.11.1 conserva el SHA-256 ya verificado; no hubo descarga o login nuevos.
- **URL HTTPS nueva: no generada.** Crear un Quick Tunnel ya publicaría una ruta;
  se hará exclusivamente después de autorización, nunca como supuesto diagnóstico.
- No se conectó a Graph API, configuró Meta, envió WhatsApp, modificó Ubuntu,
  staging, DNS o certificados, ni se hizo commit/push/despliegue.

Evidencias: qa/runtime/wa-reception-prep-0f7d9f0a6b/informe.json y
qa/runtime/meta-preparation-20261118-08afdbe364/window-plan.json, sin secretos.
Las fuentes consultadas de Meta son documentación pública, no llamadas de integración.

## 2. Archivo privado solicitado por el usuario

Ubicación local, ACL limitada a carol y SYSTEM, fuera de Git:

`C:\Proyectos\DimaBug-MVP\qa\runtime\meta-preparation-20261118-08afdbe364\private\meta-config.json`

| Campo | Cómo completarlo localmente |
|---|---|
| app_secret | App Secret de la aplicación Meta QA que firmará los eventos. No es Access Token. Consultarlo en los ajustes básicos de esa app; no copiarlo al chat ni a capturas. |
| waba_id | ID de la cuenta WhatsApp Business QA asociada al número de prueba. |
| phone_number_id | ID técnico del número empresarial QA; no es su teléfono visible. |
| authorized_participants[].alias | Alias local ya propuesto carolina-qa o alias aprobado equivalente. |
| authorized_participants[].tel | Teléfono/wa_id del remitente autorizado, con país y solo dígitos, sin + ni espacios. |
| authorized_participants[].bsuid | Solo si es conocido/autorizado; identificador exacto BSUID, sin inferirlo del teléfono. |
| verify_token | Ya generado. Conservarlo; más adelante se copia únicamente al campo Verify token de Meta. |
| public_reception_authorized | Mantener false. Cambiar JSON no concede autorización ni publica el servicio. |

Estado inicial: INCOMPLETE; faltan App Secret, WABA, Phone Number ID y remitente.
Se necesita aplicación/número de prueba QA, no redirigir un callback productivo.
Si los activos no están listos, la recepción real permanece bloqueada.

Validación local de forma/allowlist, sin mostrar valores o conectar a Meta:

```powershell
python C:\Proyectos\DimaBug-MVP\qa\whatsapp\local-isolation\meta_config.py --check C:\Proyectos\DimaBug-MVP\qa\runtime\meta-preparation-20261118-08afdbe364\private\meta-config.json
```

COMPLETE_UNVERIFIED_META significa formato completo, **no** activos verificados
por Meta ni permiso para publicar. El archivo no necesita Access Token para que
el receptor valide eventos entrantes; una suscripción administrativa faltante
se resolvería separadamente después de autorizar, nunca con un token en el chat.

Si un evento incluye teléfono y BSUID, la política actual exige que ambos estén
explícitamente autorizados al mismo alias. Si llega un BSUID desconocido, rechazar
con 403 y revisar de forma privada; no relajar ni ampliar automáticamente la lista.
Meta puede omitir el teléfono en ciertos casos con username, por lo que BSUID no
debe deducirse. [Referencia oficial de identificadores](https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/overview).

## 3. Diferencia respecto a la prueba sintética y controles

El Quick Tunnel de ayer exigía Basic Auth adicional. Esa puerta no sirve para
el GET de verificación documentado de Meta, que usa hub.mode, hub.verify_token y
hub.challenge. La variante real preparada deja Basic fuera **solo** tras el gate
expreso de autorización y carga de la configuración privada completa.

Se mantienen: HTTPS y TLS de origen verificado, ruta/métodos limitados, rate 2/s,
burst 4, máximo 3 MiB, HMAC SHA-256 sobre bytes originales con App Secret real,
WABA/número/participantes exactos y deduplicación. GET exige el Verify Token;
POST exige firma válida antes de aceptación. No se usa no-tls-verify.
[Protocolo oficial GET/POST](https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/create-webhook-endpoint).

Solo después de autorizar se pone recepcion=true. Procesamiento, medios, envío,
envío real y conexion-meta permanecen **false**. conexion-meta=false prohíbe la
conexión saliente del runtime; no hay que ponerla true para recibir un POST Meta.
OCR, búsqueda y trabajadores están excluidos del JAR, no solo desactivados por flags.

La preparación privada usa --prepare-reception: el CLI rechaza combinarlo con un
túnel. La futura activación real requiere --real-meta-config completo, TLS QA
existente y literal --public-approval AUTORIZO_RECEPCION_REAL_META_20_MINUTOS.
Ese flag es un seguro técnico, no sustituye la autorización en la conversación.
**No se ejecutó dicha activación en esta entrega.**

Las comprobaciones previas del modo real solo hacen GET y POST inválidos que no
insertan mensajes sintéticos. Se exige SQL vacío antes de entregar URL ACTIVE:
el primer mensaje aceptado debe venir del ensayo real, no de un autodiagnóstico.
El cliente sintético habitual rechaza enviar datos en una ventana real; --stop
sigue disponible. No utilizar el comando del túnel sintético como si fuese Meta.

## 4. Duración y activación posterior, NO ejecutada

Se conserva el máximo acordado de **20 minutos**, contado desde el arranque
de cloudflared autorizado, con Timer, plazo UTC y Windows Job Object de cierre.
El tiempo no ha empezado hoy. No se crea túnel ni se publica mientras los datos
estén incompletos o falte autorización. Si TLS existente caduca, fallar cerrado;
no renovarlo o desactivar verificación automáticamente.

Tras completar el archivo: validar sin revelar valores, recibir autorización
expresa de recepción pública/primera conexión Meta y arrancar la nueva sesión.
Entonces se entregarán URL **nueva** terminada en /webhooks/whatsapp, hora exacta
de expiración y ruta/identidad de la sesión. Mantener MySQL/Spring sin puertos
públicos y solo la recepción durable. No extender el plazo automáticamente.

## 5. Instrucciones exactas para Meta — SOLO después de autorizar y tener URL ACTIVE

1. Entrar en Meta for Developers con la **aplicación QA correcta**. Ir a
   **WhatsApp > Configuration**. Para apps creadas con el caso de uso de WhatsApp:
   **Use cases > Customize > Configuration**.
2. En la configuración de webhook, editar **Callback URL** y pegar la URL nueva
   completa `https://HOST_NUEVO.trycloudflare.com/webhooks/whatsapp`, sin token
   en query. No pegar la URL expirada del 17/11 ni el placeholder de este documento.
3. En **Verify token**, copiar exactamente verify_token del archivo privado.
   Es independiente del App Secret y no es un Access Token.
4. Pulsar **Verify and save**. Este paso provoca una conexión real de Meta;
   por tanto no ejecutarlo antes de la autorización. El endpoint debe devolver
   el challenge exacto y 200. Ante error, detenerse y revisar token/URL/plazo/gateway;
   no quitar HMAC o permitir rutas adicionales para forzarlo.

Estas rutas/campos se contrastaron con la [documentación oficial de configuración](https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/create-webhook-endpoint).

5. En **Webhook fields**, suscribir **messages**. No calls, plantillas, history
   u otros campos para este ensayo. messages incluye mensajes entrantes y estados;
   la existencia de un estado por sí sola no prueba recepción de un texto nuevo.
   [Campos oficiales](https://developers.facebook.com/documentation/business-messaging/whatsapp/webhooks/overview).
6. Confirmar que esa app está suscrita a la WABA QA correcta. Verificar callback
   y campos no sustituye la asociación a la WABA. Si no está asociada, detener el
   envío del ensayo y resolverlo en una acción autorizada de esa app: consultar
   `/{WABA_ID}/subscribed_apps` y, si corresponde, suscribirla mediante ese recurso,
   con credenciales del operador custodiadas y permisos que requiera Meta.
   No ejecutar llamadas ni obtener/pegar Access Tokens en esta preparación.
   [Referencia oficial de la suscripción WABA](https://developers.facebook.com/documentation/business-messaging/whatsapp/reference/whatsapp-business-account/subscribed-apps-api).
7. No cambiar la app a Live, reemplazar callbacks productivos o desactivar mTLS
   existente para sortear un bloqueo. Usar activos de prueba y permisos/roles del
   entorno QA. Si exige mTLS u otra condición no cubierta, parar: no se modifica
   infraestructura/certificados bajo esta autorización de preparación.
8. Desde WhatsApp del **remitente autorizado**, enviar **un solo texto inocuo** al
   teléfono de prueba visible en API Setup (no al Phone Number ID):
   `DIMABUG QA RECEPCION 2026-11-18 01`.
   No imágenes, información personal, errores de clientes o datos reales de negocio.
   No usar el botón de envío API ni activar respuestas automáticas de DimaBug.

Los pasos anteriores son un procedimiento futuro, no acciones realizadas. La URL
pública de Cloudflare presenta certificado válido a Meta; el certificado QA de
origen se verifica localmente y no se carga en Meta ni requiere renovar Ubuntu.

## 6. Cómo se verificará persistencia, sin mostrar el mensaje

Antes de abrir la ventana: base nueva/local QA, cero eventos y cero respuestas.
Después del texto: correlacionar la hora de envío y la entrega Meta con el 200
del gateway y una nueva fila tipo TEXTO en PENDIENTE, intentos=0 y contenido cifrado
no vacío. No confundir ESTADO/IGNORADO con el mensaje. Cifrado en reposo y HMAC
de identidad conservados; no volcar texto, teléfono, wamid, claves o payload a logs.

Consultas previstas, solo sobre la base QA de esa sesión mediante opciones de
conexión privadas, nunca contraseña en argumentos:

```sql
SELECT tipo, estado, COUNT(*) AS eventos,
       SUM(OCTET_LENGTH(contenido_cifrado) > 0) AS con_contenido_cifrado,
       MIN(recibido_en) AS primero, MAX(recibido_en) AS ultimo
FROM wa_evento GROUP BY tipo, estado;
SELECT COUNT(*) AS respuestas FROM wa_respuesta;
```

Se espera una entrada TEXTO pendiente y cero respuestas. Si Meta reentrega el
mismo evento, no debe añadirse otra fila; reenviar el texto manualmente por
WhatsApp crea un mensaje/ID nuevo y no es una prueba válida de duplicado.
La deduplicación local ya aprobada no se vuelve a auditar sin una causa concreta.
La primera recepción real solo se declarará verificada con esa evidencia;
validar GET o recibir un POST de prueba sintético no la reemplaza.

## 7. Cierre y verificación posterior

El supervisor cierra cloudflared al vencer el máximo, luego detiene solo sus
gateways, Spring y MySQL. Conserva volumen/cifrado y claves bajo custodia privada
para auditoría; no hace prune ni altera otros servicios.

Cierre anticipado de la futura sesión, en PowerShell local, con su ID nuevo:

```powershell
python C:\Proyectos\DimaBug-MVP\qa\whatsapp\local-isolation\tunnel_client.py --run C:\Proyectos\DimaBug-MVP\qa\runtime\wa-tunnel-ID_NUEVO --stop
```

Comprobar CLOSED, motivo/hora, proceso conector terminado, contenedores propios
exited y ausencia de listeners del gateway temporal. El informe final debe conservar
conteos y resultado saneado; no dar por cerrado solo porque llegó la hora prevista.

Después de la prueba, el operador debe retirar/restaurar **solo la suscripción
QA temporal** en Meta según lo autorizado; cerrar el túnel no borra esa configuración
ni evita reintentos de Meta. No tocar otra app/WABA. Si no se autorizó esa acción
administrativa, pedirla y dejar la obligación explícita, sin inventar que ya ocurrió.

## 8. Validación ejecutada y estado de Skill

- Revisión acotada del cierre anterior y verificación del binario existente.
- Smoke privado PASS, cero eventos, recepción pausada y contenedores detenidos.
- **7 pruebas nuevas correctas** de configuración/gates y **5 pruebas del túnel
  correctas** tras adaptar el modo futuro. Casos HTTP unitarios simulados, sin Meta.
- No se ejecutó el modo de recepción real, llamadas Graph, nueva URL pública o
  medición de 24 horas. No se modificaron backend general ni base remota.

**Estado Skill 4.5: primera recepción real desde Meta, pendiente de autorización.**
Preparación local realizada; faltan completar/validar el archivo privado y la
autorización expresa para abrir la ventana. No se avanza automáticamente.

## Actualización: archivo completado por el usuario

Tras corregir el campo tel, la relectura privada devuelve **COMPLETE_UNVERIFIED_META**:
sin campos obligatorios vacíos o inválidos y con un participante autorizado.
No se muestran valores, teléfonos ni claves. Esta clasificación valida estructura;
la autenticidad operativa de App Secret/activos se comprobará con la entrega real,
no mediante llamadas Meta no autorizadas.

Comprobaciones de preparación realizadas:

- meta-config.json ignorado por Git y no versionado.
- ACL del archivo restringida a propietario, administradores locales y SYSTEM,
  sin permisos generales a usuarios/everyone en las entradas observadas.
- 17 logs QA de las sesiones previas revisados contra los valores actuales:
  cero archivos con coincidencias de sus secretos/identificadores. Alcance local
  concreto, no certificación universal de todos los registros del proyecto.
- Allowlist exacta con un participante; sin ampliar identidades o aplicar cambios
  de normalización automáticos. Si Meta incorpora un BSUID desconocido, sigue
  sujeto a la misma política de rechazo y autorización explícita.
- Mapeo de propiedades mantiene recepción/procesamiento/medios/envío/envío real/
  conexión saliente Meta false hasta activación autorizada. OCR/búsqueda/trabajadores
  siguen excluidos del JAR, cuyo hash continúa coincidiendo con el probado.
- Túnel previo CLOSED/expired, sin cloudflared activo ni contenedores activos de
  esa sesión o del smoke privado. No se repitió el arranque o auditorías aprobadas.

**Configuración: COMPLETA en estructura. Seguridad: APROBADA para preparación
local. Túnel: PREPARADO, sin publicar.** La URL nueva permanece null y la duración
prevista es 20 minutos desde el futuro arranque autorizado. "Listo" se trató como
corrección del archivo, sin inferir permiso para iniciar la ventana.

Siguiente acción exclusivamente tras autorización expresa: activar solo recepción
durable con la configuración privada, abrir el túnel compatible con Meta, entregar
URL/plazo y seguir las secciones 5–7. No hubo conexión Meta, publicación, modificación
de Ubuntu o extensión automática de etapa en esta actualización.
