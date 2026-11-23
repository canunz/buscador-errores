# WhatsApp QA: identidad del proceso y validación remota pendiente

## Estado

Validación local aprobada. Procedimiento remoto preparado para revisión; **no ejecutado**.
Esta entrega no conecta a DigitalOcean, no transfiere scripts, no inicia QA, no desbloquea
cuentas ni modifica configuración remota. No hay commits.

El fallo histórico `QA_UID_IDENTITY_MISMATCH` está confirmado por el resultado del
diagnóstico v2, pero ese resultado no conserva UID, propietario de `/proc`, ejecutable
ni argumentos observados en el instante del rechazo. **Su causa exacta sigue indeterminada.**
No se debe atribuir a una transición de systemd o a permisos sin una captura correlacionada.
Tampoco identifica la causa del arranque incompleto de Spring Boot.

## Evidencias conservadas

Carpeta: [evidencias/whatsapp_identidad_local](evidencias/whatsapp_identidad_local/).

| Archivo | Evidencia |
|---|---|
| `unittest-31.txt` | Nombres y resultados de las 31 pruebas: 31 correctas |
| `integration-result.json` | Dos volcados reales; separación 15,01 s; tres JVM ajenas rechazadas; limpieza ante fallo |
| `captured-status.json` | Estados y métodos de los hilos de las dos capturas exitosas |
| `threads-1.txt`, `threads-2.txt` | Volcados completos de la JVM sintética; no provienen de DimaBug ni de usuarios |
| `failure-cleanup-status.json` | Fallo de attach inyectado; proceso detenido y cuenta simulada bloqueada |
| `environment.json` | Kernel, Python, versión Java y hashes del código ejecutado en Docker |
| `record.json` | Registro de conservación, alcance, límites del contenedor y resultado |
| `readonly-probe-checks.txt` | Cuatro comprobaciones adicionales de la sonda propuesta, sin SSH |
| `sources/` | Copia de las fuentes y plantillas usadas para reproducir el ensayo |
| `manifest.sha256.json` | Integridad SHA-256 de cada archivo conservado, excepto el propio manifiesto |

Se repitió exclusivamente el ensayo local para conservar archivos que antes se eliminaban
con el contenedor. La repetición volvió a resultar PASS y el contenedor se eliminó tras
copiar sus evidencias. No se preservan tokens, payloads reales, variables de entorno ni
credenciales. No reemplazar estas evidencias con volcados reales sin una política privada.

## Resultado de las 31 pruebas

Las pruebas con subcasos siguen contando como un único método unittest. Total:
**10 de preparación + 21 de identidad = 31**. Las comprobaciones adicionales del ensayo
con JVM real se describen aparte; no se suman artificialmente a las 31.

| Método (prefijo `test_`) | Verificación | Resultado |
|---|---|---|
| `limites_y_credenciales` | Límites y referencias a credenciales en plantillas | OK |
| `solo_schema_y_tls_sin_degradacion` | Marcadores de esquema, TLS y flags de la plantilla | OK |
| `provision_requiere_autorizacion_sin_ejecutar` | Provisionamiento permanece en plan sin autorización | OK |
| `piloto_plan_no_inicia_servicios` | Modo plan del piloto | OK |
| `umbral_y_muestra_local_sin_datos_reales` | Umbral de memoria y evento sintético | OK |
| `reversion_no_reinicia_mysql_ni_borra_esquemas` | Alcance del rollback preparado | OK |
| `uri_truststore_igual_en_comprobador_y_jdbc` | Consistencia de URI de truststore | OK |
| `supervisor_bloquea_cuenta_en_salida_y_corte` | Declaración del bloqueo y corte en supervisor | OK |
| `ajuste_arranque_cambia_solo_timeout` | Transformación histórica acotada de configuración | OK |
| `arranque_unico_sin_eventos_ni_procesamiento` | Override histórico de arranque sin funciones | OK |
| `exec_during_proc_read_is_never_accepted` | Rechaza lecturas que cruzan exec; espera identidad estable | OK |
| `unstable_foreign_unit_is_not_retried` | No reintenta una unidad ajena aunque esté en transición | OK |
| `status_uids_uses_all_four_credentials` | Lee UID real, efectivo, guardado y de filesystem | OK |
| `rejects_malformed_uid_status` | Rechaza UID ausentes, duplicados o mal formados | OK |
| `rejects_any_real_effective_saved_or_filesystem_uid_mismatch` | Rechaza discrepancia en cualquiera de los cuatro UID | OK |
| `preexec_root_to_service_transition_never_accepts_root` | Espera transición sin aceptar root como Java QA | OK |
| `preexec_cannot_switch_pid_or_invocation` | No cambia PID, inicio ni invocación durante la espera | OK |
| `accepts_exact_java_qa` | Acepta únicamente la identidad QA esperada | OK |
| `accepts_same_process_again` | Revalida el mismo proceso | OK |
| `rejects_foreign_and_changed_processes` | Rechaza unidad, UID, ejecutable o cgroup ajenos | OK |
| `rejects_pid_reuse_restart_and_invocation_change` | Detecta reutilización y reinicios | OK |
| `rejects_jar_only_mentioned_as_property` | No confunde una propiedad con el JAR ejecutado | OK |
| `rejects_wrong_jar_even_if_expected_path_is_another_argument` | No acepta coincidencia incidental de ruta | OK |
| `rejects_duplicate_jar_switch` | Exige un único `-jar` | OK |
| `rejects_wrong_config_missing_config_and_extra_overrides` | Exige argumentos de configuración exactos | OK |
| `rejects_empty_cmdline` | No acepta línea vacía | OK |
| `rejects_cgroup_suffix_or_child_trick` | Exige cgroup exacto | OK |
| `preexec_is_not_accepted_but_java_after_exec_is` | Hijo systemd transitorio nunca aceptado como JVM | OK |
| `preexec_wait_is_bounded` | Espera inicial limitada a tres segundos | OK |
| `does_not_retry_foreign_java_or_wrong_jar` | Rechazo inmediato de JVM o JAR ajenos estables | OK |
| `preexec_in_foreign_cgroup_is_rejected_without_retry` | Rechazo inmediato de cgroup ajeno | OK |

Las diez pruebas de preparación son verificaciones locales de fuentes/plantillas y modos
plan. No demuestran que TLS, cuentas, límites o temporizadores funcionen en DigitalOcean.
El test histórico del timeout no vuelve a cambiar el timeout de ningún servicio.

### Comprobaciones adicionales de la sonda propuesta

Cuatro comprobaciones correctas, separadas del total histórico de 31: rechazo sin root
antes de ejecutar comandos; consulta limitada a `systemctl show` con propiedades explícitas;
QA detenido devuelve NOT_OBSERVABLE sin consultar `/proc` ni inventar una causa; salida
de estado filtrada por lista permitida y sin un marcador de secreto de prueba.
Estas pruebas usan dobles y no ejecutan la sonda en el servidor. Los dos bloques Python
embebidos en el procedimiento también se validaron sintácticamente; el primero coincide
exactamente con la fuente local de la sonda.

## Ensayo integral con Java real

- OpenJDK Temurin **21.0.13.1+2**, Python **3.10.12**, Linux WSL2 **6.6.87.2**.
- Contenedor sin red ni puertos, sin montajes del host; 512 MiB, sin swap adicional,
  0,5 CPU y 96 tareas. Capacidades SYS_ADMIN/SYS_PTRACE solo dentro del contenedor
  para crear un namespace de montaje y observar la JVM desechable.
- JVM ejecutada como `nobody`, con `/tmp` privado y un programa `Hold.main` que espera.
  JNI ajusta dumpability únicamente en ese programa de prueba, nunca en DimaBug.
- `thread_check.main()` ejecutado con `/proc` y `jcmd Thread.print` reales.
- Dos capturas reales, separadas por **15,01 segundos**; en ambas se identifica `main`
  en WAITING dentro de `Hold.main`. No es un bloqueo de Spring Boot.
- Tres JVM reales negativas: UID incorrecto, JAR incorrecto y configuración extra.
  Todas rechazadas sin capturarlas.
- Fallo de attach inyectado: finalmente se detiene la JVM y se bloquea la cuenta simulada.
- Las órdenes systemd y SQL son dobles de prueba. No se usa MySQL real ni sus servicios.
- El primer volcado se adelanta a 2 s solo en el fixture; en QA continúa a 95 s.
  Se conserva la separación de 15 s y el límite global del script.

Reproducir desde PowerShell local, con Docker Desktop disponible:

```powershell
& 'C:\Proyectos\DimaBug-MVP\qa\whatsapp\ubuntu\shared-mysql-pilot\run_local_thread_test.ps1'
```

El comando construye una imagen local (puede descargar dependencias), ejecuta únicamente
el contenedor desechable y lo elimina. No contiene SSH. Las evidencias de esta entrega
permanecen en la carpeta anterior aunque se vuelva a ejecutar ese comando.

## Diferencias y límites frente al servidor real

| Aspecto | Local verificado | Remoto: antecedente, no revalidado en esta entrega |
|---|---|---|
| SO/JVM | Docker, kernel WSL2, Temurin 21.0.13.1 | Ubuntu, paquete OpenJDK Ubuntu 21.0.13.1 |
| Identidad systemd | Propiedades simuladas | `Type=simple`, User QA, MainPID e InvocationID reales |
| Cgroup | Linux real del contenedor; ruta esperada suministrada por el doble | Jerarquía systemd del servicio real, pendiente contraste |
| Cuenta | `nobody`, cuatro UID reales | Usuario `dimabug-wa-pilot`; UID actuales del proceso no conservados |
| Aplicación | Programa de espera sin Spring/JPA | JAR DimaBug con Spring, JPA, MySQL y beans adicionales |
| Credenciales/configuración | Rutas y valores sintéticos | LoadCredential y rutas `%d` materializadas por systemd |
| Aislamiento | `/tmp` privado real | PrivateTmp y otras protecciones systemd; equivalencia completa pendiente |
| Recursos | Contenedor 512 MiB / 0,5 CPU; heap fixture 96 MiB | Unidad QA 512 MiB / 0,5 CPU; heap 192 MiB, otros topes JVM |
| Attach | Root con capacidades del contenedor | Sudo, permisos efectivos, Yama/namespaces reales pendientes |
| Temporización | Primera captura a 2 s, segunda 15 s después | Primera a 95 s; presupuesto de 180 s |
| MySQL/watchdog | SQL y systemd-run simulados; alarm de Python real | Cuenta, SQL, timer y recuperación reales no probados localmente |
| Código remoto | Fuente local actual identificada por SHA | Última transferencia conocida anterior a las correcciones UID/lectura estable |

La propiedad de `/proc/PID` no sustituye los UID de `status`. En el kernel local no se
reprodujo la supuesta diferencia de propietario tras cambiar dumpability. Las carreras
de arranque son reproducibles en pruebas de transición, pero no prueban lo sucedido en
el PID remoto ya desaparecido. El diagnóstico remoto debe mantener esa distinción.

## Criterios para revisar la fase remota

El procedimiento exacto está en
[WhatsApp_identidad_procedimiento_remoto_solo_lectura.md](WhatsApp_identidad_procedimiento_remoto_solo_lectura.md).
No autoriza ejecutar `thread_check.py --execute`, señales, attach, arranques o SQL DDL.
Con QA detenido, el resultado correcto será **NO OBSERVABLE históricamente**, salvo que
aparezca una evidencia conservada que muestre los valores efectivos del rechazo.

La validación local está aprobada; la compatibilidad completa con systemd remoto y la
causa del arranque de Spring Boot siguen pendientes. No se declara GO de despliegue.
