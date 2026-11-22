# Skill 4.5 — Etapa 3.2-C.5: validación final de Ubuntu para WhatsApp QA

Fecha: 16 de noviembre de 2026, America/Santiago.
Procedimiento de esta etapa: documento de Skill 4.5 suministrado por el usuario
en el adjunto `c2a2af44-939d-478c-b85a-579f15334bf8/Texto pegado.txt`.
Los informes anteriores indicaban que no se había localizado la Skill; este
adjunto permite aplicar ahora su procedimiento C.5, sin inventar otros requisitos.

## 1. Resumen ejecutivo y recomendación

**NO APTO para autorizar el despliegue en este momento.**

El flujo de recepción durable y su aislamiento local disponen de evidencias
válidas y reutilizables. No se han repetido esas pruebas. Faltan evidencias
obligatorias del host compartido, administración única de certificados,
aislamiento efectivo y recuperación operativa en Ubuntu. Además, las plantillas
no representan todavía exactamente el candidato de esta etapa.

Esta revisión es documental y de archivos locales; **no es una nueva lectura
del servidor**. No se ejecutaron comandos remotos, renovaciones/dry-run ACME,
pruebas de carga, cambios de infraestructura, instalaciones, SQL remoto, commits,
push o despliegues. Solo se añade este informe; trabajo concurrente de Git,
especialmente identidad y autenticación, conservado.

Destino del diseño: Ubuntu `157.230.187.214`, usuario operador `carolina`,
dominio `wa-qa.dimabug.casol.cl`, receptor **127.0.0.1:8082** según el adjunto.
Staging (8081), MySQL existente (3306), Avanza y Buscador de Errores deben permanecer
intactos. Los puertos/configuración/UFW descritos en el adjunto son contexto
declarado; su estado actual no fue verificado remotamente en C.5.

## 2. Evidencias revisadas y alcance de aprobación

| Fuente | Evidencia reutilizada | Límite |
|---|---|---|
| C.1, `WhatsApp_3_2_C_1_resolucion_riesgos.md` | Inventario remoto fechado 15/11: dos renovadores, recursos y plan de reversión | Histórico; no acredita estado remoto de hoy |
| C.2, `WhatsApp_3_2_C_2_consolidado_minimo.md` | Salidas del operador: lineages/plugins/hooks, vigencia local, límites/slices, capacidad instantánea | No sustituye baseline ni certificado servido |
| C.2, `../07_Pruebas/QA_aislamiento_local_WhatsApp_3_2_C_2.md` | JAR mínimo y recepción durable, deduplicación y reinicio MySQL | Base de prueba, sin efectos de negocio |
| C.3, `../07_Pruebas/QA_recursos_locales_WhatsApp_3_2_C_3.md` | Cgroups Linux 512 MiB y CPU 0,5 por servicio; variante MySQL ajustada | Carga acotada de un minuto, no host compartido |
| C.4 funcional, `../07_Pruebas/QA_Nginx_HTTPS_local_2026-11-16.md` | Nginx real, HMAC, error SQL controlado y privacidad | Certificado sintético, configuración completa aislada, JDBC local sin TLS |
| Resúmenes JSON en `qa/whatsapp/local-isolation` | Métricas, hashes y resultados saneados C.3/HTTPS | Se mantienen logs y secretos crudos fuera de Git |

El informe HTTPS anterior no llevaba C.4 en el nombre: se usa como evidencia
funcional correspondiente, no se inventa una etapa adicional aprobada.

### Verificación local de identidad del artefacto, realizada en C.5

Se volvió a leer el JAR, sin ejecutarlo ni reconstruirlo:

- SHA256 coincide con el manifiesto:
  `f7242bdb10b846fddca4c934a52a36a5cb4941db30da1ce946f75980fa504523`.
- 46 clases de aplicación; Start-Class `cl.casol.qa.RecepcionQa`.
- Ninguna clase de aplicación fuera de WhatsApp/QA; sin adaptadores OCR/búsqueda,
  configuraciones generales de trabajadores ni bootstrap general del backend.
- Hashes de todas las fuentes seleccionadas siguen coincidiendo con el build.
- Composition root explícito sin component scan general. Rechaza procesamiento,
  medios, envío y conexión Meta; recepción apagada por defecto. Persistencia JPA
  se conecta mediante el puerto existente, sin romper arquitectura hexagonal.
- Puede incluir contratos/tipos de imagen en el dominio o puertos: no son un
  motor OCR ni un trabajador activo. No se empaquetó el frontend/Tesseract.

**Aprobado como evidencia local, no como instalación:** 512 MiB por servicio en
la carga ajustada; MySQL pico 272,8 MiB al inicializar y 146,4 MiB en operación;
Spring mayor pico conservador 306,8 MiB. C.3 persistió 142/142 eventos por ensayo.
HTTPS local: 17 solicitudes, primer POST 2841 ms y arranque Spring 62,083 s;
cuatro duplicados producen una aceptación y tres duplicados. SQL no disponible
devuelve 503 sin evento confirmado; reintento posterior correcto. Cero coincidencias
de secretos/marcadores en los logs revisados, conservando errores SQL técnicos.
Una muestra fría no acredita p99; no declara resuelta la latencia fría de C.3.

## 3. Certbot: estado, comprobación y relevo propuesto

La última evidencia acredita administración **mixta**, no un propietario único:

| Elemento | Evidencia fechada |
|---|---|
| APT | 2.9.0, `/usr/bin/certbot`, `certbot.timer` y `certbot.service` |
| Snap | 5.10.0, lanzador `/snap/bin/certbot`, `snap.certbot.renew.timer`/servicio |
| Alias interactivo | `/usr/local/bin/certbot` apunta a Snap, no es tercera instalación |
| Ejecuciones 15/11 | Ambas versiones comprobaron las mismas tres lineages sin necesitar renovación |
| Plugins | Tres lineages nginx/nginx; directorio certbot_nginx presente en Snap |
| Hooks revisados | Ninguno en archivos de las tres lineages; sin archivos/enlaces hasta profundidad 2 en renewal-hooks |
| Historial parcial | Indicio de éxito para staging con 5.8.0 el 11/10; no atribuye todos los certificados a Snap |

Certificados locales leídos: avanza vence 07/01/2027, buscadorerrores 27/12/2026,
staging 09/01/2027, UTC; SAN coincide con cada dominio. Estaban vigentes al
leerlos, pero faltan cadena/certificado efectivamente servido y estado actualizado.
Versión en renewal.conf y salida 0 no prueban renovación ni propiedad exclusiva.

**Snap sigue siendo candidato, no gestor aprobado.** Para cerrar el gate:

1. Inventario efectivo acotado: versiones/revisión, timers/overrides, cli.ini,
   config/work/log dirs, hooks y programadores adicionales. No volcar entornos
   o comandos privados completos. No repetir búsquedas de historial indefinidamente.
2. Correlacionar éxitos disponibles por lineage con fecha, versión, resultado y
   certificado público/fingerprint. Si el historial no alcanza, dejar constancia
   y sustituir la incertidumbre por validación prospectiva controlada del candidato.
3. Respaldos privados verificables, custodio, ventana y recuperación acordados.
4. Prueba de compatibilidad de Snap con las tres lineages, **con autorización
   específica posterior**. Un dry-run puede contactar ACME y modificar Nginx o
   ejecutar acciones: no es lectura y no se realiza en C.5.
5. Solo tras aprobar: esperar fin de ejecuciones activas; relevo supervisado del
   programador sobrante sin desinstalar APT ni borrar lineages. Verificar un único
   programador, siguiente disparo y alerta de fallo/caducidad.

Reversión: registrar estados previos; recuperar programación con un binario
compatible con renewalparams que hayan quedado. No reactivar APT a ciegas tras
cambios de formato ni sustituir certificados nuevos por copias antiguas. No borrar
locks ni interrumpir una renovación en curso. No iniciar el certificado QA mientras
la administración y el respaldo sigan sin resolver.

## 4. Capacidad: plan mínimo representativo de 24 horas

### Presupuesto candidato, no aprobado en el servidor

| Recurso | Spring QA | MySQL QA |
|---|---:|---:|
| MemoryMax / MemoryHigh | 512 / 384 MiB | 512 / 384 MiB |
| CPUQuota | 50% de una CPU | 50% de una CPU, según C.1 |
| CPUWeight / IOWeight / TasksMax | 25 / 25 / 96 | 25 / 25 / 96 |
| JVM o buffer pool | heap 64–192, direct 48, metaspace 96, codecache 48 MiB | buffer 128 MiB, redo 128 MiB, 8 conexiones |
| Concurrencia | Tomcat 8, JDBC 2 | Instancia exclusiva |

Máximo conjunto: 1 GiB y una CPU (sumando cuotas) para los dos procesos. Nginx
existente, kernel, logs, cachés, copias y aplicaciones necesitan margen adicional.
La cuota no reserva CPU y la swap no cuenta como capacidad de RAM. La captura
anterior de 2261 MiB disponibles no prueba suficiencia en picos. El pico histórico
de system.slice ~3473 MiB incluye caché y no puede restarse como si fuera RSS.

### Recogida diseñada, no iniciada ni programada

Priorizar archivos históricos de sysstat/monitorización **ya existentes**. No
instalar sar, agentes ni timers; no mantener una sesión o script remoto 24 h sin
otra autorización. Primero comprobar disponibilidad/cobertura con metadatos.

| Señal | Recogida propuesta | Resumen mínimo |
|---|---|---|
| RAM | MemAvailable/total y consumo por cgroup cuando exista; cada minuto | mínimo, p5 y máximos por servicio; no solo promedio |
| CPU | carga, utilización, iowait y steal por minuto | p95/máximo y ventanas sostenidas de saturación |
| Swap | uso y tasas de entrada/salida por minuto | máximo y episodios de actividad sostenida |
| Disco | libre, inodos, crecimiento y latencia/I/O | mínimo libre, tendencia y presupuesto de datos/logs/backup |
| Aplicaciones | métricas existentes de latencia y errores; si no existen, proponer después una sonda GET inocua a ruta acordada por aplicación | conteos, p50/p95/p99, máximos, timeouts/5xx; sin URI/query/cuerpos/IP en anexos |
| Operación | ventanas de carga, backups, tareas y renovación que ya ocurran | correlación temporal, huecos y causas |

Las sondas HTTP generan tráfico/logs: no se ejecutan automáticamente como simple
lectura. Usar rutas sin autenticación ni efectos acordadas por el operador, sin
cookies o tokens, frecuencia inicial máxima una por cinco minutos por aplicación,
timeouts/reintentos acotados y sin registrar contenido. Para p99 con muestras
escasas, indicar tamaño y no inferir un SLO robusto a partir de unos pocos GET.

Ventana: 24 horas continuas representativas, zona UTC/Santiago registrada,
incluyendo picos normales de staging, Avanza y Buscador. Si no cubre tareas
relevantes, ampliar usando histórico; no forzar backups o renovaciones para
rellenarla. Informar intervalos perdidos; no interpolarlos como capacidad libre.
Datos existentes de baja frecuencia son útiles, pero no capturan todos los picos.

Criterios candidatos C.1: mínimo baseline MemAvailable >=2 GiB para presupuestar
1 GiB QA + reserva de 1 GiB; disco reservado host >=5 GiB además del presupuesto
QA completo; sin OOM ni swap sostenida como sustituto de RAM. Definir antes de
una prueba posterior la tolerancia de latencia/error por aplicación y ventanas
de saturación CPU/I/O. El operador debe aceptar esos umbrales; no son leyes
universales ni están demostrados hoy. Si fallan, elegir capacidad/VM separada.

En un piloto futuro autorizado: alertar al caer la reserva de 1 GiB; contener QA
si se aproxima a 768 MiB o aparece OOM. No detener aplicaciones existentes.
Un baseline favorable permite decidir; no prueba por sí solo el impacto de QA:
hará falta comparación posterior controlada, sin simular fallos de Ubuntu en C.5.

## 5. Coherencia de configuración y riesgos abiertos

| Hallazgo actual | Riesgo / acción previa a instalación |
|---|---|
| Adjuntos C.5 proponen 8082; application.properties y proxy_pass usan 18132 | Canonizar **127.0.0.1:8082** en un futuro diff local revisado, comprobar que está libre y validar solo el cambio de integración pertinente. No instalar plantillas actuales sin ese diff. |
| Nginx propuesto escucha 127.0.0.1:18443 | Es puerta local de preparación, no exposición pública aprobada. Diseñar aparte vhost 443 cuando se autorice. No abrir 8082, 13328 ni 18443 en UFW. |
| Cuotas unitarias actuales coinciden con C.1 | 512 MiB/384 MiB/50%/96 tareas; JVM/JDBC/Tomcat y tasa Nginx coherentes. Coherencia numérica no equivale a capacidad remota validada. |
| mysqld-qa.cnf.template no incorpora variante ajustada C.3 | performance_schema sigue sin OFF explícito y faltan cachés/temporales reducidos. Esa plantilla corresponde a una base que llegó al límite durante inicialización. Aceptar coste de diagnóstico, preparar diff fiel y verificar valores efectivos; no aplicar el MySQL existente. |
| socketTimeout=10000 y VERIFY_IDENTITY en plantilla; ensayo usó 5000 y JDBC sin TLS | El 503 medido a ~5,3 s no acredita latencia con esos ajustes/TLS. Validar confianza, SAN, permisos y timeout elegido antes del piloto; no bajar a TLS deshabilitado como atajo. |
| Type=simple y TimeoutStartSec=90s | systemd considera activo el proceso antes de HTTP/readiness; ese timeout no prueba que Spring esté listo. Preparar gate de readiness operativo acotado (GET seguro + comprobación SQL, sin secretos en argumentos/logs) y política de calentamiento. |
| IPAddressAllow=localhost | Bloquea salida no local si el control es efectivo, pero permite alcanzar otros puertos loopback, incluidos 3306/8081. No acredita aislamiento frente a producción. Revisar control de puertos/namespaces aplicable exclusivamente a QA, grants mínimos y configuración efectiva. |
| Logs Spring/MySQL van al journal por unidad | Separación lógica, no cuota privada de disco probada. Retención, permisos y límites por QA pendientes; no cambiar journald global para resolverlos. |
| No nueva auditoría UFW/Nginx/puertos ni baseline | Contexto declarado e inventario histórico no sustituyen estado efectivo actual. Requiere lecturas mínimas del operador, una por vez. |

No se modificaron plantillas ni configuraciones para ocultar estas diferencias
en C.5. Los documentos históricos mantienen sus cifras como antecedentes; este
informe distingue candidato actual, implementación propuesta y estado probado.

## 6. Aislamiento y protección de aplicaciones

| Control | Diseño existente | Evidencia pendiente en Ubuntu |
|---|---|---|
| Servicio/usuario | dimabug-wa-qa.service; usuario y grupo dimabug-wa-qa | No colisión de nombres/UID; membresías y permisos reales |
| MySQL exclusivo | dimabug-wa-qa-mysql.service; usuario dimabug-wa-mysql; 13328; datadir/socket propios | Provisionamiento posterior, AppArmor compatible sin relajar instancia existente; no acceso a /var/lib/mysql |
| Directorios | /opt/dimabug-wa-qa; /etc/dimabug-wa-qa; /var/lib/dimabug-wa-qa y datadir propio; UMask 0077, directorios 0700 | Propietarios, permisos, disco y archivo APPROVED bajo custodia del operador; el usuario del servicio no debe poder autoaprobarse |
| Credenciales | LoadCredential/configtree, claves distintas de cifrado/HMAC, cuenta DML de DB QA | Crear/custodiar exclusivamente en fase aprobada; sin herencia de staging ni copia de secretos productivos |
| Logs | Identidad/unidad QA, Nginx técnico sin URL/query/cuerpo; Hibernate pooling WARN | Política global Nginx, journal, accesos y cuotas; alerta técnica que compense no guardar errores con URLs |
| Red | Bind 127.0.0.1, deny no-local y flags Meta false | Efectividad cgroup/BPF/UFW, aislamiento de puertos, sin acceso a bases/servicios existentes |
| Artefacto | JAR seleccionado y hash verificados; flags y entidades delimitados | Copia futura identificada, permisos solo lectura para runtime; no sustituir por JAR general |
| Datos | Tres tablas en DB nueva, ddl validate; Flyway/Liquibase/init SQL deshabilitados | TLS/grants/backup; aplicar SQL canónico solo a DB QA bajo permiso específico |

Las unidades nuevas no deben depender de reiniciar mysql.service ni
dimabug@staging.service. La ausencia de transportes Meta y el artefacto mínimo
son controles fuertes, pero no convierten localhost completo en una red aislada.

## 7. Diseño Nginx exclusivo — sin aplicación

Topología futura: cliente HTTPS -> vhost **wa-qa.dimabug.casol.cl:443** ->
**127.0.0.1:8082** -> MySQL QA **127.0.0.1:13328**. Separación de archivos y logs
por vhost, sin modificar las rutas/upstreams de staging, Avanza o Buscador.

- Certificado válido de lineage independiente con SAN QA; sin añadir el dominio
  a certificados de otras aplicaciones. Certbot único y custodia resueltos primero.
- Solo GET/POST y ruta cruda exacta `/webhooks/whatsapp`, query de verificación
  permitida pero nunca registrada. Rechazar rutas codificadas/alternativas y
  métodos extra; API, Actuator y Swagger quedan cerrados.
- No proxy del webhook por HTTP claro. Si se aprueba HTTP-01 para emisión, definir
  únicamente la excepción ACME necesaria bajo otra autorización; no habilitar
  proxy general ni modificar otros vhosts para obtener el certificado.
- TLS 1.2/1.3; cuerpo 3 MiB, buffer 64 KiB y temporales QA restringidos; body/header
  timeout 10 s, connect 3 s y lectura/envío proxy 25 s, revisados contra JDBC/TLS
  y plazo de acuse. Rate 2/s, burst 4 y ocho conexiones como candidato inicial.
- Preservar bytes y X-Hub-Signature-256; no retries de upstream ni reserialización;
  vaciar Authorization/Cookie/Forwarded y similares como en plantilla local.
- Access log técnico de estado/bytes/latencia/upstream; sin cuerpos, tokens,
  teléfono, IP, URL ni query. Auditar también global/default-server, incluidos
  errores de TLS/host desconocido; no basta el log del vhost QA.
- Vhost nuevo sin convertirse accidentalmente en default; revisión de includes,
  nombres de zonas/mapas y bind para evitar colisiones. UFW conserva solo la
  entrada pública que se autorice; nunca expone receptor o MySQL.

Antes de instalar: diff exacto y validación offline de la configuración completa.
Luego, únicamente con permiso de cambio: nginx -t del conjunto, recarga controlada
si válida y comparación antes/después de los tres sitios. No se ejecuta nginx -t
remoto, emisión, cambio de firewall ni reload en esta etapa.

## 8. Criterios de aceptación de infraestructura

| Gate obligatorio | Estado C.5 | Evidencia necesaria |
|---|---|---|
| JAR aislado e identificado | VERIFICADO LOCAL | Revalidar hash del archivo que se proponga instalar |
| Flujo HTTPS/HMAC/dedup/error SQL | APROBADO LOCAL, alcance C.4 | Reutilizar; repetir solo configuración cambiada o riesgo nuevo |
| Certbot único compatible | PENDIENTE | Inventario efectivo, validación autorizada, respaldo y un programador verificado |
| Baseline representativo 24 h | PARCIAL, actualización 17/11 | Resumen sar RAM/CPU/swap disponible; completar cobertura, disco y latencias |
| Configuración fiel a variante aprobada | NO COINCIDE TODAVÍA | Puerto 8082, ajustes MySQL, TLS/timeouts y readiness en diff coherente |
| Controles efectivos/permiso mínimo | PENDIENTE REMOTO | Puertos, UFW, usuarios, ACL, cgroups, red y grants sin producción |
| Certificado QA y Nginx Ubuntu | DISEÑO | Autorización separada; cert y vhost revisados sin interferencia |
| Contención/recuperación y custodia | DISEÑO | Runbook, responsable, alarmas, respaldos restaurables y verificación de sitios |

Con gates obligatorios abiertos no corresponde APTO ni una autorización genérica
APTO CON CONDICIONES para instalar. NO APTO describe la decisión de despliegue;
no invalida las pruebas locales aprobadas.

## 9. Procedimiento propuesto de despliegue — NO autorizado

1. Cerrar evidencias de lectura y baseline; acordar umbrales, ventana, responsable,
   backups/custodio y condiciones de aborto. Preparar lista exacta de archivos,
   hashes, objetivos y diferencias; aceptar variante MySQL y puerto 8082.
2. Autorizar por separado resolución Certbot y verificar un renovador compatible.
   No agrupar con DNS, certificado nuevo, instalación o Meta.
3. Preparar/validar offline configuración revisable, política de red y readiness;
   TLS MySQL y artefacto identificados. Sin subir límites para pasar pruebas.
4. Con permiso explícito de aprovisionamiento: crear solo usuarios/directorios,
   instancia/base QA y credenciales nuevas; aplicar solo SQL canónico aprobado;
   verificar grants/TLS, rutas y hashes. Ninguna migración en bases existentes.
5. Con permiso de piloto local: iniciar solo QA, flags apagados; comprobar cgroups,
   readiness y luego recepción sintética acotada. Observar impacto frente al baseline
   de las otras aplicaciones; no pausar ni reiniciar servicios existentes.
6. Con permiso específico: DNS/certificado/vhost y eventual apertura pública;
   validar antes de reload y comprobar sitios antes/después. Verificar certificado
   servido con SNI/SAN/cadena y privacidad global. Mantener recepción pausada hasta
   aceptación de todos los controles y del calentamiento.
7. Solo después de otra etapa/autorización: considerar conexión Meta o participantes
   reales. C.5 no las habilita, y el JAR actual sigue excluyendo procesamiento/envío.

Abortar ante OOM, presión de reserva, alteración de otras aplicaciones, fallo de
aislamiento/privacidad, divergencia de hash/configuración, certificado inseguro
o pérdida de supervisión. El inicio de un proceso no es aceptación del piloto.

## 10. Recuperación, contención y reversión propuestas

- **Contener:** bloquear solo admisión del vhost QA mediante cambio previamente
  autorizado o mecanismo QA probado; detener solo dimabug-wa-qa.service si es
  necesario. Flags actuales se leen al arrancar: editar un archivo no garantiza
  una pausa inmediata. El control operativo todavía necesita implementación/ensayo.
- **Preservar:** datos pendientes, claves de cifrado/HMAC, logs saneados, hash de
  JAR, configuración y estado del incidente bajo acceso privado. No borrar eventos
  pendientes para liberar espacio. Una pausa no congela TTL.
- **MySQL:** conservar la instancia exclusiva mientras se comprueba integridad;
  si hay que detenerla, hacerlo después de cortar QA y únicamente su unidad. No
  detener mysql.service existente ni restaurar un dump antiguo sobre eventos nuevos.
- **Recuperar:** validar disco, TLS, claves, esquema, cgroups y MySQL; arrancar
  solo QA con recepción pausada, comprobar readiness/privacidad y decidir sobre
  expiraciones antes de reanudar. Solo se recibirá/deduplicará con este artefacto;
  no atribuir recuperación de trabajadores que no contiene.
- **Revertir release/config:** volver a versión QA anterior compatible mediante
  procedimiento aprobado, conservar datos y validar hash. Si cambia semántica
  del esquema, no improvisar downgrade. No borrar DB/datadir o usuarios en reversión.
- **Nginx:** revertir únicamente el diff de QA, comprobar sintaxis y recargar solo
  con permiso; Nginx es compartido, no reiniciarlo. Volver a comprobar estados y
  latencias de staging, Avanza y Buscador, no solo una URL QA.
- **Certbot:** aplicar el retorno compatible de la sección 3; nunca reemplazar
  certificados vigentes nuevos a ciegas ni dejar renovación suspendida indefinidamente.

Requiere responsable y contacto de escalamiento definidos, tiempo máximo,
custodia/retención aprobada y ensayo en entorno aislado. **No se simularon fallos
en Ubuntu en C.5.** Recuperación local anterior no prueba este runbook remoto.

## 11. Gestión Jira — identificación, sin crear ni modificar tickets

| Campo | Valor solicitado |
|---|---|
| Épica | Integración WhatsApp Cloud API |
| Historia | Preparación segura del entorno WhatsApp QA |
| Tarea | Validación final de infraestructura Ubuntu |
| Subtareas | Certbot; capacidad; aislamiento; Nginx; recuperación |
| Sprint | QA y estabilización del MVP |
| Estado propuesto | Auditoría documentada; validación remota pendiente; despliegue bloqueado |

No se consultó ni escribió Jira; no se inventan claves o enlaces de tickets.

## 12. Próxima información mínima del operador y estado de la Skill

Primera comprobación concluida por el operador: `sar` disponible en /usr/bin/sar
y archivos históricos sa17, sa16 y sa15 bajo /var/log/sysstat. El resumen de sa16
ya se recibió; se incorpora en la actualización siguiente. No se instaló ni
activó recopilación. La próxima lectura propuesta es espacio/inodos actuales
de /var/lib y /var/log, sin sudo. Esperar el resultado del operador antes de
pedir otra lectura o decidir una recogida nueva.

Lecturas posteriores, solo si siguen faltando: estado efectivo acotado de Certbot,
puertos/UFW y metadatos/includes Nginx. Un comando cada vez, con salida filtrada;
no dumps de configuración/env ni contraseñas/llaves en el chat.

**Skill 4.5 / C.5:** procedimiento recibido y aplicado; revisión y preparación
documentadas. Validación final de infraestructura **pendiente de evidencias**;
no se certifica Ubuntu ni se cierra la etapa como infraestructura aprobada.
No se avanza automáticamente a otra etapa. Recomendación actual: **NO APTO**.

## 13. Actualización del 17/11: histórico sar aportado por el operador

Origen: salida filtrada ejecutada por carolina; Codex no abrió conexión remota.
Inventario previo: sar instalado; nueve archivos históricos; sa16 con última
modificación 16/11/2026 23:50 UTC. La salida de métricas no incluye cabecera de
fecha/zona: no se asume que sus horas estén expresadas en Santiago.

| Señal en sa16 (143 muestras por métrica) | Resultado |
|---|---|
| Horas impresas | 00:10:01–23:50:01; intervalo habitual 10 minutos |
| RAM disponible | mínimo 2222572 KiB = **2170,5 MiB**; media 2227,9 MiB; máximo 2307,9 MiB |
| CPU idle | mínimo 79,70%; media 97,81%; máximo 98,88% |
| CPU no idle (100-idle) | mayor media de intervalo 20,30%; media 2,19%; incluye iowait/steal, no solo aplicación |
| iowait | media 0,03%; máximo 1,09% |
| steal | media 0,85%; máximo 2,54% |
| Swap ocupada | 4152–8776 KiB (~4,1–8,6 MiB); 0,20–0,42% |
| Swap in | cero en todas las muestras |
| Swap out | media 0,01 páginas/s; máximo 1,37 páginas/s |

**Lectura:** favorable para seguir auditando, sin señal de saturación CPU o swap
intensa en ese resumen. El mínimo observado supera 2048 MiB por **122,5 MiB**.
Restar el máximo candidato QA de 1024 MiB dejaría **1146,5 MiB**, apenas 122,5 MiB
por encima de la reserva objetivo de 1 GiB. Es aritmética sobre muestras sin QA,
no una garantía de memoria disponible tras instalarlo.

Cobertura: 23 h 40 min entre las horas mostradas y 143 muestras coherentes con
cadencia de diez minutos. No son 24 h completas entre extremos, ni se verificaron
en esta salida fecha/zona, continuidad exacta, tareas representativas o reinicios.
La CPU es media de intervalo y la RAM una muestra: pueden ocultar picos breves.
No declarar 20,3% como máximo instantáneo de CPU ni 2170,5 MiB como mínimo continuo.

Faltan espacio/inodos y su evolución, I/O detallado, latencias/errores de las
aplicaciones existentes y representatividad de la ventana. El siguiente comando
del operador solo medirá disco **actual**, sin sustituir historial de crecimiento.
Certbot, coherencia de plantillas, controles efectivos y recuperación siguen
pendientes. La recomendación permanece **NO APTO para desplegar**.

## 14. Actualización del 17/11: espacio e inodos actuales

Salida del operador, mediante df de solo lectura, sin conexión remota de Codex:

- /var/lib y /var/log pertenecen ambos al filesystem montado en `/`.
- Tamaño informado: 23731 MiB; disponible: **10373 MiB (~10,13 GiB)**;
  uso: 57%. Las dos filas son el mismo espacio, no 20746 MiB disponibles.
- Inodos totales: 3145728; libres: 2948741; uso informado: 7%.
- Supera la reserva candidata del host de 5120 MiB por **5253 MiB**. Ese excedente
  debe cubrir todo QA: datadir, redo/índices, temporales, logs y copia privada,
  además del crecimiento de las aplicaciones existentes. No es una cuota aplicada.

**Estado:** espacio/inodos actuales favorables, sin señal de agotamiento en la
captura. No acredita tendencia de 24 h, crecimiento, I/O ni retención. En una
partición compartida, ningún límite MemoryMax impide que datos/logs agoten disco.
Las latencias/errores de las aplicaciones aún no están acreditadas; se propone
comprobar primero si sus formatos de log ya contienen tiempos de respuesta,
sin imprimir configuración completa ni registros de solicitudes. No se instalan
sondas o recolectores ni se envía tráfico por esta comprobación.

No cambia la recomendación **NO APTO para despliegue**: siguen abiertos Certbot,
baseline completo, coherencia de plantillas, controles efectivos y operación.

## 15. Lectura de formatos Nginx: resultado no concluyente

El operador devolvió `nginx.conf {'gzip': 'NO DETERMINADO'}`. Se identificó un
error en el filtro propuesto por Codex: el grupo opcional de formato podía cruzar
el punto y coma y consumir la siguiente directiva. Esa salida **no demuestra**
un formato gzip, error de Nginx ni ausencia de latencias registradas.

Se corrigió el análisis de access_log para terminar primero en `;` y separar
después sus argumentos. Tres comprobaciones locales del filtro (formato omitido
seguido de gzip, formato explícito con buffer y access_log off) fueron correctas.
Son pruebas del analizador, no repetición de las pruebas funcionales aprobadas.
Se solicitará al operador una única lectura corregida, de salida acotada y sin
mostrar rutas de logs, formato completo o líneas de solicitudes. No se modificó
Nginx ni se ejecutó conexión remota; la evidencia de latencias sigue pendiente.

## 16. Formato Nginx confirmado por la lectura corregida

El operador devolvió `nginx.conf {'combined': False}`. El filtro identificó
access_log con formato combined y sin request_time/upstream_response_time en
los archivos legibles revisados (nginx.conf, conf.d y sites-enabled). No se
imprimieron líneas de solicitudes ni configuración completa.

**Conclusión acotada:** ese formato no permite reconstruir tiempos de respuesta
históricos. No equivale a demostrar que toda monitorización del servidor carezca
de latencias: puede haber herramientas existentes u otros includes no cubiertos.
Un access log combined puede servir para conteos de estado, pero no para inventar
p95/p99. No se leerán logs crudos con URL/IP/token para sustituir este dato.

Para cerrar latencias: usar métricas ya existentes si el operador las identifica;
en su ausencia, acordar rutas inocuas, ventana y recogida acotada de nuevas sondas
bajo autorización específica, sin instalar agentes o editar Nginx automáticamente.
Las sondas nuevas no reconstruyen el día histórico ni acreditan ausencia de
regresión frente a un baseline inexistente. No repetir esta búsqueda de formatos.

Próxima lectura propuesta: listeners de 8081/3306 y puertos QA candidatos
8082/13328. Es metadato TCP, no una conexión a aplicaciones ni una apertura de
puertos. La captura solo acreditará ese instante, no una reserva de puertos.
La recomendación sigue **NO APTO para desplegar**.

## 17. Listeners TCP aportados por el operador

Filtro ss de solo lectura para 8081, 8082, 3306 y 13328:

- MySQL existente: **127.0.0.1:3306**.
- Staging: **[::ffff:127.0.0.1]:8081**, representación IPv4-mapped de loopback,
  no listener en todas las interfaces IPv6.
- No se mostraron listeners TCP en **8082** ni **13328** en esa captura.

La columna de peer `0.0.0.0:*` o `*:*` en LISTEN no es la dirección local de
escucha ni demuestra exposición pública. No se conectó a esos servicios, ni se
verificaron por esta vía su identidad de proceso, salud o latencia.
Los puertos QA parecen disponibles, pero no están reservados; revalidar antes
de cualquier aprovisionamiento autorizado. Esto no acredita aislamiento por
usuario, UFW ni restricciones de salida.

Siguiente lectura propuesta: `sudo ufw status verbose`, ejecutada por el operador
en Ubuntu, sin cambiar reglas o servicios. Su salida complementa binds; no prueba
por sí sola todas las reglas efectivas de nftables/iptables, firewall del proveedor,
NAT o alcance externo. No abrir puertos como consecuencia de la revisión.

## 18. UFW: estado aportado por el operador

`ufw status verbose` ejecutado por carolina muestra:

- Activo; logging low; política entrante deny, saliente allow, routed disabled.
- Únicas reglas listadas: ALLOW IN para **22/tcp, 80/tcp y 443/tcp**, IPv4 e IPv6.
- Sin reglas de entrada que permitan explícitamente 8081, 8082, 3306 o 13328.

La política entrante reportada es compatible con el diseño: receptor y MySQL QA
deben seguir en loopback, detrás de Nginx por HTTPS. **No se necesita abrir 8082
ni 13328.** La apertura existente de 80/443 no autoriza publicar el vhost ni
emitir certificados. SSH y demás reglas se conservan.

La política saliente allow no bloquea Meta ni otros destinos por proceso. Sigue
siendo obligatorio comprobar controles específicos QA (cgroup/red, configuración,
artefacto y permisos); no endurecer UFW global automáticamente porque afectaría
a otras aplicaciones. Bind loopback y UFW son controles complementarios.

Estado de red actualizado: listeners y política UFW entrante documentados como
favorables en la captura, no como auditoría exhaustiva de nftables/NAT/proveedor.
Falta la efectividad de aislamiento de salida y frente a otros puertos loopback.

La siguiente lectura mínima propuesta actualiza solo ActiveState/UnitFileState
de los dos timers Certbot. No renueva ni altera programación; permitirá confirmar
si persiste el bloqueo de doble administración observado el 15/11. No se reinician
pruebas locales ya aprobadas ni se vuelve a buscar historial indefinidamente.

## 19. Cierre actualizado C.5: temporizadores y decisión

El operador ejecutó systemctl show de solo lectura y aportó:

| Timer | ActiveState | UnitFileState |
|---|---|---|
| certbot.timer | active | enabled |
| snap.certbot.renew.timer | active | enabled |

**Persiste la doble programación.** Esta salida no prueba renovaciones simultáneas
ni atribuye la última emisión, pero confirma que no se ha cerrado la administración
única. Snap permanece candidato. No se deshabilitó ningún timer, ejecutó Certbot,
renovó certificado ni cambió configuración.

### Evidencia disponible al finalizar esta revisión

- Pruebas locales y hash/JAR aislado verificados, sin repetir pruebas aprobadas.
- RAM/CPU/swap: histórico sar favorable en las 143 muestras disponibles; margen
  de RAM adicional sobre el umbral candidato de solo 122,5 MiB, sin garantía
  contra picos breves. Cobertura/representatividad completas aún pendientes.
- Disco actual: 10373 MiB libres y 7% de inodos utilizados en la partición común.
- Nginx combined revisado no aporta tiempos históricos; no se inventa baseline
  de latencias ni se modifican logs para reconstruir el pasado.
- Staging y MySQL existentes en loopback; sin listeners 8082/13328 en la captura.
- UFW activo, entrada deny y solo 22/80/443 permitidos; salida allow, sin prueba
  de aislamiento específico QA por puerto/proceso.
- Ambos timers Certbot activos y habilitados: bloqueo confirmado.

### Pendientes que requieren decisión, no más inventario repetitivo

1. **Certbot:** autorizar separadamente preparación privada de respaldo/ventana,
   validación de Snap para las tres lineages y eventual relevo supervisado. No
   autorizar simplemente apagar APT: compatibilidad y recuperación deben preceder
   al cambio. La prueba ACME/dry-run sigue fuera de esta autorización.
2. **Capacidad y latencias:** identificar monitorización existente o aprobar una
   recogida nueva de 24 h con rutas/frecuencia/tolerancias acordadas. No instalar
   agentes, programar tareas ni generar tráfico sin ese acuerdo. Los históricos
   disponibles son un punto de partida, no una aprobación de capacidad compartida.
3. **Paquete local revisable:** preparar en una fase autorizada el diff coherente
   con 8082, MySQL ajustado, TLS/timeouts, readiness y restricción de salida/loopback.
   Incluye logs/retención y runbook. No instalarlo en Ubuntu al prepararlo.
4. **Aceptación operativa:** cerrar custodia/restaurabilidad, responsables y criterios
   de contención/reversión sin afectar staging, Avanza o Buscador. No simular fallos
   en Ubuntu dentro de C.5.

**Recomendación final de esta revisión: NO APTO para desplegar.** La revisión
documental y las lecturas acotadas solicitadas están registradas. La validación
de infraestructura permanece abierta por esos gates, no por falta de repetir
las pruebas locales. No se piden más comandos en este cierre ni se avanza de etapa.

**Skill 4.5 / C.5:** auditoría y preparación documentadas conforme al adjunto;
infraestructura no aprobada. El siguiente paso depende de una decisión explícita
del usuario sobre los pendientes, no implica autorización de despliegue o Meta.

## 20. Preparación local posterior autorizada, 17/11

El usuario solicitó preparar el relevo reversible Certbot, corregir plantillas,
preparar medición sin ejecutarla y documentar aislamiento/recuperación. Se entrega:

- `WhatsApp_Certbot_relevo_reversible.md`: plan condicionado, comandos futuros
  aparcados, backup/ventana/compatibilidad, único renovador y retorno no ciego.
- `WhatsApp_QA_preparacion_local_operacion.md`: candidato, uso seguro de medición,
  comprobaciones y recuperación exclusivamente QA; gates todavía pendientes.
- Plantillas locales: receptor/proxy **8082**, límites 512/384 MiB sin incrementos,
  MySQL ajustado de C.3 y socketTimeout 5000 conservando TLS VERIFY_IDENTITY.
  El antecedente JSON C.1 se conserva; `candidato-qa-local.json` refleja el actual.
- Colector `baseline_24h.py` y rutas ejemplo: plan por defecto, recogida futura
  explícita en Ubuntu no root, sondas opcionales apagadas y placeholders rechazados.
- **18 pruebas locales correctas** y salida PLAN_ONLY comprobada. No se repitieron
  las pruebas funcionales ya aprobadas; no se inició medición ni tráfico real.

Esta actualización resuelve la discrepancia **local** de puerto/ajustes de las
plantillas señalada en C.5, no instala ni valida su efectividad en Ubuntu.
Readiness, control de salida por puertos, TLS, baseline/latencias y relevo Certbot
siguen sujetos a su validación/autorización específica. Los hechos históricos
de secciones anteriores no se reescriben como si se hubieran aplicado cambios.
**Preparación solicitada completada; despliegue sigue NO APTO/no autorizado.**
