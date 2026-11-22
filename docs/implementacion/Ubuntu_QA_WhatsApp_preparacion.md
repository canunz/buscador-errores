# Ubuntu QA — diseño previo a ejecución

## Estado y alcance

**VERIFICADO localmente:** rama `integracion-final-uat`; se revisó Git y se creó
un baseline de 1.214 archivos previos. Se conservan todos sus cambios. Esta entrega
añade solo plantillas, scripts preparados y documentación: no instala, conecta,
inicia ni modifica servicios Ubuntu, DNS, certificados o bases existentes.

**PROPUESTO:** servicio propio para recepción QA y vhost exclusivo de
`wa-qa.dimabug.casol.cl`, nombre suministrado para esta etapa. No se comprobó DNS
ni que ese nombre esté actualmente configurado en Nginx o en un certificado.

**PENDIENTE crítico:** identificar el Certbot activo mediante inspección Ubuntu
de solo lectura expresamente autorizada. Los antecedentes locales describen
systemd y Nginx de staging, pero no demuestran si Certbot es APT, Snap o virtualenv.
No es correcto deducirlo de Ubuntu, de la ruta del certificado o de `which certbot`.
Se mantiene la restricción anterior de no conectarse al servidor.

## Servicio Spring independiente — PROPUESTO

No reutilizar `dimabug@staging.service`, su usuario, JAR, EnvironmentFile,
secretos, directorios o puerto 8081. El informe histórico local de staging
menciona esos recursos; no se asume que su estado remoto siga siendo el mismo.

| Recurso | Diseño QA; comprobar disponibilidad antes de instalar |
|---|---|
| Unidad | `dimabug-wa-qa.service` |
| Usuario/grupo | `dimabug-wa-qa`, sin login y sin pertenecer a grupos productivos |
| Artefacto | `/opt/dimabug-wa-qa/releases/<release>/whatsapp-qa.jar` |
| Selector de versión | `/opt/dimabug-wa-qa/releases/current` |
| Estado/runtime | `/var/lib/dimabug-wa-qa`, `/run/dimabug-wa-qa` |
| Configuración | `/etc/dimabug-wa-qa/application.properties`, root y sin secretos |
| Secretos | `LoadCredential`, ficheros root 0600, directorio root 0700 |
| Escucha | `127.0.0.1:18132`, sin confiar en Forwarded |
| Presupuesto | 1 CPU, MemoryHigh 768 MiB, MemoryMax 1 GiB, 128 tareas, 4096 FDs |
| JVM inicial | Heap 128–384 MiB y límites adicionales; sin OCR ni procesamiento |
| Salida de red | Solo loopback mediante política systemd, a validar en el host |

La plantilla requiere un marcador `/etc/dimabug-wa-qa/APPROVED` y un artefacto
existente; no se crean en esta etapa. Incluye reinicios acotados, apagado gradual,
aislamiento del sistema de archivos y grupos de control. No se habilita
MemoryDenyWriteExecute porque puede interferir con el JIT de Java.

**El JAR general actual no es el artefacto propuesto.** Su arranque descubre las
entidades de otros módulos: `ddl-auto=validate` contra tres tablas de WhatsApp no
es suficiente. Tampoco basta con apagar Gmail y automatizaciones para excluir
sus beans y dependencias. No se debe resolver copiando credenciales productivas,
creando tablas por Hibernate ni conectando el proceso a la base de staging.

Antes de activar hay que aprobar e implementar un bootstrap/artefacto QA que
registre solo recepción Meta, seguridad, controles, cifrado y persistencia
WhatsApp. Inicialmente no debe registrar trabajadores de búsqueda/OCR/envío.
Las pruebas locales de la etapa anterior demuestran ese contexto mínimo, no
proporcionan automáticamente un JAR Ubuntu desplegable. Esta entrega no modifica
código productivo para crearlo.

Para habilitar procesamiento posteriormente será necesario resolver el catálogo
de conocimientos QA con datos sintéticos/autorizados, manteniendo la búsqueda
pública. El adaptador OCR actual fija una ruta Node Windows; su portabilidad
Linux queda pendiente y no se invoca con medios/procesamiento apagados.

Todos los controles permanecen falsos: recepción, procesamiento, medios,
despacho simulado, envío real y conexión Meta. El guardián actual rechaza activar
los dos últimos. No modificarlo como atajo de despliegue.

## MySQL aislado — PROPUESTO, sin modificar bases existentes

Se propone una **segunda instancia** propia, no una nueva base dentro del daemon
productivo. Usuario `dimabug-wa-mysql`, unidad `dimabug-wa-qa-mysql.service`, datos
en `/var/lib/dimabug-wa-qa-mysql`, socket/runtime propios y puerto loopback 13328.
No usar `/var/lib/mysql`, el socket global ni la unidad `mysql.service` existente.
La opción contenedor exige una revisión separada si Docker está autorizado en
Ubuntu; no se presupone su instalación.

- 1 CPU y 1 GiB, buffer pool inicial 256 MiB, máximo 16 conexiones; pool Spring 4.
- Elegir versión soportada —candidata 8.4 LTS— y binario/digest exacto después de
  inventario y pruebas de compatibilidad. Los antecedentes locales se validaron
  con 8.0.46; eso no demuestra automáticamente compatibilidad con otra versión.
- Inicializar exclusivamente el datadir NUEVO y crear una base QA nueva, por
  ejemplo `wa_durable_ab32c001`, compatible con el guardián actual. Su nombre es
  propuesto y no acredita existencia. Nunca reutilizar una base con datos previos.
- Aplicar el SQL revisado de las tres tablas exclusivamente a esa nueva instancia,
  después de aprobación explícita. La aplicación usa `validate`, sin migraciones
  automáticas ni permiso DDL.
- Cuenta de aplicación `wa_qa_app`, restringida al host local y a DML en las tres
  tablas. Cuenta separada del auditor con SELECT; ningún permiso sobre staging.
- Credenciales nuevas y exclusivas; no importar dumps o filas productivos.
- TLS interno requerido en la plantilla: CA y certificado con SAN adecuado para
  127.0.0.1 y truststore de la JVM, con VERIFY_IDENTITY. Verificar permisos y
  compatibilidad; no cambiar a confianza universal para obtener conexión.
- Revisar espacio para datos, índices, colas y retención antes de activar. Un
  límite por conversación no limita por sí solo el crecimiento global.

Las plantillas no inicializan ni arrancan MySQL. `After=`/`Wants=` tampoco
garantizan disponibilidad SQL: el arranque y la vigilancia deben detectar que
la conexión o el esquema fallan. No devolver ACK durable si MySQL no está listo.

## Nginx exclusivo — PROPUESTO

`qa/whatsapp/ubuntu/nginx-wa-qa.conf.template` es un bloque para contexto **http**,
con zonas/mapa/log únicos y server_name `wa-qa.dimabug.casol.cl`. No es un archivo
listo para instalar: requiere rutas TLS reales aprobadas y `nginx -t` con la
versión y configuración del host. No se recargó Nginx.

Para las pruebas escucha únicamente **127.0.0.1:18443**. Abrir una escucha
pública 443 es otro cambio que requiere autorización, evaluación DNS/firewall y
comprobación de que no afecta a otros virtual hosts. No sustituir el site de
staging ni copiar su `location /` o su proxy `/api`.

La plantilla:

- Solo permite GET/POST de la ruta cruda exacta `/webhooks/whatsapp`.
- Rechaza API, Actuator, Swagger, slash final, rutas codificadas y otros métodos.
- Preserva query de verificación y cuerpo original para HMAC; no reescribe JSON,
  no añade redirecciones, no reintenta el upstream ni intercepta sus errores.
- Elimina Authorization, Cookie, Forwarded y X-Forwarded-* del cliente; Spring
  conserva su propia comprobación de firma y alcance, sin depender de esas IP.
- Máximo 3 MiB, 16 conexiones por vhost, 10 solicitudes/s con ráfaga 20, límites
  de lectura/conexión/espera. Revisar el dimensionamiento con tráfico de QA.
- Access log únicamente con estado, bytes y duración. No registra IP, URI,
  request line, query, cuerpos ni cabeceras. El error log del vhost se descarta
  para evitar que errores upstream incluyan el Verify Token en la URL.

**PENDIENTE:** inspeccionar también error logs globales/default-server, auditorías
del sistema y cualquier balanceador/CDN antes de afirmar privacidad de extremo
a extremo. Descartar el error log se compensa con métricas y alertas por estados;
no habilitar debug en producción para diagnosticar un token.

Nginx puede bufferizar cuerpos a disco: preparar un directorio NUEVO y privado
`/var/lib/dimabug-wa-qa-nginx/body`, propiedad del usuario worker real del host,
con capacidad acotada y política de restos tras caídas. No utilizar un directorio
de staging ni ejecutar limpieza sobre una ruta calculada sin verificación.
La privacidad/retención de estos temporales debe validarse antes de datos reales.

## Determinación de Certbot — PENDIENTE

El script preparado `audit_certbot_readonly.py` necesita Ubuntu y el argumento
`--lectura-autorizada`. **No se ejecutó en el servidor.** No instala, invoca
Certbot, renueva, contacta ACME ni muestra secretos. Inventaría rutas/binarios,
paquetes APT/Snap, timers y sus ejecutables, referencias cron y plugins de
lineages. No imprime argumentos completos de servicios ni renewalparams.

Procedimiento de lectura, después de autorizar acceso:

1. Identificar versión Ubuntu/systemd/Nginx, puertos, usuarios, espacio y límites.
2. Inventariar APT, Snap y virtualenv de Certbot; comprobar el ejecutable que
   invoca cada timer/cron, no solo el que resuelve el PATH interactivo.
3. Relacionar el programador activo con su config-dir y lineage. Revisar también
   wrappers, crontabs de usuarios y otros schedulers si aparecen indicios.
4. Determinar qué instalación lee la configuración de renovación de cada
   certificado. Si APT y Snap compiten sobre el mismo directorio, no deshabilitar
   ni borrar ninguno sin plan separado y autorización.
5. Contrastar la ruta y SAN del certificado que realmente carga el vhost. No
   asumir `/etc/letsencrypt/live/wa-qa.dimabug.casol.cl`: puede haber otra lineage
   o un sufijo. No copiar ni mostrar la clave privada.

El script ofrece un candidato de timer cuando hay uno activo; no constituye por
sí solo prueba de propiedad. La conclusión requiere el contraste anterior.
Actualmente la respuesta verificable es: **instalación administradora no
determinada con los antecedentes disponibles**.

Para este hostname nuevo, preferir renovación mediante DNS-01 si está aprobada
y soportada por el gestor existente: mantiene la regla de una sola ruta web.
HTTP-01 requeriría una excepción ACME expresamente autorizada en puerto 80. No
añadirla ni ejecutar certbot --nginx, renew o dry-run ahora; todos pueden producir
cambios o tráfico externo. El deploy hook futuro debe probar sintaxis antes de
recargar y no modificar otras lineages ni sites.

## Monitoreo — PROPUESTO

No publicar Actuator ni paneles. Supervisar:

| Señal | Comprobación/acción propuesta |
|---|---|
| Proceso y recursos | Estado systemd, reinicios, memoria, CPU, tareas y FDs de unidades QA |
| HTTP/TLS | GET local con SNI y token custodiado, challenge fijo, sin registrar URL |
| MySQL | SELECT 1 con auditor exclusivo; alerta por desconexión/esquema inválido |
| Cola | Conteos por estado, edad/vencimiento próximo, errores/reintentos; no leer contenido |
| Pausas | Distinguir pausa voluntaria de fallo; alertar si los eventos se acercan a vencer |
| Nginx | Tasas 4xx/429/5xx y latencia desde log seguro, no cuerpos ni teléfonos |
| Certificados | Caducidad: aviso <21 días y crítico <7, fallo del único renovador autorizado |
| Disco | Datos, índices, temporales y journal; detener recepción antes de agotamiento |

La API de métricas actual es Java y local a cada JVM; no existe exportador remoto
operativo. Diseñar su lectura/alarma posterior sin abrir un endpoint público.
Un GET exitoso no prueba por sí solo que MySQL funcione; combinar señales. El
monitor no requiere App Secret ni acceso a los mensajes. Revisar retención y
cuotas de journal/logs por unidad sin modificar la política global a ciegas.

## Pruebas HTTPS Nginx preparadas — NO EJECUTADAS

No existe validación nueva de runtime Nginx en esta entrega. Las pruebas de la
etapa 3.2-B correspondían a un proxy JDK y a sintaxis HAProxy; no se presentan
como pruebas de Nginx.

Después de autorizar el entorno aislado:

1. Renderizar rutas TLS, comprobar usuario/directorio temporal y ejecutar
   `nginx -t` en una configuración local de pruebas; no recargar el site público.
2. Usar certificado de prueba para este hostname, CA explícita y Nginx en loopback.
3. Aprovisionar únicamente activos/participantes sintéticos y el artefacto mínimo.
4. Ejecutar `check_nginx_https.py` con CA y puerto local, fase `paused` y aprobación
   explícita por entorno. El script no resuelve DNS: conecta solo a 127.0.0.1 con
   SNI/Host `wa-qa.dimabug.casol.cl` y valida el certificado, sin trust-all.
5. Autorizar posteriormente solo recepción y repetir fase `receive-only` con
   WABA `qa-waba-ubuntu`, número `qa-numero-ubuntu` y participante ficticio del
   script. Comprobar cuatro duplicados concurrentes: un aceptado y tres duplicados.
6. Desde un auditor SQL distinto comprobar el commit, filas PENDIENTE, ausencia
   de respuestas y efecto nulo de firmas/tokens/participantes incorrectos. El
   script HTTP no afirma haber observado el commit en la BD.
7. Probar caída/recuperación exclusivamente de MySQL QA, HMAC con espacios/Unicode,
   limitación real de cuerpos chunked, saturación y timers/cierre de conexiones.
8. Revisar logs Nginx, globales y backend usando marcadores ficticios. No pasar
   a exposición externa mientras alguna prueba requerida esté pendiente.

Secretos ficticios solo por entorno: `WA_QA_TEST_VERIFY_TOKEN` y
`WA_QA_TEST_APP_SECRET`; aprobación `WA_QA_NGINX_TEST_APPROVED=SI_SINTETICO_LOCAL`.
No incluir sus valores en argumentos, evidencias o chat. El comando preparado:

```text
python3 check_nginx_https.py --ca-file <CA-local> --port 18443 --phase paused
```

Los seis tests estáticos locales de `test_templates.py` comprueban contratos de
seguridad y sintaxis Python. **No equivalen a nginx -t, systemd-analyze verify,
arranque MySQL o integración HTTPS.**

## Cambios que requieren aprobación ANTES de ejecutar

| Cambio | Condición previa |
|---|---|
| Inspección remota de lectura | Acceso autorizado; no revisar secretos ni cambiar servicios |
| Artefacto QA aislado | Implementación y pruebas propias; no arrancar el JAR general |
| Usuarios/unidades/directorios | Capacidad, puertos libres y soporte systemd/cgroup/LoadCredential |
| Instancia MySQL nueva | Versión, datadir vacío, TLS, SQL aprobado y cuentas exclusivas |
| Nginx QA loopback | Certificados ficticios, sintaxis y privacidad de todos los logs |
| DNS/certificado del hostname | Propiedad, proxy/CDN, Certbot real y renovación comprobados |
| Listener público/firewall | Autorización independiente tras pruebas locales |
| Meta | Aplicación/activos, secretos y autorización explícita posterior |

## Reversión — PROPUESTO, sin borrar datos

Apagar recepción/procesamiento/despacho; detener únicamente el listener/site QA
y su unidad Spring después de identificar sus recursos. Mantener claves, volumen
y base. Para revertir código, cambiar el selector `current` al artefacto anterior
aprobado, no restaurar una base antigua sobre eventos recibidos. Revisar antes
compatibilidad de esquema y claves. Las filas pueden vencer aunque el proceso
esté parado; no prometer procesamiento indefinido.

Verificar puertos 18132/13328/18443 del piloto y, si se autorizó, el vhost público,
sin parar servicios de staging. No borrar lineages, desinstalar Certbot, editar
unidades compartidas ni ejecutar rm/prune/drop como reversión. Conservar las
evidencias y la configuración anterior privada. Actualmente no hay cambios de
servidor que revertir porque no se ha ejecutado ninguno.
