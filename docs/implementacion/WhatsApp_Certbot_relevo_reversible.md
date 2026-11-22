# Certbot — Procedimiento reversible preparado, NO ejecutado

Fecha: 17 de noviembre de 2026. Preparación solicitada después de C.5.
**Este documento no autoriza ejecutar los comandos de cambio que contiene.**
No se conectó al servidor, ejecutó Certbot, creó backup remoto, cambió timer,
instaló paquete o modificó certificado/Nginx. No hay script de relevo automático.

## Decisión candidata y alcance

APT 2.9.0 y Snap 5.10.0 tienen timers active/enabled según salida del operador.
Snap es el candidato a conservar, sujeto a compatibilidad de las tres lineages:
avanza.casol.cl, buscadorerrores.casol.cl y staging.dimabug.casol.cl.
La revisión sin renovación de los tres certificados no acredita capacidad de
renovarlos. No hay asignación exclusiva por dominio demostrada.

Primer cambio propuesto: dejar un único programador compatible **sin desinstalar
APT**, borrar lineages/cron/enlaces ni emitir el certificado QA. Nginx y las
aplicaciones deben seguir funcionando. Cada fase exige autorización concreta.

## 1. Precondiciones y preparación privada

Antes de autorizar la ventana, registrar en anexo privado con acceso root:

- Operador, segundo responsable de contingencia, canal de contacto, ventana
  máxima propuesta de 30 minutos y hora de aborto. No depender de una sola SSH.
- Estado enabled/active de timers, servicios de renovación, próximos disparos,
  procesos/locks, versiones, revisión Snap, plugins, directorios efectivos,
  overrides, cli.ini, crons/wrappers y hooks (sin volcar comandos/secretos al chat).
- SAN, cadena, vigencia y fingerprint del certificado servido por cada sitio,
  y estado/código/latencias de las tres aplicaciones antes del cambio.
- Configuración y hashes de renewalparams, enlaces live/archive y vhosts;
  no continuar si hay cambios concurrentes o renovación en curso.
- Copia consistente y **privada** de /etc/letsencrypt, configuración Nginx y
  overrides/configuración de programadores relevantes, con permisos preservados;
  versiones/estados en manifiesto. Material privado nunca al repositorio o chat.
- Verificación de copia restaurable en directorio privado aislado. No copiar
  credenciales a este workspace ni restaurar sobre certificados activos para probar.

No comenzar sin vigencia suficiente, respaldo verificado, responsables presentes
y un retorno viable. Un certificado próximo a caducar requiere plan de incidente
separado. No asumir que instalar paquetes o cambiar rutas forma parte del relevo.

Lecturas acotadas posibles para la ventana, **no ejecutadas aquí**:

```bash
systemctl show -p Id -p ActiveState -p UnitFileState -- certbot.timer snap.certbot.renew.timer
systemctl show -p Id -p ActiveState -- certbot.service snap.certbot.renew.service
systemctl list-timers --all --no-pager certbot.timer snap.certbot.renew.timer
```

Si difiere del estado conocido active/enabled para ambos timers, rehacer el plan
de retorno exacto; no aplicar ciegamente los comandos de la sección 3.

## 2. Compatibilidad del candidato, autorización separada

Primero validar Snap/plugins/configuración en entorno aislado o copia privada
controlada. Esto puede requerir custodia de material privado: se aprueba aparte.
No se considera suficiente version, plugins presentes o nginx -t.

En una ventana real **expresamente aprobada** para cambios temporales y tráfico
ACME: comprobar que no hay renovaciones activas, que Snap sigue programado y que
no disparará otra renovación durante el ensayo. No detener un servicio de
renovación activo ni borrar locks. Esperar o posponer si el tiempo no alcanza.

Solo si se autoriza esa pausa temporal, detener el timer APT conocido; mantenerlo
habilitado y conservar Snap. El segundo responsable vigila ventana y sitios:

```bash
# CAMBIO PROPUESTO; NO EJECUTAR EN LA ETAPA DE PREPARACION
sudo systemctl stop certbot.timer
```

Una pausa no impide una llamada manual/cron ajena: el inventario y la observación
siguen siendo necesarios. La pausa no debe ser indefinida. Si se pierde la sesión,
el responsable de contingencia ejecuta la decisión de recuperación compatible;
no se programa un timer de retorno ni un comando background en esta preparación.

Pruebas candidatas, **solo bajo esa autorización**, una por lineage:

```bash
# NO ES LECTURA: puede contactar ACME y cambiar temporalmente Nginx.
sudo /snap/bin/certbot renew --cert-name avanza.casol.cl --dry-run
sudo /snap/bin/certbot renew --cert-name buscadorerrores.casol.cl --dry-run
sudo /snap/bin/certbot renew --cert-name staging.dimabug.casol.cl --dry-run
```

No añadir flags de hooks ni forzar renovación/expedición para lograr un verde.
No ejecutar certbot --nginx genérico o comandos desde PATH ambiguo. El operador
guarda resultados saneados por lineage; no logs completos con material sensible.

Aceptar solo si las tres pruebas completan correctamente, configuraciones y
certificados quedan como se espera, no hay cambios inesperados en otros vhosts,
las aplicaciones mantienen disponibilidad/latencias y el retorno sigue viable.
El dry-run no prueba una renovación real futura: exigir vigilancia de la próxima
ejecución que corresponda y alertas de fallo/caducidad (candidato aviso 21 días,
crítico 7). No provocar una renovación real fuera de otra autorización.

## 3. Confirmación del relevo y criterios de aceptación

Después de las tres validaciones y **con autorización de consolidar el relevo**:

```bash
# CAMBIO PROPUESTO; NO EJECUTAR AHORA
sudo systemctl disable certbot.timer
```

No se usa mask, no se elimina el servicio ni el paquete APT. Snap debe conservar
su estado operativo aprobado. Verificar a continuación:

- certbot.timer inactive/disabled; Snap timer active/enabled y próximo disparo.
- Ausencia de otro programador/wrapper efectivo. El cron APT conocido solo se
  omite mientras su condición systemd siga cumpliéndose; no borrarlo sin otro plan.
- Ningún proceso de renovación interrumpido y ningún error nuevo de las tres apps.
- Certificados servidos correctos, config-dirs sin duplicación inventada y backups
  custodiados. Registrar diff de cambios y hash del paquete de evidencia.
- Alerta/operador capaz de detectar pérdida de renovación; no depender de un exit 0.

Solo entonces marcar administración única aceptada. Despliegue QA, DNS/certificado
nuevo y conexión Meta quedan fuera; no se conceden por cerrar este gate.

## 4. Retorno condicionado y contención

**Si los archivos y formatos no cambiaron y APT sigue siendo compatible**, recuperar
el estado inicial registrado (para el baseline conocido active/enabled):

```bash
# RETORNO PROPUESTO; requiere comprobar compatibilidad y autorizarlo
sudo systemctl enable --now certbot.timer
```

Snap permanece como estaba, salvo que el plan aprobado indique otra cosa. Volver
temporalmente a dos timers es **retorno al baseline**, no resolución del problema;
registrarlo y mantener el gate abierto. No disparar APT renew como prueba de retorno.

**Si Snap cambió renewalparams, certificados o hay incompatibilidad**, no reactivar
APT automáticamente. Mantener un renovador compatible operativo, preservar los
certificados nuevos y escalar al responsable. Restaurar la programación/configuración
anterior solo tras validar compatibilidad y sintaxis; no sobrescribir certificados
más nuevos con copias antiguas. El respaldo es capacidad de recuperación, no una
orden de restauración completa.

Si aparece otra renovación en curso, fallo de un sitio, divergencia de hash,
hook inesperado, caducidad cercana o pérdida de supervisión: abortar la transición,
preservar evidencia y aplicar la rama compatible. No reiniciar Nginx/MySQL/staging,
interrumpir una renovación activa ni dejar ambos renovadores deshabilitados.

Un reinicio del host obliga a revisar estados: la pausa temporal APT con enable
conservado puede volver a duplicar programación. No usar el reinicio como rollback.
No se puede prometer una restauración automática segura de binario antiguo tras
cualquier cambio; por eso el operador de contingencia y las comprobaciones son gates.

## Registro mínimo para autorizar la ejecución posteriormente

Ventana/responsables; inventario y certificados antes/después; validación de las
tres lineages; backup verificado bajo custodia; diferencias exactas; estado de
timers/siguiente disparo; salud de las tres apps; rama de retorno; alertas.
**Estado actual: preparado para revisión, sin ejecución ni autorización de cambio.**
