# Inspección Ubuntu QA y Certbot — 14 de noviembre de 2026

## Alcance

Inspección SSH de solo lectura autorizada por el usuario, después de desbloquear
su clave en el agente local. Se ejecutó como `carolina`. `sudo -n` requiere
contraseña y no se utilizó para elevar privilegios. No se solicitó ni recibió
ninguna contraseña, clave privada o token por el chat.

No se ejecutó Certbot, ACME, renovación o dry-run. No se alteraron servicios,
timers, Nginx, certificados, secretos, bases de datos o configuración remota.
La lectura no incluye claves privadas ni entornos de servicios. El material de
auditoría se ejecutó por stdin, sin instalar archivos en Ubuntu.

La evidencia se tomó aproximadamente a las 22:35 del 14 de noviembre en
America/Santiago (01:35 UTC del día 15 en el servidor).

## Certbot — VERIFICADO

**No existe un único renovador configurado: APT y Snap tienen renovación
automática habilitada y programada.** No se debe elegir basándose en el PATH.

| Instalación | Versión observada | Ruta/administrador |
|---|---|---|
| APT | Certbot 2.9.0-1; plugin nginx 2.9.0-1 | `/usr/bin/certbot`, `certbot.timer` |
| Snap | Certbot 5.10.0, revisión 5940 | `/snap/bin/certbot`, `snap.certbot.renew.timer` |
| Acceso en `/usr/local/bin` | Enlace al lanzador Snap | `/usr/local/bin/certbot` resuelve a `/usr/bin/snap` |

Ambos timers estaban `enabled`, `active`, en espera. Los servicios de renovación
estaban inactivos entre ejecuciones; no se observaron dos procesos renovando en
ese instante. Las últimas ejecuciones registradas terminaron con estado 0:

- APT: 14 de noviembre, 22:11 America/Santiago.
- Snap: 14 de noviembre, 17:26 America/Santiago.

Eso demuestra ejecución sin error reportado por systemd, **no una renovación
efectiva**: un certificado que no vence también puede producir salida 0.

Los comandos de los servicios invocan renovación y no especifican otro
`--config-dir` ni configuración alternativa. Ambas instalaciones contienen las
referencias del directorio predeterminado `/etc/letsencrypt`. Se identificaron
estas configuraciones de renovación legibles:

| Lineage | Versión registrada en el archivo | Autenticador/instalador |
|---|---|---|
| `avanza.casol.cl` | 2.9.0 | nginx / nginx |
| `buscadorerrores.casol.cl` | 5.8.0 | nginx / nginx |
| `staging.dimabug.casol.cl` | 5.8.0 | nginx / nginx |

La versión de una configuración acredita su historial, no propiedad exclusiva
por el paquete actualmente instalado. Las dos primeras conclusiones compatibles
con la evidencia son administración histórica mixta y dos programadores actuales.
No se atribuye la última emisión de cada certificado a un timer sin pruebas.

También existe `/etc/cron.d/certbot`, pero contiene la condición que omite la
tarea cuando existe `/run/systemd/system`; esa condición se cumple. No se presenta
por ello como un tercer renovador ejecutándose. No se inspeccionaron todas las
crontabs de usuarios o posibles wrappers privados.

**PENDIENTE:** decidir un gestor único para el nuevo certificado y revisar el
impacto sobre las tres lineages antes de deshabilitar o desinstalar nada. La
elección requiere autorización separada, compatibilidad y verificación controlada
de renovación. No se recomienda borrar APT o Snap basándose solo en estas rutas.

## Nginx y hostname QA

**VERIFICADO:** Nginx 1.24.0 (Ubuntu). Se leyeron únicamente directivas
seleccionadas de sites-enabled/conf.d, sin imprimir configuraciones completas:

- `avanza`: dominio avanza, certificado de su lineage, proxy local 3000.
- `buscadorerrores`: certificado de su lineage y socket Unix propio.
- `dimabug-staging`: dominio staging, certificado de su lineage, backend 8081.

No se encontró `wa-qa.dimabug.casol.cl` en esos sites/directorios de renovación.
Esto **no** es una expansión exhaustiva de todos los includes de Nginx ni una
comprobación DNS, SAN o cadena de certificados. No se utilizó `nginx -T`/`-t`,
porque la inspección no debía abrir logs o cargar claves mediante comandos de
validación privilegiados. No se hizo ninguna solicitud HTTP a los sitios.

Puertos observados: 80 y 443 en escucha general y 8081 en loopback. No se
observaron 18132, 13328 o 18443 en la consulta TCP realizada. Es una instantánea;
debe repetirse antes de asignarlos. Los listeners públicos existentes no fueron
creados, cerrados ni modificados por esta auditoría.

## Plataforma y capacidad

**VERIFICADO:**

- Ubuntu 24.04.4 LTS; systemd 255.
- Java OpenJDK 21.0.13.0.3.
- Binario MySQL 8.0.46 instalado; no se abrió una conexión SQL ni se inspeccionaron
  datos de bases existentes.
- 2 CPU lógicas y 4.009.852 KiB de RAM total (aproximadamente 3,82 GiB).
- MemAvailable: 2.303.312 KiB (aproximadamente 2,20 GiB), variable según carga.
- Espacio libre observado bajo `/var/lib`: aproximadamente 11,1 GiB.
- No existen las rutas propuestas `/opt/dimabug-wa-qa`, `/etc/dimabug-wa-qa` o
  `/var/lib/dimabug-wa-qa-mysql` en la inspección realizada.

**PENDIENTE de dimensionamiento:** los máximos propuestos de 1 GiB Spring + 1 GiB
MySQL consumirían casi todo el margen disponible observado, sin reserva suficiente
para picos de los servicios existentes, sistema y temporales. No autorizar ese
presupuesto tal cual solo porque encaje nominalmente. Revisar carga, disco,
retención, cgroups/egress y presupuesto reducido de recepción, o utilizar una VM
QA separada. Tener systemd 255 no prueba por sí solo el cumplimiento de todas
las restricciones de recursos/red de las plantillas.

Se mantiene el requisito de artefacto Spring QA aislado: el JAR general no puede
suponerse compatible con una base de solo tres tablas.

## Evidencias locales

`qa/runtime/ubuntu-wa-qa-auditoria/`:

- `certbot-sin-privilegios.json`: instalaciones, paquetes, timers y plugins.
- `contraste.json`: fechas, rutas TLS declaradas, plataforma y recursos.
- `contraste-administradores.json`: comandos clasificados, cron y rutas QA.
- `referencias-config-dir.json`: referencias en los paquetes instalados.

Los mensajes completos del journal no se imprimieron. Las consultas de journal
de las unidades no devolvieron entradas legibles; no se deduce de ello ausencia
de historial. No se certificó la fecha de expiración de los certificados ni la
última renovación efectiva. Esas comprobaciones pueden requerir una lectura
privilegiada acotada, con autenticación local segura y otra fase expresamente
definida; no hace falta compartir contraseñas en el chat.

## Próximas decisiones, sin ejecución

1. Seleccionar y validar el gestor de renovación; resolver la duplicidad sin
   afectar a los certificados actuales.
2. Aprobar capacidad y separación QA, artefacto mínimo y nueva instancia MySQL.
3. Confirmar DNS y política del certificado de `wa-qa.dimabug.casol.cl`.
4. Solo después, autorizar aprovisionamiento, pruebas Nginx loopback y, en otra
   etapa, exposición HTTPS y Meta.

Esta inspección no autoriza ninguno de esos cambios.
