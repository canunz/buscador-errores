# QA — Infraestructura HTTPS local, etapa 3.2-B

## Resultado final — VERIFICADO

| Validación | Resultado |
|---|---|
| Cliente HTTPS → proxy QA → Spring TCP → MySQL exclusivo | 11 pruebas aprobadas, 0 fallos/errores |
| Regresión de WhatsApp, seguridad, persistencia, OCR y búsqueda pública | 182 ejecutadas correctas, 2 omitidas, 0 fallos/errores |
| HAProxy HTTP | Sintaxis aprobada, sin listeners |
| HAProxy TLS | Sintaxis y PEM localhost aprobados, sin listeners |
| Archivos anteriores | 1.206 hashes originales intactos |
| SQL, frontend, núcleo OCR, backend productivo | Sin modificaciones |
| Cierre | Proxy/Spring sin listener; contenedores exclusivos detenidos |

Las dos omisiones pertenecen a métodos históricos del lanzador de reinicios
MySQL, no habilitados en la regresión H2. Esta etapa sí comprobó una parada real
y recuperación de su MySQL nuevo en la prueba TCP independiente. No se ejecutó
la suite completa de módulos ajenos ni Angular.

No se conectó Meta o Ubuntu, no se utilizaron datos ni credenciales reales, no
se crearon túneles ni servicios públicos, no se instalaron dependencias, no se
modificaron bases anteriores y no hubo migraciones, commits, push o despliegues.

## Matriz de seguridad

| Caso | Estado | Comprobación |
|---|---|---|
| GET autorizado | VERIFICADO | 200, challenge con ceros exacto, TLS 1.2/1.3 y sin redirección |
| Token incorrecto | VERIFICADO | 403 sin divulgar credenciales |
| POST autorizado | VERIFICADO | 200 y fila comprometida visible desde otra conexión JDBC |
| Métodos no permitidos | VERIFICADO | PUT/DELETE/PATCH/HEAD/OPTIONS rechazados por el proxy |
| Rutas desconocidas | VERIFICADO | 403; URI ambigua rechazada con 400/403 por parser o proxy |
| API/Actuator/Swagger | VERIFICADO | Denegados en el proxy, sin pasar al backend |
| Firma incorrecta/cuerpo alterado | VERIFICADO | 403, sin nuevos efectos persistentes |
| Bytes originales | VERIFICADO | Espacios, saltos y Unicode firmados aceptados sin normalización |
| Tamaño >3 MiB | VERIFICADO | 413 y ninguna fila |
| Concurrencia | VERIFICADO | Cuatro reentregas simultáneas, todos 200 y una sola fila |
| Autorización | VERIFICADO | Participante no permitido rechazado; WABA/número/BSUID cubiertos también por regresión |
| MySQL indisponible | VERIFICADO | Parada del contenedor propio; 503, recuperación TCP/JDBC y ningún evento falso |
| Persistencia obligatoria | VERIFICADO | Adaptador JPA real, SQL aprobado y prueba de fallo de arranque sin almacén en regresión |
| Conservación sin procesamiento | VERIFICADO | Solo recepción encendida; evento PENDIENTE y sin respuesta |
| Cuatro controles | VERIFICADO | Todos apagados al arrancar; independencia y medios deshabilitados en regresión |
| Envío real/descarga real | VERIFICADO | Adaptadores inexistentes y guardián que rechaza activarlos |
| Logs | VERIFICADO | Sin tokens, firmas, teléfono, texto/ID marcadores ni query sensible en capturas de proxy y backend |
| Certificado público y Meta | PENDIENTE | El certificado usado es ficticio autofirmado para localhost |
| Tráfico mediante HAProxy estándar | PENDIENTE | Solo se validaron sintaxis/PEM; el recorrido funcional usó proxy JDK QA |
| Ubuntu, DNS, firewall, renovación | PENDIENTE | No se accedió a infraestructura remota |

La prueba funcional usa contexto Spring mínimo con los componentes reales de
recepción/seguridad/JPA; no es un despliegue del producto completo. La regresión
complementaria incluye el flujo OCR/búsqueda y trabajadores. No se deduce de estas
pruebas la disponibilidad pública ni capacidad productiva.

## Evidencias

Recorrido final aprobado:

- `qa/runtime/dimabug-wa-https-ec38c87e14/informe.json`
- `build/target/surefire-reports/TEST-cl.casol.backend.whatsapp.https.ProxyMetaHttpsTest.xml`
- `maven-test.log`, `fuentes.json`, `listeners.json`, `privacidad-final.json`,
  `backend-capturado.log`, `proxy.log`, `cierre-verificado.json`.
- Base `wa_durable_ec38c87e14`, contenedor `dimabug-wa-https-ec38c87e14`, volumen
  `dimabug-wa-https-ec38c87e14-data`: solo tres tablas, usuario DML, 2 CPU y 2 GiB.
- Imagen MySQL: `sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b`.
- Proxy y Spring se enlazaron a 127.0.0.1 y se comprobó que dejaron de escuchar.
- El certificado PKCS12 y los secretos de prueba están en carpetas privadas con
  ACL restringida. No incluir sus valores o archivos en entregables públicos.

Configuración HAProxy:

- `qa/runtime/dimabug-wa-https-ec38c87e14/haproxy-sintaxis.txt` y ejecución de
  validación con código 0.
- `qa/runtime/dimabug-wa-https-ec38c87e14/tls-haproxy-90629823/informe.json` y
  `validacion.txt`: código 0, red none y sin listener.
- Imagen existente:
  `sha256:5378234d19457d645ed6d5a0ac994ef196614f9a2cabda8f6ccd6ac8815f9098`.

Regresión y conservación:

- `qa/runtime/meta32b-regresion/maven-test.log` y `junit/` (18 informes XML).
- `qa/runtime/meta32b-baseline.json`: inventario Git y hashes iniciales.
- `qa/runtime/meta32b-manifiesto-final.json`: hashes de los ocho archivos nuevos.
- `qa/runtime/meta32b-git-final.txt`: estado final completo de Git.

El intento inicial `dimabug-wa-https-4835939cbf` se conserva como evidencia de
dos problemas del harness: esperaba 403 en una URI que el parser rechazaba con
400, y comprobaba JDBC antes de recuperar la disponibilidad TCP. Ambos se
corrigieron sin cambios en Spring o relajaciones de seguridad. Ese contenedor
también quedó detenido; su volumen no se borró.

## Archivos nuevos y Git final

1. `qa/whatsapp/https/haproxy.local.cfg`
2. `qa/whatsapp/https/haproxy.tls.local.cfg`
3. `qa/whatsapp/https/run.py`
4. `qa/whatsapp/https/validar_tls.py`
5. `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/https/ProxyHttpsLocal.java`
6. `dima-bug-backend/src/test/java/cl/casol/backend/whatsapp/https/ProxyMetaHttpsTest.java`
7. `docs/implementacion/WhatsApp_HTTPS_etapa_3_2_B.md`
8. Este informe.

**VERIFICADO:** rama `integracion-final-uat`; sin commits. Los 55 archivos
rastreados que ya estaban modificados conservan sus bytes. Se mantienen también
los archivos previos sin seguimiento; las adiciones nuevas siguen sin commit.
No se borraron archivos, contenedores o volúmenes. No se alteró ningún servicio
previo para facilitar las pruebas.

## Requisitos y riesgos antes de conectar Meta

**PROPUESTO:** Ubuntu QA aislado con TLS propio para el primer piloto si se
confirma separación de producción. Un túnel temporal requiere demostrar que no
termina TLS/inspecciona contenido sensible de forma incompatible con la política.
No se debe publicar directamente Spring ni usar el proxy de pruebas como prueba
de endurecimiento de un proxy productivo.

**PENDIENTE:** autorización explícita, aplicación/activos Meta válidos, dominio,
TLS de CA pública, firewall, operación del proxy estándar, límites de carga,
logs de infraestructura, custodia/renovación de credenciales y certificados.

La activación y reversión propuestas están en
`docs/implementacion/WhatsApp_HTTPS_etapa_3_2_B.md`. La reversión no borra datos:
detiene la exposición del piloto y preserva cola/volumen/claves, teniendo en
cuenta los vencimientos. Un evento del panel no demuestra recepción de mensajes
reales; no se habilita envío automático como parte de esa comprobación.
