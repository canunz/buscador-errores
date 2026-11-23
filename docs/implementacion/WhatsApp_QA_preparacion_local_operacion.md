# WhatsApp QA — Paquete local de preparación y operación

Fecha: 17 de noviembre de 2026. Solicitud posterior a C.5: procedimiento Certbot,
plantillas coherentes, medición preparada y comprobaciones de aislamiento/recuperación.
**Preparado localmente; no desplegado ni ejecutado en Ubuntu.** No hay medición
de 24 h iniciada, sondeo real, SSH, cambio de firewall/servicios/DNS/certificados,
SQL remoto, conexión Meta, commit o push. Trabajo concurrente conservado.

## 1. Archivos y candidato actual

| Archivo | Finalidad |
|---|---|
| `WhatsApp_Certbot_relevo_reversible.md` | Fases autorizables, precondiciones, comandos de cambio aparcados y retorno condicionado |
| `qa/whatsapp/ubuntu/candidato-qa-local.json` | Candidato actual, separado del antecedente histórico C.1 |
| `application.properties.template` y `nginx-wa-qa.conf.template` | Receptor y proxy coherentes en **127.0.0.1:8082** |
| `mysqld-qa.cnf.template` | Ajuste MySQL de menor memoria probado en C.3, solo para instancia nueva |
| `baseline_24h.py` | Plan por defecto; recogida futura en primer plano, sin instalar/programar nada |
| `baseline-24h.targets.example.json` | Rutas pendientes, deliberadamente no ejecutables como sondas válidas |
| `test_templates.py` y `test_baseline_24h.py` | Coherencia y pruebas sintéticas de seguridad/parseo; no medición real |

Los antecedentes C.1–C.5 y sus pruebas no se reescriben como si hubieran probado
estos archivos nuevos. Las pruebas funcionales aprobadas no se repiten aquí.

## 2. Plantillas alineadas, sin ampliar recursos

- Spring y MySQL: **MemoryMax 512 MiB, MemoryHigh 384 MiB, CPUQuota 50% de una
  CPU, TasksMax 96, CPUWeight/IOWeight 25**. Máximo conjunto: 1 GiB y una CPU.
- JVM: heap 64–192 MiB; direct 48, metaspace 96, codecache 48 MiB. JDBC 2 y Tomcat 8.
- Receptor y proxy: **8082**. MySQL exclusivo sigue **13328**; gateway local Nginx
  sigue **127.0.0.1:18443**, sin publicar 443 ni abrir firewall.
- MySQL: buffer y redo 128 MiB, ocho conexiones, performance_schema OFF,
  table_open_cache 128, table_definition_cache 400, thread_cache_size 4,
  tmp_table_size/max_heap_table_size 16 MiB, temptable_max_ram 32 MiB,
  skip-name-resolve. No cargar esta configuración en mysql.service existente.
- Durabilidad InnoDB explícita: flush_log_at_trx_commit 1, doublewrite ON,
  sync_binlog 1. La plantilla ya deshabilita binlog: no proporciona recuperación
  a un instante mediante binlogs ni debe presentarse como tal. Backup pendiente.
- socketTimeout se alinea a **5000 ms** con el ensayo, manteniendo TLS MySQL
  **VERIFY_IDENTITY** y require-secure-transport. TLS/truststore todavía requieren
  validación propia: el JDBC del ensayo anterior no utilizó TLS.
- Recepción, procesamiento, medios, envío y conexión Meta siguen false. Hibernate
  pooling WARN evita inventario INFO con URL JDBC; errores SQL continúan visibles.
- No se sustituyen JAR, flags de seguridad, endpoints, autenticación o base existentes.

El ahorro MySQL se compra con menor diagnóstico SQL al desactivar performance_schema;
debe aceptarse antes de instalar. Esto alinea un candidato con pruebas locales,
no aprueba 512 MiB en Ubuntu. El cambio 18132 -> 8082 requiere un futuro smoke
de integración del paquete renderizado; no justifica repetir todo el OCR/buscador.

## 3. Medición representativa de 24 horas — lista, NO iniciada

### Comportamiento del programa

Sin argumentos imprime exclusivamente `PLAN_ONLY`. No lee /proc, crea informes,
abre HTTP o instala/programa tareas. En esta preparación se comprobó solo ese
modo y pruebas unitarias con datos sintéticos.

La futura ejecución exige simultáneamente:

1. Autorización del operador, ventana y custodia acordadas (un flag no concede permiso).
2. `--execute`, confirmación literal `--approval MEDICION_24H_AUTORIZADA` y
   `--output` a un directorio **nuevo** con padre existente y de confianza.
3. Ubuntu y usuario no root. Sin sudo; no modifica sysstat, cron o systemd.

Recoge 86400 segundos, con una muestra inicial y una cada 60 s hasta el extremo
final. Usa reloj monotónico y timestamps UTC, registra intervalos reales y huecos.
Ctrl+C, TERM/HUP producen informe incompleto cuando se puede ejecutar finally;
un kill/reinicio puede dejar solo datos parciales. Sin summary completo nunca
dar la ventana por aprobada. No se ejecuta como daemon ni se instala un timer.
Para una sesión estable de 24 h, acordar el mecanismo de supervisión **antes**;
no asumir que sobrevivirá a una desconexión ni lanzar nohup/tmux automáticamente.

### Datos y privacidad

- RAM disponible/total y swap ocupada desde /proc; CPU, iowait/steal y tasas de
  swap a partir de deltas, sin duplicar guest. Primer delta o reset: no inventar cero.
- Espacio e inodos de /var/lib y /var/log agrupados por filesystem: no se suman
  dos veces. I/O del dispositivo correspondiente cuando está disponible, sin
  sumar disco padre y particiones. Métrica de dispositivo, no latencia de aplicación.
- Memoria y contador CPU de cgroups conocidos: staging, mysql, buscador y PM2.
  Si una ruta no existe/no es legible, marcar unavailable. PM2 es el gestor, no
  una identificación demostrada del consumo exclusivo de Avanza; no sumar padre/hijo.
- No lee entornos, argumentos de procesos, logs crudos, claves o bases de datos.
- Directorio 0700 y archivos 0600 creados exclusivamente, sin sobrescribir.
  Datos: `plan.json`, `samples.jsonl`, `summary.json`. El operador controla custodia,
  fecha de borrado y entrega únicamente el resumen saneado; no usar carpeta pública.
- `coverage_24h` exige duración/cobertura y muestras válidas; no implica capacidad
  suficiente ni autorización. `deployment_approved` siempre false.

### Sondas HTTP opcionales y apagadas por defecto

Nginx combined no reconstruye latencias. Para medirlas se requiere aprobar rutas
GET inocuas de staging, Avanza y Buscador, sin login ni efectos; el ejemplo tiene
placeholders que el programa rechaza. No rellenarlos automáticamente con `/`,
`/health` o una ruta adivinada.

Solo `--allow-http --targets-file ARCHIVO_APROBADO` habilita tráfico. Lista cerrada
de tres hosts HTTPS, puerto 443; sin userinfo, query, fragmentos o tokens. Un GET
por destino cada 300 s, timeout 5 s, sin redirects, cookies, credenciales o lectura
de cuerpo. Verifica certificado/hostname; no se desactiva TLS para hacer pasar
una sonda. Excepciones se registran por tipo, sin mensajes que revelen URL/datos.

Registra alias, estado o tipo de error y duración; no URL, cabeceras, cuerpo o IP.
p50/p95/p99 muestrales solo de respuestas 2xx, con conteos y errores separados.
Sin los tres destinos y respuestas útiles, `latencies_pending` sigue true.
HTTP 3xx/4xx/5xx no se contabiliza silenciosamente como muestra exitosa rápida.
Las propias sondas generan tráfico/logs existentes: deben autorizarse antes.

### Forma futura de uso — NO ejecutar ahora

Tras acordar/custodiar rutas y transferir el script a una ubicación aprobada:

```bash
# Solo plan; sin --execute nunca comienza a medir
python3 /ruta/aprobada/baseline_24h.py
```

Ejemplo **aparcado**, únicamente para una autorización posterior y rutas reales
ya revisadas, sin tareas programadas:

```bash
# EJECUCION DE 24 HORAS: PROHIBIDA EN ESTA PREPARACION
python3 /ruta/aprobada/baseline_24h.py --execute \
  --approval MEDICION_24H_AUTORIZADA \
  --output /home/carolina/wa-baseline-IDENTIFICADOR \
  --allow-http --targets-file /ruta/privada/rutas-get-aprobadas.json
```

Sin sondas se omiten ambos flags HTTP: eso no completa el gate de latencias.
No pegar los placeholders anteriores para iniciar nada en la etapa actual.

### Aceptación propuesta

Ventana representativa de 24 h, sin ocultar huecos/reinicios, incluyendo picos y
tareas normales. Usar también sar existente como contraste. No provocar renovaciones,
backups o fallos para completar la ventana. Registrar si las muestras cada minuto
pueden ocultar picos; no afirmar un mínimo continuo de RAM con esa cadencia.

Mantener candidatos C.1: MemAvailable mínimo observado >=2 GiB antes de QA,
reserva objetivo 1 GiB durante piloto, sin OOM o swap sostenida como sustituto
de RAM y disco libre reservado >=5 GiB más presupuesto QA/backup/logs. Acordar
con el operador límites CPU/I/O y tolerancias de latencia/errores de cada aplicación
antes de ver resultados. El script no inventa esos umbrales ni los aprueba.
El baseline favorable aún necesita comparación con un piloto posterior autorizado.

## 4. Cómo comprobar aislamiento — procedimiento futuro

No se ejecuta en Ubuntu aquí. Registrar evidencia saneada y ejecutar acciones
mutables solo en la fase que se autorice; nunca un lote de órdenes globales.

| Control | Comprobación y aceptación |
|---|---|
| Identidad del release | Hash JAR, Start-Class y lista permitida iguales al artefacto revisado; no JAR general. Revalidar sin reconstruir sobre fuentes concurrentes. |
| Usuarios y rutas | QA usa dimabug-wa-qa; MySQL nuevo dimabug-wa-mysql. Dueños, grupos y ACL impiden leer secretos/datos de staging; runtime no puede editar el marcador APPROVED o el release. No mostrar contenidos. |
| Servicios efectivos | Revisar propiedades systemd de unidades QA y drop-ins filtrados; usuario, ExecStart, configuración, cuotas, TasksMax, IPAddressDeny/Allow. No volcar Environment ni credenciales. |
| Binds/UFW | Solo 127.0.0.1:8082 y 13328; no 0.0.0.0/::; reglas no abren esos puertos. Staging/3306 conservan sus binds. No basta una captura antigua o estado active. |
| Salida específica | IPAddressAllow=localhost no limita puertos. Preparar/aprobar control específico solo para identidades QA: nuevas conexiones Spring únicamente a MySQL QA; respuestas a conexiones entrantes permitidas; MySQL sin nuevas salidas innecesarias. No aplicar reglas globales que afecten otros usuarios. |
| Verificación de denegaciones | Inspeccionar política efectiva cgroup/BPF/nft y probarla con canarios propios en entorno aislado antes del host. No usar staging/producción como destino de pruebas negativas sin permiso ni afirmar que un bind prueba egress. La política por puerto sigue pendiente de implementación aprobada. |
| MySQL | Instancia/binario/version config propios, datadir/socket/puerto exclusivos, AppArmor sin relajar MySQL existente. Valores efectivos iguales al candidato; cuenta aplicación DML solo sobre DB nueva, sin root/FILE/DDL/GRANT ni acceso a otras bases. Revisar grants saneados, sin modificar privilegios productivos. |
| TLS SQL | SELECT 1 autenticado con archivo privado de opciones/credencial; cadena, SAN 127.0.0.1 y VERIFY_IDENTITY; negativo con CA/identidad incorrecta en QA aislado. No desactivar TLS ante error. |
| Flags y módulos | Recepción inicialmente false y demás flags siempre false; ausencia de trabajadores, OCR/búsqueda/envío y transportes Meta en el JAR, no solo en propiedades. |
| Registros | Marcadores sintéticos bajo permiso; escanear stdout/stderr, journal y Nginx global/vhost, incluidos fallos. Mantener señales técnicas sin query, tokens, payloads o firmas. No copiar logs crudos al repo/chat. |

Comandos de lectura breves candidatos, **no ejecutados** y uno por vez cuando se
pidan al operador: ss filtrado por puertos, systemctl show con propiedades antes
de `--`, permisos/stat de rutas específicas y lectura de cgroup de unidades QA.
No incluir ps con argumentos, /proc/environ, nginx -T sin filtrar ni dumps SQL.

## 5. Cómo comprobar disponibilidad y recuperación QA

### Readiness antes de admitir tráfico

`Type=simple` y systemctl active no esperan la disponibilidad HTTP. TimeoutStartSec
90 s tampoco implementa un gate de aplicación con ese tipo. Verificar por separado:
estado de proceso, bind correcto, GET challenge seguro y disponibilidad SQL con
TLS. El token debe leerse desde credencial/archivo privado en memoria; nunca
en un argumento curl, URL impresa o mensaje de excepción.

Antes de la instalación falta preparar/aceptar el helper de readiness o mecanismo
equivalente que haga cumplir ese gate y un timeout acordado. Un GET solo no acredita
que se pueda guardar un evento. El calentamiento con escritura sintética requiere
permiso y un contexto QA; registrar y conservar el evento, no borrarlo para ocultarlo.

### Matriz de ensayo aislado, no de fallos en Ubuntu

| Caso | Resultado exigido |
|---|---|
| Pausa controlada de admisión | Mecanismo QA probado devuelve 503 sin ACK ni persistir lo rechazado. Editar un flag de arranque no es pausa inmediata. |
| Detención app QA | Solo unidad QA termina, sin tocar mysql.service, Nginx o staging. Mantener datos y claves; pendientes sobreviven. |
| Caída/reinicio MySQL QA | Error controlado sin ACK prematuro; reconexión/readiness; reintento aceptado una vez y repetición deduplicada. No afirmar recuperación de trabajadores excluidos del JAR. |
| OOM/fallo de proceso | Revisar causa y cgroup; Restart=on-failure y StartLimitBurst acotan reintentos. No elevar límites automáticamente ni hacer un bucle de reinicios. |
| Reversión release | Volver solo al release/config QA anterior compatible, verificar hash y esquema; no migración inversa improvisada o dump antiguo sobre eventos nuevos. |
| Retorno Nginx QA | Restaurar solo diff QA autorizado, validar conjunto antes de reload autorizado; nunca restart global para resolver QA. |
| Verificación colateral | Comparar estados, códigos y latencias de staging/Avanza/Buscador con baseline y tolerancias acordadas. Sin histórico no inventar ausencia de regresión. |

El ensayo local anterior de error SQL/deduplicación sigue siendo evidencia válida;
no se repitió. TLS, puerto nuevo, integración completa systemd y política efectiva
son cambios/riesgos que justifican verificaciones específicas posteriores.

### Contención y retorno propuestos, NO ejecutados

Si se autoriza contención: cerrar únicamente admisión QA con mecanismo revisado;
detener **solo** dimabug-wa-qa.service y, si corresponde después, su MySQL exclusivo.
Preservar eventos, claves, configuración, logs saneados, hash y tiempos bajo custodia.
No borrar pendientes por espacio; una pausa no congela TTL. No detener servicios
existentes ni restaurar certificados/DB indiscriminadamente.

Para reanudar: corregir causa, verificar espacio/TLS/claves/esquema/cuotas, arrancar
solo QA con recepción pausada, pasar readiness, revisar expiraciones y vigilar
impacto antes de habilitar admisión autorizada. Recuperación Certbot sigue el
procedimiento separado y nunca restaura APT incompatible a ciegas.

## 6. Estado y autorización siguiente

Los cuatro entregables de **preparación local** están listos para revisión.
La ejecución del relevo Certbot, recogida 24 h, transferencia/instalación del script,
sondas HTTP, implementación del control de salida/readiness, pilotos, certificados,
DNS y despliegue requieren las autorizaciones específicas correspondientes.
**No se declara Ubuntu APTO.** No se ejecuta otra etapa automáticamente.

## 7. Validación de esta preparación

Se ejecutaron **18 pruebas locales correctas**: contratos de plantillas y casos
sintéticos del colector (modo seguro, activación accidental, destinos inválidos,
CPU/memoria/I/O, excepciones saneadas, ausencia de lectura de cuerpos/redirects,
resúmenes parciales y latencias pendientes). Los clientes HTTP de esos tests
son simulados: no enviaron peticiones reales ni leyeron /proc del servidor.

Se ejecutó además el programa **sin argumentos**, obteniendo PLAN_ONLY, duración
86400 s, intervalo 60 s y HTTP false. No se ejecutó `--execute`, no se generó
una serie de muestras real y no se probó el colector durante 24 h. Los comandos
mutables del procedimiento Certbot permanecen únicamente en documentación.
