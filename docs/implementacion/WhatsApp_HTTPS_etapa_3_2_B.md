# Etapa 3.2-B — Preparación HTTPS local

## Alcance y diagnóstico

**VERIFICADO:** rama `integracion-final-uat`, inventario Git inicial y contraste
de controles 3.2-A contra código y pruebas. El baseline contiene 1.206 archivos.
No se modificó ninguno de esos archivos: todos los cambios de esta etapa son
archivos nuevos de QA/documentación. Se preservaron los cambios locales previos.

**VERIFICADO:** Java 21, Maven/dependencias en caché, Docker Desktop y las imágenes
locales MySQL 8.0.46 y HAProxy 3.2.26. No hay nginx/haproxy/caddy/ngrok/cloudflared
como ejecutables disponibles en el PATH. Se encontró OpenSSL existente en Git;
se utilizó únicamente para generar un certificado localhost ficticio.

Las configuraciones previas `qa/mysql/compose.qa-proxy.json` y
`qa/mysql/haproxy.qa-local.cfg` corresponden a un proxy **TCP de MySQL**, no a un
proxy HTTP seguro del webhook. No se reutilizaron sus contenedores, volúmenes,
credenciales ni puertos. No se instalaron dependencias.

**VERIFICADO:** Spring conserva bind `127.0.0.1`, Forwarded desactivado, ruta exacta
GET/POST, firma HMAC-SHA256 de bytes originales con comparación constante, token
GET, alcance WABA/número/participantes, compatibilidad teléfono/BSUID, persistencia
obligatoria y ACK posterior al commit. No se relajó el guardián ni se admitieron
direcciones de contenedores como excepción de red.

## Alternativas para conexión posterior — PROPUESTO

| Aspecto | A: túnel temporal + proxy | B: Ubuntu QA + proxy dedicado |
|---|---|---|
| Puesta en marcha | Rápida si herramienta/plataforma están autorizadas | Requiere revisión del servidor y aislamiento |
| Reversión | Cerrar túnel y detener proxy | Retirar listener/ruta y cerrar acceso autorizado |
| TLS | Comprobar dónde termina y quién puede leer el contenido | Certificado y clave bajo control de la organización |
| Disponibilidad | Depende del equipo local y del proveedor de túnel | Mayor continuidad si existe una instancia QA dedicada |
| Dominio | URL variable o dominio autorizado según proveedor | DNS/subdominio autorizado, todavía no definido |
| Riesgos | Inspección del proveedor, registro de cuerpos, URL expuesta, caída del equipo | Confusión con producción, firewall/DNS, renovación y mantenimiento |
| Requisitos | Proxy obligatorio, sin publicación directa de Spring, inspección deshabilitada | Segmentación QA, TLS, firewall, monitoreo, renovación y acceso administrativo aprobado |

**Recomendación para el primer piloto:** dadas las exigencias de evitar inspección
externa, preferir **Ubuntu QA aislado con TLS propio**, siempre que una revisión
posterior confirme que puede separarse de producción. No se recomienda usar el
servidor existente sin esa comprobación. No hubo SSH ni acceso a Ubuntu.

El túnel temporal queda como alternativa condicionada: debe permitir TLS hasta
un proxy controlado y cumplir la política de confidencialidad. Desactivar un
inspector no evita técnicamente que un proveedor que termina TLS lea el contenido.
Si no se puede demostrar la condición, no utilizarlo con datos sensibles.

**PENDIENTE:** estado real de Ubuntu, dominio autorizado, DNS, certificados
públicos, firewall, proveedor de túnel y sus condiciones. No se inventó un dominio
operativo ni se creó túnel alguno.

## Arquitectura local comprobada — VERIFICADO

Cliente sintético HTTPS → proxy JDK exclusivo de QA en 127.0.0.1 → webhook Spring
Boot TCP en 127.0.0.1 → MySQL exclusivo con puerto publicado solo en 127.0.0.1.

El proxy funcional está en **código de pruebas**, no en el artefacto productivo.
Se usa el JDK ya instalado para evitar instalaciones y no debilitar el bind de
Spring: un contenedor HAProxy con una topología Docker convencional no debe
forzar a abrir Spring en 0.0.0.0 para llegar al host Windows.

La prueba usa certificado autofirmado para localhost con SAN DNS/IP, truststore
explícito del cliente y verificación TLS habitual; no utiliza trust-all ni un
hostname verifier permisivo. Se verifica TLS 1.2/1.3. Esto no acredita un
certificado público válido ni compatibilidad de ese certificado con Meta.

El contexto Spring de recorrido es deliberadamente mínimo: controlador y política
Meta reales, seguridad real y adaptador JPA real contra el SQL aprobado de tres
tablas. El procesamiento permanece desactivado. Los trabajadores, OCR, búsqueda
y los restantes interruptores se verifican además en la regresión 3.2-A.

## Configuración de proxy preparada

**VERIFICADO — proxy de pruebas JDK:**

- Solo acepta la representación exacta `/webhooks/whatsapp`, GET/POST.
- Rechaza API, Actuator, Swagger, subrutas, slash final y codificaciones de ruta.
- El parser HTTP puede devolver 400 para una URI ambigua antes de llegar al
  handler; una denegación no se convierte en autorización.
- Límite 3 MiB, ocho hilos, cola de 16 y backlog de 16; plazos HTTP del JDK y
  timeout de conexión/upstream. No es una validación de carga productiva.
- Transmite bytes originales sin normalizar JSON, modificar espacios o decodificar
  contenido. Conserva la firma y los parámetros GET; no sigue redirecciones.
- Solo transmite Content-Type y firma desde la solicitud. No reenvía Authorization,
  cookies, Forwarded ni X-Forwarded-* suministrados por el cliente.
- Solo registra estados HTTP, nunca ruta, query, cabeceras ni cuerpo.

**PROPUESTO — proxy estándar para una futura plataforma autorizada:**

- `qa/whatsapp/https/haproxy.local.cfg`: entrada HTTP **loopback** 18080 para un
  recorrido posterior aprobado con TLS terminando en una entrada autorizada.
- `qa/whatsapp/https/haproxy.tls.local.cfg`: entrada TLS **loopback** 18443 con PEM
  externo y TLS mínimo 1.2. No incorpora certificados ni claves.
- Ambos usan backend loopback 18081; los puertos son ejemplos locales, no puertos
  publicados ni una configuración aplicada a Spring.
- Denegación por defecto de rutas/métodos, límites de conexiones y tiempos, sin
  logs de tráfico, stats, capturas de query/cuerpo, retries o redirecciones.
- Eliminan cabeceras Forwarded, origen no confiable, Authorization y cookies.
- El límite declarado y el tamaño disponible en buffer se comprueban en HAProxy.
  Un cuerpo chunked mayor que el buffer sigue requiriendo el límite de lectura
  real de Spring; no se afirma que req.body_size almacene o mida todo un cuerpo
  de 3 MiB. No se aumenta el buffer a varios MiB por conexión.

**VERIFICADO:** HAProxy aceptó ambas configuraciones con `-c`. La variante TLS
cargó un PEM localhost ficticio nuevo. Se usó la imagen existente, `--pull never`,
`--network none` y sin iniciar listeners. **No se ejercitó tráfico HAProxy real**
entre Docker y Spring Windows. No utilizar estas pruebas como autorización para
activar el proxy estándar en Internet. La prueba funcional corresponde al proxy
JDK descrito arriba.

## Interruptores y protección de datos

**VERIFICADO:** recepción, procesamiento, medios y despacho simulado arrancan
apagados. Los indicadores `envio-real` y `conexion-meta` siguen bloqueados por
el guardián; no existe adaptador real de envío ni de descarga.

- Recepción apagada: POST firmado obtiene 503 y no se guarda el evento.
- Solo recepción encendida: se compromete el evento y permanece PENDIENTE.
- Procesamiento apagado: no hay búsqueda ni respuesta automática.
- Medios apagados: no se ejecuta OCR por imágenes; no existe descargador externo.
- Envío apagado: no se consume la salida simulada; nunca se llama a Meta.

La prueba HTTPS habilita solo recepción en memoria del contexto de prueba cuando
lo requiere el caso. No cambia los valores por defecto de los archivos del
proyecto. Pausar no congela vencimientos: imágenes/propuestas 15 minutos y texto
24 horas. No prometer conservación procesable indefinida durante una parada.

## Reproducción local autorizada

Desde la raíz del repositorio, con las herramientas/imágenes ya disponibles:

```text
python -I -B qa/whatsapp/https/run.py
```

El lanzador crea un directorio nuevo bajo `qa/runtime`, copia las fuentes a un
build aislado sin perfiles de producción, compila offline y crea **una base nueva**
con solo las tres tablas aprobadas. Utiliza usuario DML y secretos sintéticos por
fichero con ACL restringida. Verifica etiqueta, imagen, volumen y binding antes
de detener/reiniciar exclusivamente su contenedor durante la prueba de caída.
El puerto MySQL permanece fijo y solo en loopback durante ese recorrido.

Al finalizar se detienen la JVM/listeners y el MySQL de esa ejecución. No se
eliminan archivos, contenedores o volúmenes. Los secretos/certificados ficticios
permanecen en carpetas privadas; no se deben copiar a documentación ni al chat.

Validación opcional de la configuración TLS estándar tras una ejecución aprobada:

```text
python -I -B qa/whatsapp/https/validar_tls.py <directorio-de-la-ejecucion-aprobada>
```

Genera otro certificado localhost ficticio mediante OpenSSL ya instalado y
ejecuta únicamente HAProxy `-c` en red deshabilitada. No instala/activa un proxy.

## Activación futura de infraestructura — PROPUESTO, requiere autorización

1. Elegir plataforma QA aislada tras inspección autorizada. No reutilizar
   servicios/credenciales productivos ni alterar el guardián como atajo.
2. Confirmar dominio, titularidad, DNS, certificado de CA pública y renovación.
3. Autorizar por separado el listener HTTPS y el firewall. Backend y MySQL deben
   seguir privados. No publicar todo el puerto de Spring detrás de un túnel.
4. Revisar logs de proxy, proveedor, sistema y aplicación. Omitir request line
   completa, query strings, cabeceras y cuerpos; no habilitar paneles de inspección.
5. Probar desde el exterior únicamente tras autorización, con información
   sintética: ruta exacta, TLS, métodos, tamaño, firma y ausencia de otros servicios.
6. Mantener inicialmente procesamiento, medios y envíos apagados. No activar
   recepción real hasta aprobar la configuración de Meta y custodia de secretos.

## Configuración futura de Meta — PROPUESTO, no ejecutada

Formato exclusivo de callback:

```text
https://<dominio-autorizado>/webhooks/whatsapp
```

Generar Verify Token mediante CSPRNG, custodiarlo en el gestor autorizado y
entregarlo al backend y al panel por canales seguros. No ponerlo en comandos,
logs, Git o chat. El App Secret para HMAC y las claves de cifrado/HMAC de
persistencia son credenciales diferentes; no intercambiarlas.

1. Seleccionar la aplicación expresamente autorizada y revisar restricciones.
   El antecedente hello_world no demuestra recepción ni habilita otra aplicación.
2. Verificar WABA, Phone Number ID, activos y permisos/suscripciones efectivos.
3. Registrar la URL autorizada exacta, sin slash final ni redirección.
4. Introducir el Verify Token custodiado sin mostrarlo en evidencias.
5. Ejecutar la verificación GET y comprobar challenge exacto y logs sin query.
6. Suscribirse al campo `messages` y comprobar la asociación con la cuenta
   correspondiente. La configuración del callback y la suscripción son controles
   distintos.
7. Enviar un evento sintético desde el panel y comprobar firma y alcance.
   Si utiliza IDs de muestra fuera de la lista autorizada, el rechazo es esperado:
   no desactivar firma o autorización para obtener un 200 artificial.
8. Verificar el commit, código de respuesta y contadores, sin capturar el cuerpo.
9. Confirmar que procesamiento/medios/envío siguen apagados y que no se genera
   ninguna respuesta automática.

El evento del panel prueba únicamente ese recorrido de prueba. Una recepción
real exige posteriormente un mensaje de un participante autorizado dirigido al
número correcto. No ejecutar esa comprobación en esta etapa. Interfaz, permisos
y capacidades exactas deberán revisarse en el momento de la autorización.

## Reversión sin limpieza — PROPUESTO

1. Apagar envío real y despacho simulado; preservar el estado de cualquier
   operación iniciada. Una operación externa incierta no se reenvía a ciegas.
2. Apagar recepción y procesamiento mediante controles/configuración autorizados.
   No devolver 200 si no se conserva un evento. Los reintentos externos son finitos.
3. Detener exclusivamente el túnel/listener/proxy del piloto, identificados por
   su configuración/PID/etiqueta. No detener otros procesos o contenedores.
4. Conservar MySQL, volumen, claves, filas pendientes y evidencias. No ejecutar
   DROP, rm, prune, borrado de volúmenes ni restauración destructiva.
5. Verificar que los puertos del piloto ya no escuchan en ninguna interfaz
   pública; revisar también reglas del proveedor y firewall.
6. Recuperar la configuración anterior custodiada y arrancar solo la modalidad
   aprobada, sin eliminar los registros recibidos. Revisar vencimientos antes de
   reanudar y no convertir respuestas SIMULADAS en envíos reales.

En las pruebas locales de esta etapa no se modificaron archivos anteriores, por
lo que no hay configuración anterior que restaurar. Los recursos exclusivos
quedaron detenidos y las evidencias conservadas.
