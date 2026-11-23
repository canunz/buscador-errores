# Etapa 3.2-C.1 — Resolución de riesgos previa a despliegue

Fecha: 15 de noviembre de 2026, America/Santiago.

## Alcance y decisión

Esta entrega contiene lectura y diseño. No ejecuta eliminación de duplicidad,
renovación/dry-run ACME, cambios de Ubuntu, DNS, Nginx, certificados, MySQL o
despliegue. No modifica las plantillas anteriores ni el código de DimaBug.

**Resultado:** duplicidad confirmada y resolución diseñada, pero **riesgos aún
no cerrados para autorizar despliegue**. La identidad del último renovador
efectivo por certificado y la capacidad sostenida del host siguen pendientes.
No se sustituye evidencia faltante por una elección basada en la versión más alta.

## 1. Qué administra los certificados

Lectura actualizada a las 12:48 del 15 de noviembre, hora de Santiago
(15:48 UTC). La inspección anterior del día 14 queda como antecedente.

| Aspecto | Evidencia actual | Clasificación |
|---|---|---|
| APT | Certbot/nginx 2.9.0-1, `/usr/bin/certbot` | VERIFICADO |
| Snap | Certbot 5.10.0, revisión 5940 | VERIFICADO |
| Acceso interactivo alternativo | `/usr/local/bin/certbot` y `/snap/bin/certbot` resuelven al lanzador Snap | VERIFICADO |
| Renovación APT | `certbot.timer`: enabled, active/waiting; servicio usa `/usr/bin/certbot` | VERIFICADO |
| Renovación Snap | `snap.certbot.renew.timer`: enabled, active/waiting; servicio invoca Snap renew | VERIFICADO |
| Ejecuciones recientes | Ambos servicios registran resultado 0; estaban inactivos entre ejecuciones | VERIFICADO |
| Última renovación efectiva | Logs detallados no legibles por el usuario disponible | PENDIENTE |
| Gestor exclusivo de todas las lineages | No está demostrado; hay dos programadores | NO DEMOSTRADO |

Las configuraciones de renovación visibles mantienen:

- `avanza.casol.cl`: versión registrada 2.9.0.
- `buscadorerrores.casol.cl`: versión registrada 5.8.0.
- `staging.dimabug.casol.cl`: versión registrada 5.8.0.

Todas indican autenticador e instalador nginx. La versión escrita en una lineage
es histórica: no prueba qué timer realizó la última renovación ni impide por sí
sola que otro binario la procese. Código de salida 0 también puede significar
que no correspondía renovar. No se afirma que ambos procesos renovaran a la vez.

Los timers no declaran otro config-dir. Las lineages examinadas están en
`/etc/letsencrypt`; las referencias de los paquetes al directorio predeterminado
son compatibles con ello, pero deben contrastarse también cli.ini, wrappers y
entorno de los programadores antes del cambio. El cron APT observado se omite
cuando hay systemd; no se interpreta como tercer renovador activo. No se han
descartado aún todos los schedulers o crons privados.

**Determinación válida:** actualmente APT y Snap pueden iniciar renovación. No
existe una asignación exclusiva por dominio acreditada. Elegir uno es un cambio
de administración que debe aprobarse, no un hecho deducible del PATH.

### Evidencia adicional mínima de lectura

Obtener mediante un operador autorizado una salida **filtrada**, nunca logs o
secretos completos, que relacione para cada lineage:

1. Binario, versión/revisión y origen del proceso de renovación.
2. Fecha, dominio/lineage, resultado efectivo y, cuando esté disponible, serial
   o fingerprint público anterior/posterior y vencimiento.
3. Directorios efectivos de configuración, trabajo y logs.
4. Plugins y hooks; identificar qué recargan, sin ejecutar hooks ni imprimir
   credenciales o argumentos completos.
5. Programadores adicionales, overrides y llamadas manuales documentadas.
6. Certificado que efectivamente carga Nginx, con SAN, cadena, vigencia y destino
   de enlaces. Leer certificados públicos no exige mostrar claves privadas.

Los logs `/var/log/letsencrypt/letsencrypt.log` y su rotación no fueron legibles
sin privilegios. No se pidió contraseña ni se cambiaron permisos para leerlos.
La fase debe conservar esta carencia como bloqueo, no marcar C2 aprobado.

## 2. Eliminación segura de la duplicidad — SOLO DISEÑO

**Candidato a validar:** Snap, por ser la instalación que corresponde a la rama
de versión histórica de dos lineages y al acceso interactivo observado. No queda
seleccionado definitivamente ni se asegura que APT sea incompatible. El candidato
debe demostrar compatibilidad con **las tres** lineages, incluido avanza.

Si esa prueba no es satisfactoria, revisar la elección o separar planes por
lineage; no introducir ahora config-dirs nuevos o copiar certificados para
forzar que cada versión tenga una parte del inventario.

### Fase A: preparar una transacción operativa

- Repetir inventario inmediatamente antes del cambio; versiones, revisión Snap,
  timers, cron, hooks, nginx y lineages pueden haber cambiado desde esta lectura.
- Registrar estado enabled/active y próximos disparos. Identificar procesos y
  locks, sin borrarlos ni interrumpir renovaciones en curso.
- Comprobar vencimientos. Una caducidad cercana o un certificado roto requiere
  plan de incidente separado, no experimentar con el cambio de gestor.
- Preparar respaldo privado consistente de configuración, enlaces y material
  necesario para recuperación, preservando permisos. El custodio debe verificar
  hashes/restaurabilidad sin trasladar claves al repositorio, chat o informe.
- Aprobar ventana, responsable, tiempo máximo y mecanismo de restauración de
  programación si se pierde la sesión. No depender de una sesión SSH abierta.

### Fase B: validación del candidato, con permiso específico

- Comprobar plugins, permisos y comportamiento de hooks del candidato antes de
  probar renovación. No asumir que nginx y el plugin coinciden por nombre.
- Validar primero en un entorno aislado o copia privada controlada, sin alterar
  el inventario compartido. Una copia de claves requiere custodia aprobada.
- Un `renew --dry-run` **no es inspección de lectura**: puede contactar ACME,
  hacer desafíos y modificar temporalmente Nginx o ejecutar acciones de hooks.
  Requiere otra autorización, ventana y observación. No se ejecutó aquí.
- Exigir resultados identificables para cada lineage. No aceptar únicamente
  `certbot --version`, `nginx -t`, salida 0 o ausencia de errores.

### Fase C: relevo del programador, solo tras aprobación

No desinstalar paquetes en la primera transición. Conservar ejecutables, plugins,
lineages y backups para recuperación.

Si Snap supera los gates, el cambio propuesto afecta inicialmente **solo** al
programador APT conocido. Esperar que cualquier ejecución en curso termine;
pausar temporalmente su timer durante una ventana supervisada y mantener el
programador elegido operativo. No detener Nginx ni una renovación activa.

Tras validar el candidato y el mecanismo de recuperación, se podrá autorizar
deshabilitar el timer sobrante. No editar unidades del proveedor ni utilizar
un nombre genérico `certbot` para operaciones: registrar rutas absolutas.
El cron APT se conserva mientras se verifique que continúa inoperante bajo
systemd. Otros crons/wrappers tienen que quedar inventariados y resueltos.

Un reinicio del host o cambio de revisión durante la ventana obliga a repetir
preflight: un timer solo detenido temporalmente puede volver a activarse al
arrancar. No dejar una pausa sin vigilancia ni una ventana indefinida sin
renovación. Definir y verificar un mecanismo de recuperación limitado antes de
aplicar, no improvisarlo durante la incidencia.

**No incluye** desinstalar APT, borrar enlaces de `/usr/local/bin`, borrar cron,
ejecutar snap remove/revert, reemitir certificados, editar renewalparams o cambiar
DNS. Cualquiera de esas medidas sería una fase separada si resultara necesaria.

### Aceptación de Certbot

| Gate | Evidencia exigida | Estado actual |
|---|---|---|
| C1 Inventario completo | Binarios, timers, cron/wrappers y configuración efectiva | PARCIAL: faltan ámbitos privilegiados |
| C2 Renovación efectiva | Correlación por las tres lineages y vigencia pública | PENDIENTE |
| C3 Compatibilidad | Prueba autorizada del candidato, plugins y hooks | PENDIENTE |
| C4 Recuperación | Backup privado verificado, estados y procedimiento ensayable | PENDIENTE |
| C5 Programación única | Un solo renovador efectivo, siguiente disparo y alertas | NO CUMPLE: dos timers |

No incorporar el nuevo certificado de `wa-qa.dimabug.casol.cl` hasta resolver
estos gates y aprobar su creación expresamente.

## 3. Revisión de recursos — PROPUESTA NO APLICADA

La lectura actual registra aproximadamente 3,82 GiB de RAM total, 2,18 GiB de
MemAvailable, 2 CPU y 11,1 GiB libres bajo `/var/lib`. La swap existente no se
considera RAM disponible para justificar el despliegue. Una instantánea no
describe los picos de staging, los otros sitios o la renovación.

El presupuesto anterior de 1 GiB Spring + 1 GiB MySQL dejaría unos **0,18 GiB**
del MemAvailable observado antes de otros picos: **no aprobarlo tal cual**.
Las plantillas anteriores conservan sus bytes como antecedente; no son la
configuración revisada ni deben instalarse sin un diff aprobado.

Se propone un candidato reducido exclusivamente para recepción sintética:

| Recurso | Spring QA | MySQL exclusivo QA |
|---|---:|---:|
| MemoryHigh | 384 MiB | 384 MiB |
| MemoryMax | 512 MiB | 512 MiB |
| CPUQuota | 50% de una CPU | 50% de una CPU |
| CPUWeight / IOWeight | 25 / 25 | 25 / 25 |
| TasksMax | 96 | 96 |
| Ajuste principal | Heap 64–192 MiB, direct 48, metaspace 96, code cache 48 MiB | Buffer pool 128 MiB, 8 conexiones |
| Concurrencia inicial | Tomcat 8, pool JDBC 2 | Sin trabajos de otros módulos |

Los dos CPUQuota suman **una CPU**, no dos: en systemd 100% representa una CPU.
Los pesos no reservan capacidad ni sustituyen un límite. Las medidas requieren
cgroup/IO controller disponibles y comprobación efectiva en el host.

El máximo conjunto de memoria sería 1 GiB, dejando aproximadamente 1,18 GiB
del margen observado; no constituye una garantía. El heap no es todo el consumo
JVM y el buffer pool no es todo MySQL. Deben medirse arranque, calentamiento,
metadatos, TLS, threads, cache y recuperación. Si 512 MiB no bastan, **no subir
silenciosamente el límite ni tomar capacidad de producción**: revisar arquitectura,
ampliar capacidad con autorización o elegir otra VM QA.

Recepción Nginx inicial propuesta: 2 solicitudes/s, burst 4 y 8 conexiones QA,
en lugar de la tasa anterior. Mantener controles apagados, salvo recepción
sintética en una prueba autorizada. No habilitar OCR/procesamiento/envío con
este presupuesto sin nueva medición.

### Gates de capacidad y carga

- Artefacto Spring QA mínimo identificado con hash; el JAR general sigue
  bloqueado contra una base de tres tablas.
- Baseline de al menos 24 horas, incluyendo carga normal y renovación del
  gestor acordado. No reemplazarlo por el MemAvailable de esta inspección.
- Objetivo previo: mínimo observado de MemAvailable >=2 GiB para presupuestar
  1 GiB QA y 1 GiB de reserva. Es un umbral inicial a aprobar con el operador,
  no una regla universal. Verificar también después del arranque.
- Carga sintética en entorno aislado: cero OOM/reinicios inesperados, cero
  eventos perdidos o ACK prematuros y recuperación de reservas/DB comprobada.
- Uso sostenido por servicio por debajo de MemoryHigh; pico inferior al 90%
  del MemoryMax. Si no se cumple, bloquear y redimensionar.
- Objetivos iniciales: ACK p95 <=1 s, p99 <=3 s, sin errores inesperados al ritmo
  aprobado; verificar cuerpos límite y cuatro duplicados concurrentes.
- CPU y latencia de los servicios existentes sin regresión material respecto
  del baseline acordado. Sin swap sostenida como sustituto de RAM.
- Alertar con presión de memoria; si MemAvailable cae por debajo de 1 GiB,
  suspender admisión QA y revisar. Si cae por debajo de 768 MiB o hay OOM,
  ejecutar el procedimiento acordado de contención QA, no detener producción.

### Disco y retención

Presupuesto inicial orientativo: datadir QA hasta 2 GiB, temporales 64 MiB y logs
128 MiB, con espacio adicional para al menos una copia privada consistente. Se
requieren límites reales de almacenamiento; una cifra en un documento o un
MemoryMax no limita el crecimiento de ficheros MySQL.

Reservar al menos 5 GiB libres para operación existente según política aprobada.
Antes de activar, calcular el presupuesto completo con backup, índices, redo,
tablas del sistema y retención, usando espacio y carga actuales. Si no cabe,
usar almacenamiento/VM separados; no aplicar cuotas a directorios productivos.

Advertir al 70% del presupuesto QA, detener admisión al 80% y escalar al 85% o
si se amenaza la reserva del host. No borrar eventos pendientes para liberar
espacio. Revisar si el máximo de 30 días de tombstones cabe al ritmo permitido.
Pausar no congela las caducidades de mensajes y propuestas.

El JSON `qa/whatsapp/ubuntu/riesgos-3.2-C.1.json` recoge los valores candidatos;
no es un drop-in instalable ni una aprobación de capacidad.

## 4. Criterios de reversión

### Certbot

Abortar si no puede identificarse la configuración efectiva, aparece otro
renovador, cambia una lineage/Nginx inesperadamente, falla un hook, se pierde la
vigilancia o hay vencimiento próximo sin un gestor compatible demostrado.

Restaurar el estado de programación registrado solo después de comprobar que
los binarios son compatibles con el estado que quedó. Si hubo una renovación
real o actualización de renewalparams, **no reactivar a ciegas una versión antigua**
ni sobrescribir certificados más nuevos con backups. Mantener el servicio TLS
existente y escalar al operador con un renovador compatible; no dejar una
ausencia de renovación indefinida. Volver temporalmente al estado duplicado
anterior puede ser una contención explícita, no una resolución aceptada.

No eliminar lineages, cuentas ACME, claves, cron o paquetes para revertir. No
restaurar un archivo privado mediante el chat. La restauración de configuración
o un reload Nginx se autorizan aparte y exigen validación previa.

### QA

Ante presión de recursos, OOM, disco cercano al límite, pérdida de eventos,
filtración en logs o fallo de aislamiento: detener admisión/procesamiento del
piloto y después las unidades QA que correspondan al plan. Conservar datos,
claves, cola y evidencias. No detener MySQL/Nginx de otros servicios ni revertir
la base con un dump antiguo sobre eventos recibidos. Comprobar puertos y cgroups
propios al cerrar. Revisar TTL antes de reanudar.

## 5. Paquete de evidencia para pedir despliegue

Ninguna autorización debe agrupar de forma implícita Certbot, aprovisionamiento,
exposición pública y Meta. Preparar y aprobar por separado:

1. **Certbot:** C1–C5, estados exactos antes/después, respaldo privado verificable,
   gestor seleccionado, plugins/hooks, certificados públicos y alertas.
2. **Host:** baseline fechado de 24 h, capacidad residual y cuotas probadas;
   versiones/soporte de los controles de seguridad.
3. **Artefacto:** bootstrap QA aislado, commit/hash/JAR, dependencias, pruebas y
   ausencia de módulos, credenciales o transportes productivos innecesarios.
4. **MySQL nuevo:** identidad/datadir/puerto propios, permisos DML, TLS, esquema
   aprobado, restauración y recuperación; sin tocar bases anteriores.
5. **Nginx local:** diff del vhost, certificado sintético de CA controlada,
   nginx -t y recorrido HTTPS usando Nginx real, rutas/métodos/bytes/HMAC,
   concurrencia, caída SQL y privacidad de todos los logs. Las pruebas anteriores
   con proxy JDK no sustituyen este gate.
6. **Operación:** controles apagados por defecto, observador de cola y recursos,
   alertas, responsables y ensayo de contención/reversión sin pérdida de datos.
7. **Cambio revisable:** archivos exactos y hashes, diferencias respecto a
   plantillas anteriores, comandos/objetivos propuestos, ventana y condiciones
   de aborto. No incluir secretos en los anexos.
8. **Autorización:** decisión explícita para cada paso. DNS/certificado QA,
   publicación HTTPS y conexión con Meta siguen sin autorización.

## Evidencias de esta etapa y conclusión

`qa/runtime/ubuntu-qa-riesgos-20261115-14efa22d/lectura-remota.json` conserva la
lectura filtrada actual; `baseline.json` registra los archivos locales previos.
Los informes del 14 de noviembre se mantienen sin reescribir como si fueran
evidencia del día 15.

No se ejecutaron pruebas de renovación, integración Nginx o carga: requerirían
infraestructura/cambios fuera del alcance. No se marca resuelto lo que aún no
cumple los gates. Esta etapa entrega el diagnóstico actualizado y un plan
reversible; **el despliegue continúa bloqueado por evidencia pendiente**.
