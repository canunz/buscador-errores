# WhatsApp QA — Verificación local con Nginx real

Fecha de informe: 16 de noviembre de 2026, America/Santiago.
Alcance: comprobar seis comportamientos del receptor mediante HTTPS local.
No hubo SSH, cambios en Ubuntu, DNS, certificados públicos, bases existentes,
servicios remotos, conexión Meta, commits ni despliegues. Git local conservado.

## Resultado

**PASS del ensayo local**, tras corregir problemas del montaje de prueba.
Nginx real recibió 17 solicitudes HTTPS y las encaminó o rechazó según lo
esperado. La deduplicación acreditada es de recepción durable; el JAR no ejecuta
OCR, búsqueda, trabajadores ni envío. No se afirma procesamiento de negocio
exactamente una vez ni entrega de mensajes externos.

| Comprobación solicitada | Evidencia obtenida |
|---|---|
| 1. HTTPS en Nginx | Nginx 1.24.0 real, nginx -t correcto; TLS con confianza explícita y SAN IP correcto. Certificado no confiable y hostname localhost no incluido en SAN rechazados. Todas las respuestas comprobadas tienen sesión TLS y cabecera del gateway. GET challenge conserva exactamente `000123`. |
| 2. Firma sobre bytes originales | POST de 82255 bytes con UTF-8, CRLF y padding >64 KiB aceptado. Un byte de whitespace modificado conservando firma anterior: 403. Firma modificada: 403. Sin firma: 403. No hubo reserialización previa a verificar el HMAC que alterase esos bytes. |
| 3. Duplicados | Cuatro POST concurrentes del mismo evento: una aceptación y tres duplicados, todos 200. Dos eventos en SQL al terminar esa fase: el frío y el duplicado. |
| 4. Error SQL controlado | Pausa exclusiva del contenedor MySQL nuevo; POST devuelve 503 y cuerpo fijo `{"estado":"RECHAZADO"}` en 5287 ms. Al reanudar, SQL sigue con dos eventos: esa petición no quedó comprometida. Reintento: una aceptación; repetición: un duplicado; total final tres eventos. |
| 5. Privacidad | Cero coincidencias de los marcadores sensibles y secretos del ensayo en stdout/stderr de ocho contenedores, auditados después de detenerlos. Incluye Nginx global/vhost, Spring, MySQL y clientes. Se conservaron señales técnicas de error SQL. |
| 6. Latencia fría | Primer POST de ese proceso Spring antes de calentamiento de persistencia: **2841 ms** vía HTTPS, cuerpo de 82255 bytes. Arranque Spring: **62,083 s**. Son medidas distintas. |

El registro técnico Nginx confirma **8 respuestas 200, 8 respuestas 403 y una
503**. Cero 429, 502 o 504 en este recorrido: no se confundió rate limiting o
timeout del proxy con un rechazo de firma o un error controlado de aplicación.
Rutas de búsqueda pública, Actuator, Swagger, ruta codificada y método PUT
fueron bloqueados. El fallo SQL quedó visible como cuatro señales técnicas
en el log Spring (coincidencias de categorías/mensajes, no cuatro fallos distintos).

## Qué significa la medición en frío

El cliente de readiness solo hace GET; no se ejecutó warmup ni otro POST antes
de la muestra fría. Nginx, MySQL y el contexto Spring ya estaban disponibles.
La petición usa HTTPS; puede reutilizar la conexión TLS del GET challenge.
**No es tiempo de arranque total, cold boot del host ni handshake TLS aislado.**
No se purgó caché global de Docker/Windows/Linux. Es una única muestra, no p95
ni p99 y no demuestra resuelto el incumplimiento frío observado en C.3. Cambia
también el tamaño del cuerpo respecto a los mensajes cortos de esa etapa.
El reintento tras la pausa SQL tardó 2609 ms; la respuesta de error SQL, 5287 ms,
incluye el socket timeout configurado de 5 s y manejo de excepción. No mezclar
estos tiempos con latencias exitosas bajo carga.

## Montaje reproducible y seguridad

- Lanzador existente ampliado con `--variant optimized --https`; mantiene las
  fuentes del backend general intactas. Mismo JAR aislado C.2, SHA256
  `f7242bdb10b846fddca4c934a52a36a5cb4941db30da1ce946f75980fa504523`.
- MySQL 8.0.46 ajustado y Spring Temurin 21: cgroups Linux separados de 512 MiB,
  CPU 0,5 y sin swap adicional. MySQL nuevo, tres tablas canónicas y permisos
  DML de aplicación limitados a esa base. Se conservan ajustes de durabilidad.
- Nginx: imagen pública 1.24.0 descargada exclusivamente para QA, fijada por
  digest. Usuario 101, filesystem de solo lectura, tmpfs 16 MiB, cgroup 64 MiB y
  CPU 0,25. Configuración completa propia; no se carga conf de otros sitios.
- Red Docker interna; ningún puerto publicado. Nginx y Spring escuchan loopback
  en el namespace QA. Los clientes TLS comparten ese namespace mediante el
  contenedor Nginx, que sigue ejecutándose mientras MySQL está pausado.
- Certificado autofirmado sintético nuevo de dos días: SAN dominio QA e IP
  127.0.0.1. El cliente verifica IP/SAN y confianza de forma activa, con Host QA;
  no deshabilita hostname verification ni utiliza trust-all. No se validó SNI
  del dominio público, DNS, cadena pública ni renovación Certbot.
- Se renderiza la plantilla Nginx propuesta con rutas locales de certificados,
  logs y temporales. Se conserva rate 2/s, burst 4, límite de conexiones 8,
  ruta exacta, firma y límites de cuerpo. El cliente espacia casos negativos
  para no sustituir sus resultados por un 429 de tasa.
- Lecturas técnicas y logs crudos permanecen en carpeta con ACL privada, fuera
  de Git. Certificado/clave, contraseñas y claves criptográficas son sintéticos.

## Incidencias del lanzador y correcciones locales

Se conservaron los intentos anteriores; no se presentan como aprobados:

1. `wa-nginx-1d38fbe77d`: nginx -t detectó niveles distintos para la misma ruta
   `/tmp/body` en configuración global y location. Se unificaron en `1 2` en
   el render local. Auditoría también detectó URL JDBC completa en inventario
   INFO de Hibernate; no tokens ni payloads en ese arranque.
2. `wa-nginx-9322ad90a1`: readiness confundió el servidor temporal de inicialización
   MySQL (socket, puerto 0) con el definitivo. Ahora exige SELECT 1 mediante TCP
   al puerto 3306 antes de iniciar las pruebas. No se añadió una pausa arbitraria.
3. `wa-nginx-8e8b163df4`: Spring tardó ~65 s en iniciar, mientras el cliente esperaba
   60 s. La espera se alineó con los 90 s ya previstos en la plantilla de servicio,
   sin aumentar CPU o RAM. No llegó a comprobar Nginx.

La categoría `org.hibernate.orm.connections.pooling` queda en WARN solo en la
configuración del ensayo y en la plantilla QA local propuesta; no se modificó
el logging de Ubuntu ni código productivo. Se conserva WARN/ERROR de esa categoría
y el resto de avisos/errores SQL. La prueba de fallo y las señales técnicas del
log acreditan que la privacidad no se logró apagando todos los errores.

El lanzador también preserva el error original si falla la auditoría posterior,
guarda diagnóstico privado y reanuda MySQL en finally tras la pausa. El cliente
concurrente cierra su executor incluso al fallar un caso.

## Archivos y evidencia

- `qa/whatsapp/local-isolation/HttpsProbe.java`: casos TLS/HMAC/deduplicación/error
  y medición explícita del primer POST.
- `qa/whatsapp/local-isolation/nginx_https.py`: certificados, render Nginx,
  validación, arranque y auditoría de registros.
- `resources_linux.py`: modo HTTPS, coordinación SQL y diagnóstico privado;
  `LoadProbe.java`: readiness acotado a 90 s; README ampliado.
- `qa/whatsapp/ubuntu/application.properties.template`: categoría INFO limitada
  a WARN; prueba estática correspondiente actualizada.
- `qa/runtime/wa-nginx-5c2e3cfe87/informe.json`: ejecución final PASS y mediciones.
- `qa/whatsapp/local-isolation/evidencias-https-local-2026-11-16.json`: resumen
  saneado, versiones/digests y hashes de fuentes y clases de cliente ejecutadas.
- Sintaxis Python y compilación Java correctas; **7 pruebas estáticas correctas**.

Los ocho contenedores del ensayo final terminaron `exited`; los contenedores de
los intentos previos también se detuvieron. Volúmenes y redes propios conservados
para revisión; no se hizo limpieza global. Docker Desktop se inició localmente
para este trabajo; no se detuvo el daemon ni se alteraron recursos ajenos.

## Pendientes que esta prueba no cierra

Compatibilidad del paquete/configuración Nginx de Ubuntu y sus logs globales;
TLS MySQL (el JDBC del ensayo sigue sin TLS en loopback); certificados públicos y
Certbot; baseline/impacto del servidor compartido; lotes máximos, carga prolongada
y política de calentamiento; procesamiento de negocio y efectos externos.
No se valida toda clase de fallo SQL ni incertidumbre de commit: aquí se probó
indisponibilidad por pausa antes de confirmar. No se certifica ausencia universal
de datos sensibles: se comprobaron secretos y marcadores concretos del ensayo.

**Decisión:** seis comprobaciones locales documentadas con el alcance indicado.
No equivale a autorización de despliegue o conexión con Meta. El usuario no
necesita ejecutar comandos adicionales para esta entrega.
