# WhatsApp QA — Aislamiento local y validación acotada

Fecha local: 15 de noviembre de 2026. Autorización: avanzar con aislamiento QA
local, validar capacidad y certificados. Sin cambios remotos, conexión Meta,
certificados públicos, commits ni despliegues. Cambios previos de Git conservados.

## Resultado

**Recepción durable aislada funcional; capacidad NO aprobada para despliegue.**
Certificados sintéticos comprobados offline. Certbot y certificados reales no
validados funcionalmente. No se aumentaron los límites para hacer pasar pruebas.

## Implementación local

- Composition root en `qa/whatsapp/local-isolation/RecepcionQa.java`, sin escaneo
  general. Reutiliza dominio, puerto de almacenamiento, JPA, cifrado, política,
  firma y controlador existentes. No duplica reglas OCR ni de búsqueda.
- Construcción offline mediante lista permitida y hashes de fuentes. JAR con
  46 clases de aplicación, exclusivamente WhatsApp/QA; sin trabajadores, OCR,
  búsqueda, identidad, correo o tickets. El composition root rechaza activación
  de procesamiento, medios, envío o conexión Meta. Recepción apagada por defecto.
- No se modificaron fuentes, pom ni recursos del backend general. Se usa un
  proyecto de compilación generado dentro de qa/runtime.
- Plantillas locales Ubuntu alineadas con C.1: 512 MiB por servicio, High 384,
  CPU 50%, pesos 25, tareas 96; JVM 64/192, direct 48, metaspace 96, codecache 48;
  MySQL buffer 128 y conexiones 8; JDBC 2, Tomcat 8; Nginx 2 r/s, burst 4, conns 8.
  **No instaladas.** Pruebas estáticas no equivalen a systemd-analyze/nginx -t.

## Evidencia ejecutada

Build: `qa/runtime/wa-local-build-5994884ff7/informe.json`.
JAR SHA256:
`f7242bdb10b846fddca4c934a52a36a5cb4941db30da1ce946f75980fa504523`.

Ensayo inicial: `qa/runtime/wa-local-9a18522a86/informe.json`.
Ensayo ampliado: `qa/runtime/wa-local-a097a241ee/informe.json`.
Resultados runtime ignorados por Git; secretos y logs crudos bajo ACL privada.

| Comprobación | Resultado |
|---|---|
| Construcción offline / inventario JAR | Correctos; manifiesto de fuentes y hash registrados |
| Contratos de plantillas | 7 pruebas correctas, incluida coherencia con presupuesto JSON C.1 |
| MySQL exclusivo | Imagen local por digest, volumen/base nuevos; solo tres tablas canónicas; cuenta aplicación DML de esa base |
| Límites MySQL | 512 MiB, sin swap adicional, CPU 0,5 y 96 tareas; puerto publicado solo en 127.0.0.1 |
| Spring aislado | Arranca con flags JVM revisados y pool 2; proceso Windows sin límite total de cgroup |
| GET challenge | Respuesta correcta en loopback |
| Recepción pausada / firma inválida | 503 / 403 respectivamente |
| Ruta interna de búsqueda | Rechazada con 403; no expuesta por este artefacto |
| Duplicados concurrentes | Cuatro peticiones, cuatro 200, una fila de evento |
| Carga acotada | 30 peticiones del mismo evento a 2/s: p95 203 ms, p99 muestral 219 ms |
| Reinicio exclusivo MySQL | Evento conservado; reenvío recibido sin crear otro evento |
| TLS sintético offline | Certificado nuevo localhost válido con confianza explícita; hostname incorrecto rechazado |
| Privacidad | Sin contraseñas/token/secreto ni marcadores de texto/participante en log Spring comprobado |
| Finalización | Procesos Java terminados y ambos contenedores nuevos detenidos; volúmenes conservados |

La carga reutiliza un evento: mide deduplicación, no un caudal de inserción de
eventos distintos ni crecimiento de cola. No ensaya procesamiento de reservas
ni OCR. En el ensayo ampliado se observó un deadlock SQL transitorio; las cuatro
peticiones terminaron 200 con una fila, pero deben cubrirse más patrones antes
de aprobar carga sostenida. No interpretar ese resultado como ausencia de errores
SQL internos.

## Capacidad: criterio incumplido

- Pico cgroup MySQL previo al reinicio: **528560128 bytes (~504,1 MiB)**,
  aproximadamente **98,5%** de 512 MiB. Supera el criterio C.1 de pico <90%.
- Primera muestra docker stats: ~432 MiB; no demuestra uso sostenido bajo
  MemoryHigh 384. Docker stats y memory.current contabilizan caché de forma
  distinta; no tratarlos como medidas idénticas.
- Tras reinicio: memory.current 390713344 bytes (~372,6 MiB), pico 408649728
  (~389,7 MiB); memory.events sin OOM/OOM kill en ese cgroup reiniciado. No se
  conserva aquí el contador anterior como si fuera del mismo arranque.
- Spring Windows: working set ~275 MiB, pico ~307,3 MiB, memoria privada
  ~310,4 MiB. No prueba cumplimiento de 512 MiB en Linux ni suma una garantía
  de capacidad de Ubuntu.
- Primeras cuatro peticiones concurrentes: ~2,84–2,89 s en el ensayo ampliado;
  ~3,36–3,45 s en el inicial. Diferenciar ese arranque frío de la muestra posterior
  de 30 peticiones; no declarar cumplido el objetivo de latencia global.

**No subir cuotas ni aprobar 1 GiB total sobre el host compartido.** Próxima
validación: separar inicialización y operación normal del MySQL nuevo, medir
ambas con carga de eventos distintos y cgroup Spring en Linux aislado; si el
margen sigue siendo insuficiente, ajustar de forma justificada o separar la VM.
Faltan baseline Ubuntu de 24 h y comparación con latencias de otras aplicaciones.

## Certificados y límites del ensayo

No se descargó Nginx: no estaba entre las imágenes locales. La prueba OpenSSL
no sustituye Nginx real, cadena pública, certificado servido o renovación ACME.
Snap sigue siendo candidato; no se deshabilitó APT ni se ejecutó Certbot.
Las evidencias de vigencia locales reales siguen fechadas en el consolidado.

TLS MySQL queda pendiente: el ensayo local usa JDBC sin TLS por loopback; las
plantillas Ubuntu conservan VERIFY_IDENTITY. No se afirma equivalencia de
seguridad de red: bridge Docker no limita por sí solo el tráfico saliente como
las reglas propuestas de systemd.

## Pendientes mínimos después de esta entrega

1. Resolver el margen de memoria y latencia fría mediante ensayo Linux aislado,
   sin ampliar límites a ciegas; baseline Ubuntu y tolerancia de impacto acordada.
2. Nginx real y TLS MySQL, rutas/firmas/privacidad de logs globales bajo carga.
3. Validación Certbot separadamente autorizada para las tres lineages, certificado
   servido, respaldo privado y relevo con un programador único.
4. Ensayo operativo de pausa/alertas y recuperación de trabajadores cuando estos
   se incorporen: este artefacto solo acredita recepción durable.

Consolidación y aislamiento local avanzados. Despliegue no aprobado. Skill 4.5
sin definición disponible: no se certifica su checklist.
