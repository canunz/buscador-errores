# Resultado: compatibilidad remota del diagnóstico WhatsApp QA

## Decisión

**NO-GO para ejecutar ahora el diagnóstico definitivo con el script instalado.**
La infraestructura examinada no muestra una incompatibilidad estática con el mecanismo.
El impedimento comprobado es que **el servidor conserva la versión anterior del script**,
sin la corrección local validada de UID y lecturas estables durante exec.

No se transfirió ni modificó ningún archivo remoto. No se inició QA, no se desbloqueó
MySQL, no se instalaron paquetes y no se ejecutó jcmd ni se enviaron señales.
No se intentó reconstruir la identidad histórica del proceso desaparecido.

## Evidencia de las lecturas autorizadas

| Comprobación | Resultado |
|---|---|
| QA y pasarela antes/después | `inactive`, MainPID=0, NRestarts=0 en ambos extremos |
| Staging, MySQL y Nginx antes/después | Activos, mismos MainPID, NRestarts=0 |
| Unidad QA | `Type=simple`, User=`dimabug-wa-pilot` |
| ExecStart filtrado | Java `/usr/bin/java` y `-jar /opt/dimabug-wa-pilot/whatsapp-qa.jar` coinciden |
| Cgroup del host | Controladores cgroup v2 presentes |
| ControlGroup del servicio | Vacío con QA detenido; correcto no intentar validarlo contra un PID inexistente |
| Restricciones | PrivateTmp=yes; ProtectHome=yes; sin drop-ins registrados |
| Memoria | MemoryHigh=384 MiB; MemoryMax=512 MiB; swap=0 |
| CPU/tareas | CPUQuotaPerSecUSec=500ms; TasksMax=96 |
| Tiempos | TimeoutStart=180 s; TimeoutStop=45 s; sin cambios |
| jcmd aislado | Archivo existente, legible y ejecutable por el usuario SSH |
| Script remoto | Legible; no tiene bit ejecutable, que no es necesario al invocarlo mediante Python |
| Salida del servicio | journal; límite 100 mensajes/30 s: sigue desaconsejando SIGQUIT para dos volcados completos |
| Estado guardado del intento v2 | FAIL, QA_UID_IDENTITY_MISMATCH, cero capturas; limpieza final guardada como correcta |

El hash del script remoto es
`6271a877742a049a83b2ebff533ffcd25028a317b97379cf61077dc520e8536f`.
El de la versión local validada es
`cb943aa1db479427cb2af529aae6de7d4beb550c234a6c9a61f0683b1cffda28`.
La diferencia demuestra que la corrección todavía no está desplegada. No demuestra
la causa exacta del rechazo histórico, que continúa indeterminada.

## Permisos y límites de esta inspección

`sudo -n true` respondió que se requiere contraseña. Por eso no se ejecutaron la sonda
privilegiada completa, la lectura privilegiada del journal ni el SELECT de mysql.user.
Se completaron únicamente sus lecturas públicas/permitidas: propiedades systemd
filtradas, SHA de archivos, resultado seguro existente y presencia de cgroup v2.

**El bloqueo actual de la cuenta no se comprobó**: el JSON acredita el estado al
finalizar el intento anterior, no una consulta SQL actual.

Con QA detenido no se pueden verificar los UID efectivos, el cgroup exacto del proceso,
la expansión real de las rutas de credenciales ni el attach sobre esa JVM. ProtectHome
y PrivateTmp aplican al servicio QA; no implican por sí solos que el diagnóstico root,
ejecutado fuera de ese servicio, no pueda leer el JDK aislado. La compatibilidad efectiva
de attach sigue condicionada a una ejecución posterior autorizada, no se declara probada.

No se repitieron versiones JVM, diagnósticos de capacidad, conectividad, TLS ni pruebas
funcionales ya realizadas. No existe un nuevo impedimento demostrado de JVM, paquetes,
puertos o memoria que justifique cambiarlos durante esta revisión.

## Condiciones mínimas para reconsiderar GO

1. Autorizar por separado la transferencia de la versión local validada y verificar
   que su SHA remoto coincida; esta entrega no ejecuta esa transferencia.
2. Confirmar manualmente mediante el SELECT de solo lectura documentado que la cuenta
   QA continúa bloqueada. No alterar la cuenta durante esa comprobación.
3. Mantener QA detenido hasta autorizar expresamente el diagnóstico activo con capturas
   y reversión. No ampliar permisos, timeouts o límites como condición previa sin evidencia.

Único comando manual pendiente necesario para comprobar el bloqueo actual:

```bash
sudo mysql --protocol=socket --batch --skip-column-names --execute="SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1';"
```

Esperado: una fila `Y`. Si devuelve otra cosa o falla, informar sin corregirlo automáticamente.

El NO-GO corresponde al estado actual del **diagnóstico**, no a una nueva auditoría de
la infraestructura ni a una decisión de despliegue de WhatsApp. Las lecturas anteriores
no autorizan transferir, arrancar, desbloquear o capturar.
