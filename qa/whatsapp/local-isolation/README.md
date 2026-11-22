# Recepción durable QA local

Herramientas exclusivas de pruebas. No se instalan en Ubuntu ni habilitan Meta.
No sustituyen el JAR general ni modifican fuentes del backend.

- `build.py`: selecciona dominio, puertos y adaptadores de recepción/persistencia;
  excluye configuración general, trabajadores, búsqueda y OCR. Compila offline
  con dependencias existentes. Verifica el contenido del JAR y registra hashes.
- `RecepcionQa.java`: composition root explícito, sin component scan general.
  Reutiliza el controlador, política, validación de firma y almacenamiento JPA.
  Rechaza procesamiento, medios, envío y conexión Meta. La recepción está
  apagada por defecto; el lanzador la habilita solo para eventos sintéticos.
- `validate.py <directorio-build>`: crea MySQL exclusivo con imagen local fijada
  por digest, volumen propio y puerto loopback aleatorio. Aplica únicamente el
  SQL canónico de tres tablas; credencial de aplicación limitada a DML de esa
  base. Arranca Java local, comprueba firma, pausa, rutas internas, duplicados,
  carga acotada y persistencia tras reinicio del contenedor propio. Comprueba
  certificado ficticio con OpenSSL offline, nunca con ACME.

Todos los resultados quedan en `qa/runtime` (ignorado por Git). Las credenciales,
claves sintéticas y logs crudos permanecen en una carpeta con ACL restringida
al usuario local y SYSTEM. No compartir esa carpeta ni añadirla al repositorio.
El lanzador detiene sus procesos y contenedor al finalizar; conserva el volumen
y material de QA para análisis. Su eliminación posterior debe verificar nombres,
labels y rutas antes de actuar; nunca usar limpieza global de Docker.

El JDBC sin TLS se limita al ensayo local loopback; **no valida TLS MySQL** ni es
la configuración propuesta para Ubuntu (`VERIFY_IDENTITY`). Docker bridge no
constituye una política de denegación de salida; no se afirma aislamiento de red
equivalente a systemd. No hay llamadas a Meta ni descargas de imágenes.

## Límites de la validación

MySQL: cgroup 512 MiB, swap adicional cero, CPU 50% y 96 tareas. No se aplica
MemoryHigh de systemd en Docker. El arranque se mide además de la carga.
Spring: flags JVM revisados; proceso Windows **sin límite total por cgroup**.
Las mediciones Windows y Linux no son equivalentes y no se suman como si fueran
un baseline de Ubuntu. La prueba acotada no acredita 24 horas ni impacto sobre
staging. `APROBADO_ACOTADO` describe comprobaciones funcionales, no capacidad.
Nginx no estaba en las imágenes locales: no se descarga ni se sustituye su
validación por HAProxy, JDK u OpenSSL. Certificados reales y Certbot pendientes.

Las plantillas en `../ubuntu` quedan alineadas con el presupuesto C.1 como
preparación local. Siguen sin estar autorizadas para instalación.

## Etapa C.3 — límites Linux y comparación MySQL

`resources_linux.py <directorio-build> --variant baseline|optimized` usa el mismo
JAR y dos contenedores Linux separados, con 512 MiB, media CPU y sin swap adicional
cada uno. Red Docker interna, sin puertos publicados. El cliente Java sintético
tiene un tercer cgroup (256 MiB/media CPU), para no cargar sus recursos al proceso
Spring medido. Todos comparten exclusivamente el namespace de red del MySQL nuevo;
por ello Spring y JDBC siguen escuchando/usando loopback como exige el guard.

Se registra inicialización de datadir, esquema, reinicio normal, arranque Spring,
calentamiento y carga. Cada prueba crea su volumen; no modifica ni reutiliza bases
existentes. `LoadProbe.java` genera 20 eventos de calentamiento, 120 eventos de carga
a 2/s, y un grupo de cuatro duplicados tras cada fase (142 eventos únicos en total).
La carga es secuencial a ese ritmo, salvo los grupos de duplicados concurrentes.
No representa un caudal concurrente arbitrario ni el peor lote de 1000 eventos.

`baseline` conserva los ajustes MySQL C.2. `optimized` conserva buffer pool 128 MiB,
redo 128 MiB y ocho conexiones; deshabilita performance_schema y reduce cachés y
memoria temporal. Es una variante experimental, no una modificación de las
plantillas Ubuntu. Desactivar performance_schema reduce las posibilidades de
diagnóstico SQL; solo se propone para una instancia QA dedicada tras aceptación.
No se cambian fsync, doble escritura, redo ni garantías de durabilidad para ahorrar.

Se leen `memory.current`, `memory.peak`, `memory.stat`, `memory.events`, `cpu.stat`
y tareas del cgroup. Docker MemoryMax sí se aplica; MemoryHigh de systemd no se
emula. Las muestras por fase bajo 384 MiB no prueban un máximo sostenido entre ellas.
El pico y los contadores de presión/OOM complementan esas muestras.

`summarize_resources.py <directorio-evidencias> [...]` produce un resumen de las
mediciones y gates sin arrancar recursos. Los resultados funcionales PASS no son
aprobación de capacidad de Ubuntu. Las pruebas son secuenciales; no se purga la
caché global del host. La inicialización crea un datadir nuevo en cada variante,
pero la caché del sistema y la carga de Docker Desktop pueden variar.

La imagen Java 21 Temurin se obtuvo del registro público exclusivamente para QA,
y el lanzador la fija por digest. No se añadieron dependencias Maven ni imágenes
al despliegue del proyecto; la imagen MySQL existente es 8.0.46. La salida queda
en qa/runtime con ACL privada y contenedores detenidos al terminar. No hay SSH,
ACME, Nginx ni tráfico Meta en este ensayo.

## Comprobaciones HTTPS con Nginx real

El modo adicional `resources_linux.py <build> --variant optimized --https`
reutiliza el montaje Linux y reemplaza la carga C.3 por los casos de
`HttpsProbe.java`, coordinados por `nginx_https.py`:

- Certificado sintético nuevo de dos días y confianza explícita; rechazo de
  certificado no confiable y hostname incorrecto. Cliente HTTPS HTTP/1.1 con
  verificación de identidad activa para 127.0.0.1, incluido en SAN, y Host QA.
  No hay DNS público ni validación del certificado real del dominio.
- Nginx real 1.24.0 fijado por digest. Renderiza la plantilla de Ubuntu con rutas
  locales para certificado, logs y temporales. Conf completa independiente,
  `nginx -t`, usuario 101 y límites 64 MiB/CPU 0,25; escucha loopback en el
  namespace QA, sin publicar puertos. Global y vhost tienen política de logs
  explícita; no se heredan configuraciones de otras aplicaciones.
- GET challenge exacto; primer POST antes de cualquier calentamiento de
  persistencia. Cuerpo UTF-8, CRLF y padding >64 KiB firmado sobre bytes originales.
  Un byte cambiado con la firma anterior, firma incorrecta o ausente dan 403.
- Rutas internas, ruta codificada y método extra rechazados. Cuatro duplicados
  concurrentes: exactamente una aceptación y tres duplicados, contrastados con SQL.
- Pausa exclusiva del MySQL nuevo: 503 con cuerpo fijo, sin confirmación SQL.
  Se reanuda siempre en finally; reintento aceptado y repetición deduplicada.
- Auditoría de stdout/stderr tras detener componentes: contraseñas, tokens,
  claves, firmas, texto, participantes, IDs y URL JDBC. Logs crudos en carpeta
  privada; resumen solo con conteos. El inventario INFO de conexiones Hibernate
  se limita a WARN en QA, conservando avisos y errores SQL.

El check de MySQL listo exige conexión TCP al puerto definitivo: el servidor
temporal de inicialización, que escucha solo socket, no es readiness válido.
Los fallos del lanzador conservan diagnóstico privado y no ocultan el error
original si también falla la auditoría de logs.

La deduplicación acreditada es de recepción durable. No hay OCR, búsqueda,
trabajadores ni envío en este JAR. Un primer POST es una muestra de latencia
fría, no un percentil ni una prueba de arranque tras purgar caché del host.

## Quick Tunnel temporal (autorización específica de 20 minutos)

`resources_linux.py <build> --variant optimized --https --temporary-tunnel
--cloudflared-exe <binario-verificado>` es un modo opt-in que crea exclusivamente
datos/credenciales sintéticos. Nunca se activa mediante los modos anteriores.

La imagen de backend sigue en red interna, sin publicar MySQL ni Spring (8082).
Un gateway interno Nginx y un gateway frontal Nginx separados conservan TLS,
autenticación Basic temporal y filtros de ruta/método; el frontal publica solo
su HTTPS en un puerto aleatorio **127.0.0.1** del host. El frontal se conecta a
la red backend; sus configuraciones son de solo lectura y su upstream es fijo.
No se afirma que la red del frontal tenga bloqueo de egress como la del backend.
Esta separación evita publicar un puerto del contenedor MySQL y permite llegar
desde Windows cuando el publish sobre una red Docker internal no es accesible.

cloudflared Windows, fijado por SHA-256 del release oficial, abre un Quick Tunnel
anónimo de URL aleatoria hacia ese único HTTPS local, con CA pool y nombre de
origen verificados. No utiliza no-tls-verify, login, DNS, servicio instalado ni
cuenta/token Cloudflare del usuario. Su perfil se limita al directorio privado QA.
X-Forwarded-Proto debe ser https antes de admitir autenticación en el gateway.

Antes de crear URL pública se comprueban publicación de puertos, Basic, bloqueo
API, confianza TLS y challenge local. Antes de declarar ACTIVE se comprueban
en la URL pública Basic requerido, rutas internas cerradas, HMAC, cuerpo alterado,
firma ausente y cuatro duplicados con una aceptación; se contrasta una fila SQL.
No hay acceso desde este modo a WhatsApp/Meta ni activos reales. Basic es control
de esta prueba sintética; no se presupone compatible con los webhooks reales Meta.

`temporary_tunnel.py` supervisa el proceso: Timer máximo 20 minutos desde el
arranque de cloudflared, comprobación de plazo y Windows Job Object KILL_ON_JOB_CLOSE.
Si el supervisor termina, el conector también. No se crea tarea programada.
Al cerrar el conector, el lanzador detiene exclusivamente sus contenedores.
Volúmenes/redes y credenciales sintéticas quedan bajo custodia privada para auditoría;
no se hace docker prune, borrado de bases o cambios en Ubuntu.

Estado/URL/plazo: `<run>/tunnel-status.json`; pruebas: `tunnel-public-tests.json`.
Las credenciales están solo en `<run>/private/client.json`, con ACL privada;
no imprimirlas ni enviarlas al chat. El cliente no toma credenciales por argumentos:

```powershell
python qa/whatsapp/local-isolation/tunnel_client.py --run qa/runtime/wa-tunnel-ID --self-test
```

Sin --self-test genera un único mensaje sintético firmado; con **--stop** solicita
cierre anticipado mediante marcador privado, sin matar procesos ajenos. Rechaza
URL http o estado expirado. Sustituir el ID por la ejecución indicada por el supervisor.
Tras cierre, el resultado final y estados de contenedores quedan en informe.json.
No prometer prueba aprobada cuando solo hay STARTING o CLOSED por un fallo previo.
