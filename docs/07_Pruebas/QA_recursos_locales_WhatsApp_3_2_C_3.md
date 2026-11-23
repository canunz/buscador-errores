# Etapa 3.2-C.3 — Optimización y validación local de recursos

Fecha local: 15 de noviembre de 2026 (ejecución Linux con marcas UTC del 16).
Alcance autorizado: recursos locales. Sin SSH ni cambios en Ubuntu, DNS, Nginx,
MySQL existente, certificados públicos o servicios; sin conexión con Meta.
Se conserva el trabajo pendiente de Git. Sin commits ni despliegues.

## Resultado y decisión

**512 MiB por servicio bastaron para el ensayo de recepción durable ajustado.**
No es una aprobación para el host Ubuntu compartido, OCR, búsqueda, trabajadores
o envío. No se aumentó la memoria: se identificó y redujo consumo de MySQL.

La configuración base no ofrece margen en la inicialización: llega a 512 MiB
y activa reclamación de memoria. La variante ajustada reduce el pico de esa
fase a 272,8 MiB, preservando controles de durabilidad comprobados. Spring se
validó por primera vez dentro de un cgroup Linux real con 512 MiB y CPU limitada.

**Pendiente:** primera escritura en frío por encima de p99 3 s; falta diseñar y
probar calentamiento/readiness antes de admitir tráfico. Una carga de un minuto
no valida 24 horas ni el efecto sobre las demás aplicaciones de Ubuntu.

## Método y aislamiento

- Mismo JAR de recepción C.2 (46 clases de aplicación). SHA256:
  `f7242bdb10b846fddca4c934a52a36a5cb4941db30da1ce946f75980fa504523`.
  No se modificó código del backend, motor OCR ni reglas de búsqueda.
- MySQL **8.0.46**, imagen local fijada por digest. Java Linux: Temurin 21 JRE
  Jammy, descargado del registro público para QA y fijado por digest en el
  lanzador. No se añadieron dependencias Maven ni se publicó una imagen.
- Pruebas secuenciales, cada una con red Docker `--internal`, volumen, base,
  credenciales y contenedores nuevos. Ningún puerto publicado. Spring/cliente
  comparten solo el namespace de red del MySQL propio, manteniendo JDBC y HTTP
  en loopback y preservando el guard existente.
- MySQL y Spring: **512 MiB, swap adicional cero, CPU 0,5, 96 tareas** cada uno,
  valores efectivos verificados mediante Docker inspect. Spring con filesystem
  de solo lectura, usuario sin privilegios y temporal tmpfs 64 MiB.
- Cliente sintético en un tercer cgroup de 256 MiB/CPU 0,5, fuera de las medidas
  de Spring. Ese cliente es instrumentación local, no parte del presupuesto QA.
- Se midieron cgroup memory.current/peak/stat/events, cpu.stat y tareas:
  inicialización de datadir, creación del esquema canónico de tres tablas,
  reinicio normal de MySQL, arranque Spring, calentamiento y carga.
- En cada ensayo: 20 mensajes distintos de calentamiento + cuatro envíos de un
  duplicado; 120 mensajes distintos a 2/s + cuatro envíos de otro duplicado.
  Cuatro participantes sintéticos autorizados. Total: **148 peticiones POST,
  142 eventos únicos**, todos recibidos y persistidos sin duplicados adicionales.
- No hay trabajadores en este artefacto. La cola conserva eventos pendientes;
  no se afirma consumo de cola, recuperación de reservas ni ejecución OCR.

## Ajustes experimentales de MySQL

La variante base mantiene los ajustes de C.2. La variante optimizada conserva
buffer pool **128 MiB**, redo **128 MiB** y **8 conexiones**, y añade:

| Parámetro | Variante optimizada |
|---|---:|
| performance_schema | OFF |
| table_open_cache | 128 |
| table_definition_cache | 400 |
| thread_cache_size | 4 |
| tmp_table_size | 16 MiB |
| max_heap_table_size | 16 MiB |
| temptable_max_ram | 32 MiB |

Se comprobó explícitamente en la instancia ajustada:
`innodb_flush_log_at_trx_commit=1`, `innodb_doublewrite=ON`, redo 134217728 bytes
y `sync_binlog=1`. No se ahorró memoria debilitando esos ajustes.

**Coste:** desactivar performance_schema limita el diagnóstico SQL. Las métricas
cgroup y contadores generales siguen disponibles, pero no equivalen a esa
instrumentación. Es un candidato para QA dedicado, no una recomendación de
aplicarlo al MySQL existente. Los ajustes se probaron como conjunto; no se
atribuyen ahorros individuales a un único parámetro.

No se modificaron las plantillas Ubuntu para incorporar esta variante. Se
requiere aceptar ese coste y un diff posterior antes de una instalación.

## Memoria observada (MiB)

| Medición | Base | Ajustada |
|---|---:|---:|
| MySQL pico durante inicialización/esquema | 512,0 | 272,8 |
| MySQL actual al finalizar inicialización | 488,8 | 226,4 |
| MySQL memoria anónima al finalizar inicialización | 346,5 | 117,4 |
| MySQL caché de archivos en esa muestra | 134,5 | 100,9 |
| MySQL actual al finalizar carga | 378,8 | 142,9 |
| MySQL pico desde reinicio normal hasta final de carga | 383,3 | 146,4 |
| Spring actual al finalizar carga | 302,7 | 259,5 |
| Spring pico desde arranque hasta final de carga | 306,8 | 263,3 |

En la base hubo **387 eventos memory.max** hasta la creación del esquema y cero
OOM/OOM kill. `memory.max` cuenta presión/reclamación al alcanzar el límite;
no significa 387 caídas ni prueba margen suficiente. Tras reinicio se separan
los contadores del ciclo normal. La variante ajustada no registró eventos max
ni OOM en las fases medidas.

El pico inicial de MySQL no era solo caché: la base tenía unos 346 MiB anónimos
frente a 117 MiB ajustados. Se preservan las categorías para no confundir RSS,
docker stats y memoria completa del cgroup.

**Precaución con Spring:** la base cargó ~46 MiB de caché de archivos al cgroup;
la ajustada casi ninguno, mientras la memoria anónima fue similar (253,3 frente
a 256 MiB al finalizar carga). No atribuir su diferencia total a optimizar MySQL.
La caché del host no se purgó y puede quedar contabilizada en otros cgroups o
ancestros. Para el presupuesto conservador se mantiene el mayor pico Spring
observado, ~307 MiB, no solo el valor menor de la segunda ejecución.

El candidato ajustado cumple pico <460,8 MiB por servicio y las muestras de
calentamiento/carga quedan bajo 384 MiB. **MemoryHigh de systemd no se aplicó**:
Docker sí impuso el máximo de memoria y CPU. Las muestras por fase no son una
medición continua de permanencia bajo MemoryHigh. cpu.stat acredita throttling,
especialmente durante el arranque; las latencias ya incluyen esa cuota.

## Latencias y resultados funcionales

| Medición | Base | Ajustada |
|---|---:|---:|
| Calentamiento p95 | 444 ms | 545 ms |
| Calentamiento p99 / máximo | 3692 ms | 3311 ms |
| Carga p95 | 126 ms | 116 ms |
| Carga p99 | 409 ms | 339 ms |
| Carga máximo | 600 ms | 605 ms |
| HTTP fallidos | 0 | 0 |
| Eventos esperados / persistidos | 142 / 142 | 142 / 142 |

Los cuantiles son muestrales. La fase de carga duró aproximadamente un minuto;
incluye 120 peticiones a 2/s y el grupo final de cuatro duplicados concurrentes.
No es un ensayo de 2/s con concurrencia ilimitada ni una prueba de lotes de 1000
eventos. Los eventos tienen texto corto sintético, no archivos ni cuerpos de 3 MiB.

Se cumplen p95 <=1 s y p99 <=3 s **en carga posterior al calentamiento**.
No se cumple p99 <=3 s en frío. Spring base tardó ~55 s en arrancar con CPU 0,5;
GET challenge disponible no demuestra que la primera escritura esté caliente.
No ocultar ese incumplimiento mediante promedios o retirando muestras.

## Evidencias y archivos

- `qa/runtime/wa-c3-a2f5ce853e/informe.json`: base.
- `qa/runtime/wa-c3-46a05cbe17/informe.json`: ajustada.
- `qa/whatsapp/local-isolation/evidencias-3.2-C.3.json`: resumen saneado
  conservado fuera de runtime, con métricas, gates y hashes de herramientas.
- `resources_linux.py`: orquestador aislado; `LoadProbe.java`: cliente sintético;
  `summarize_resources.py`: lectura/resumen sin iniciar recursos; README ampliado.
- Verificación de sintaxis Python y **7 pruebas estáticas de plantillas correctas**.

Los diez contenedores creados (cinco por ensayo) quedaron `exited`, sin reinicios
automáticos. Volúmenes y redes internas propios conservados para revisión; no se
limpió Docker globalmente. Credenciales y logs crudos en carpetas privadas con
ACL del usuario y SYSTEM, ignoradas por Git. No hay secretos en el resumen.

## Qué está listo, qué falta y decisión siguiente

**Listo:** comparación MySQL y explicación de consumo; candidato ajustado dentro
de 512 MiB; Spring validado con límites Linux reales; carga controlada sin
pérdida/duplicación y evidencias documentadas. No hace falta ampliar cuotas
para esta carga de recepción.

**Falta antes del despliegue:** resolver/probar calentamiento y admisión en frío;
baseline Ubuntu de 24 h e impacto sobre aplicaciones existentes; Nginx real,
TLS MySQL, certificados/Certbot y operación/retención/reversión. Una prueba local
no cierra esos gates. El presupuesto no se extiende a OCR o trabajadores.

**Usuario:** no ejecutar comandos en Ubuntu para esta entrega. Revisar y aceptar
o descartar la variante QA sin performance_schema antes de preparar su aplicación.
Ninguna aprobación de este informe implica aplicar cambios remotos.

**Estado:** C.3 ejecutada localmente, con incumplimiento de latencia fría y
limitaciones declaradas. Skill 4.5 sin definición disponible; no certificada.
Despliegue pendiente de evidencias y autorización específica.
