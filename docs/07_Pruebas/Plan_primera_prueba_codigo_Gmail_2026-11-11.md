# Primera prueba funcional controlada de códigos por Gmail API

Estado: plan preparado, no ejecutado. Sin cambios de configuración, códigos generados ni correos enviados en esta preparación.

## Hechos comprobados

- JAR remoto SHA-256 21df61ccb07090d36417fe3032583a38fe21a3c5721f2dac797387b2d82bd91f. Se cotejaron por SHA-256 AuthController, AutenticarUsuarioService, CodigoAccesoEntity y CodigoAccesoRepositoryAdapter con la copia local analizada mediante javap; todas coinciden. Los adaptadores de envío/Gmail y cifrado ya habían sido cotejados.
- El JAR usa el flujo antiguo: EnvioCodigoAccesoCorreoAdapter.despachar -> gmailApiLista -> GmailOAuthService.disponible -> enviarPorGmailApi -> GmailOAuthService.enviar. El ejecutor es en memoria; la aceptación de la tarea por el ejecutor y HTTP 200 no acreditan envío ni recepción.
- No tiene DespacharCodigosService ni la máquina persistente de estados de correo del código local nuevo. La tabla u_correo_codigo y los ocho registros NO_CONFIGURADO históricos no representan el envío de este JAR ni tienen despachador automático en él. No reencolar, reiniciar el backend local nuevo ni activar otro consumidor durante la ventana.
- GMAIL_ENVIO_REAL_HABILITADO es global. No hay interruptor exclusivo para códigos, lista blanca de destinatario ni límite nativo de un mensaje. dimabug.auth.codigo.usar-gmail-api=true, ya empaquetado, exige Gmail pero no habilita ni restringe por sí solo una prueba.
- Estado previo: staging activo con Gmail false, Procedimientos DESHABILITADO, MailSenderAutoConfiguration excluida y staging7 detenido.
- Destinatario único aprobado por usuario: dimabug507@gmail.com. SELECT específico de lectura confirmó exactamente una cuenta registrada, usuario activo y rol activo. No se creó ni modificó usuario. Revalidar antes de la ejecución.

## Aislamiento y cambios futuros que requieren autorización

Para limitar operacionalmente la prueba sin modificar código es necesario impedir tráfico concurrente, además de los flags de transporte. Propuesta: mantenimiento temporal únicamente de las rutas API del virtual host staging en Nginx, verificando su configuración y sin afectar otros sitios. Mientras dure, todas las peticiones públicas de API staging se rechazan; la prueba se realiza por el loopback del servidor o túnel SSH privado. No parar Nginx globalmente. Revisar también otras rutas que puedan llegar al mismo backend y abortar si no se puede cerrar el acceso concurrente. No ampliar el puerto 8081 a interfaces públicas.

Crear respaldos separados de staging.env y de la configuración Nginx del sitio afectado. El respaldo de staging.env debe ser el actual con la custodia y el aislamiento instalados, root:root 0600. No restaurar un respaldo anterior a la custodia.

Único flag de envío a cambiar durante la ventana: GMAIL_ENVIO_REAL_HABILITADO=true. Conservar GMAIL_OAUTH_HABILITADO=true, DIMABUG_AUTOMATIZACION_MODO=DESHABILITADO, exclusión SMTP y cualquier exclusión previa. Conservar dimabug.auth.codigo.usar-gmail-api=true en el JAR. No modificar código ni desplegar un JAR distinto. Se requiere detener y volver a iniciar staging para cargar el flag; staging7 permanece detenido. El permiso futuro debe incluir este ciclo y el cierre posterior, no asumir que editar el archivo cambia el proceso existente.

Comprobar el entorno real después del arranque, cuenta OAuth activa y versión actual (no fijar ciegamente versión 5: podría renovarse), remitente/destinatario y ausencia de otro proceso consumidor conocido. Con el modo DESHABILITADO el trabajador periódico permanece registrado pero devuelve lista vacía sin procesar vencidas. Con la autoconfiguración SMTP excluida los adaptadores antiguos no obtienen JavaMailSender. Los endpoints de prueba administrativa Gmail también usarían el flag global: no invocarlos y mantenerlos inaccesibles públicamente.

## Única solicitud

Objetivo: recuperar el envío que fallaba en recuperación de contraseña, sin cambiar ninguna contraseña. Ejecutar una sola petición privada POST /api/auth/recuperar/email, con campo JSON email igual al destinatario aprobado (el método Java identificador() no es un campo JSON). No pulsar reenviar ni ejecutar una prueba administrativa paralela. No probar POST /api/auth/codigo además de recuperación, porque generaría otro mensaje. Si el código expirara antes de validarlo, cerrar la prueba como incompleta y pedir autorización para un nuevo envío.

La solicitud genera un código y puede invalidar códigos previos pendientes de ese usuario; ese efecto debe estar incluido en la autorización de prueba. Registrar como baseline solo metadatos de u_codigo_acceso del destinatario y conteos de u_correo_codigo; jamás hashes, códigos, MIME, passwords ni tokens.

## Observabilidad y criterios

1. Antes: metadatos del último código y conteos de la cola; usuario/rol activos; SMTP excluido; Procedimientos deshabilitado; acceso público bloqueado.
2. Solicitud: HTTP 200 indica solicitud aceptada, no entrega. Registrar hora y una sola solicitud.
3. Persistencia: SELECT del destinatario obtiene nuevo codigo_id, creado_en, expira_en, intentos y usado. No consultar codigo_hash. Confirmar un nuevo código para esa solicitud y comparar los conteos de la cola histórica, que deben permanecer sin cambios.
4. Transporte: log del adaptador de código de recuperación enviado vía Gmail API solo tras retorno satisfactorio de GmailOAuthService.enviar; si hay error, clasificar fallo. Este JAR descarta el ID devuelto por Gmail en ese adaptador y no guarda una fila de estado de correo ENVIADO. No prometer trazabilidad por messageId ni transiciones PENDIENTE -> ENVIANDO -> ENVIADO.
5. Recepción: propietario del buzón confirma manualmente Recibidos/Spam y el mensaje nuevo de la ventana, remitente oficial y destinatario único. Puede revisar Enviados al ser el mismo buzón. No compartir código, asunto completo que pueda contener el código, capturas o cabeceras sensibles. gmail.send no permite comprobar programáticamente el buzón.
6. Validación: el usuario introduce el código de forma oculta por el canal privado y se realiza únicamente POST /api/auth/recuperar/codigo. No pegar el código en el chat ni argumentos/historial; no imprimir el cuerpo de la petición. Debe devolver validación satisfactoria. No ejecutar /recuperar/password, no cambiar contraseña. La validación de recuperación no consume el código; se dejará expirar naturalmente, sin SQL manual de limpieza. Si se desea completar login o cambio de contraseña sería una prueba y autorización diferentes.

Los estados de prueba son observacionales: NO_SOLICITADO -> CODIGO_CREADO -> GMAIL_ACEPTO -> RECEPCION_CONFIRMADA -> CODIGO_VALIDADO, con alternativas FALLIDO o INCIERTO. No son estados persistidos de correo en este JAR. No afirmar recepción solo por HTTP, log de código generado o cuenta OAuth ACTIVA.

## Cierre y recuperación ante fallo

Ante error, timeout o recepción no confirmada: no reintentar. Registrar incertidumbre sin reenviar. Detener staging, restaurar el flag Gmail false conservando los aislamientos y la custodia, verificar archivo y reiniciar únicamente staging en estado bloqueado. Revisar proceso real/logs. La detención no puede retirar un mensaje ya aceptado y el cierre ordenado podría terminar una tarea que ya estaba en curso. No afirmar cancelación de la solicitud por cambiar el archivo.

Al terminar satisfactoriamente se aplica el mismo cierre Gmail false y se reinicia staging con Procedimientos y SMTP aún bloqueados. Solo después de comprobar ese bloqueo se restaura la configuración temporal Nginx del sitio, conservando el estado previo de aislamiento. Si algo falla, mantener cerrada la ventana y staging detenido o bloqueado, sin restaurar Gmail true ni habilitar SMTP como alternativa automática. No deshacer la cuenta OAuth ni regenerar la clave.

## Alcance de la futura aprobación

Se requiere aprobación para mantenimiento temporal de API staging, respaldos, activación temporal del flag global Gmail, ciclos de stop/start únicamente de staging, una solicitud al destinatario aprobado (incluye persistencia/invalidez de códigos previos del destinatario), validación privada del código, posible renovación OAuth normal durante el envío, lecturas de metadatos/logs y cierre completo con Gmail false. No permite enviar otros mensajes, habilitar Procedimientos/SMTP, arrancar staging7 ni cambiar contraseñas.

## Compatibilidad y límites

La prueba de generación, envío, recepción y validación de recuperación es posible sin modificar código desplegado, con el aislamiento operativo descrito y cambios temporales de configuración autorizados. El JAR no puede garantizar por sí solo exclusividad de un destinatario/tipo ni aportar una máquina persistente de estados de correo. Si se exige un interruptor nativo exclusivo de códigos, lista blanca obligatoria o estados persistentes, se requiere cambio de código y despliegue independiente; no forma parte de esta prueba.


## Primera ejecución autorizada: resultado y corrección

La única solicitud efectiva devolvió HTTP 400. El controlador de prueba preparó incorrectamente {identificador: destinatario}; SolicitarCodigoRequest es un record con componente email y alias correo. VerificarCodigoRequest también espera email y codigo (alias correo/code). Este error de formato ocurrió antes de la generación del código. No atribuirlo a un fallo Gmail ni a tokens/cifrado. Se corrigió únicamente el controlador de prueba, sin cambiar código desplegado ni hacer otra solicitud. Prueba sintética del payload y del bloqueo de repetición satisfactoria. No se eliminó el marcador persistente de solicitud única; otra ejecución real requiere nueva autorización y preparación deliberada del siguiente identificador de prueba.

Cierre confirmado: proceso staging activo con Gmail false, modo DESHABILITADO y SMTP excluido; Nginx restaurado; staging7 detenido; temporizador de cierre inactivo. La verificación SELECT posterior confirmó 21 códigos del destinatario, igual al baseline de 21; cola histórica sin cambios y sin aceptación Gmail observada. No se pidió ni introdujo código ni se cambió contraseña.

Antes de esa solicitud hubo dos intentos del controlador sin petición HTTP de código: ambos cerraron correctamente. El primer controlador incluía una opción MySQL no soportada, corregida; el segundo informe identificó el punto real de parada en la comprobación inmediata de 503 tras recarga Nginx. Se corrigió la espera y se ejecutó un modo de controles sin envío: 503 confirmado en dos comprobaciones, SELECT de baseline correcto, Gmail permaneció false y Nginx restaurado. No contar esos preparativos como envíos ni como pruebas exitosas del transporte.

Informe final bruto sanitizado conserva resultado_incierto=true por la regla genérica del controlador para cualquier solicitud sin aceptación. La revisión posterior de HTTP 400, contrato DTO y conteo sin aumento permite precisar que no se generó un código nuevo en esta prueba; se conserva el informe original sin sobrescribir su evidencia.


## Segundo intento autorizado: prueba funcional satisfactoria

Evidencia: informe sanitizado /home/carolina/prueba_codigo_intento2.jsonl y salida systemctl compartidos por el usuario. La conexión SSH de esta sesión rechazó autenticación aun tras recargar la identidad; no se atribuye a esta sesión una consulta remota directa del informe final.

- Segundo intento autorizado expresamente; controlador empleó JSON email y un marcador independiente, conservando el del intento rechazado anterior.
- Respaldos previos verificados: /etc/dimabug/custodia-backups/staging.env.pre-prueba-codigo.20261111T222134Z.567256.bak y /etc/dimabug/custodia-backups/nginx.staging.pre-prueba-codigo.20261111T222134Z.567256.bak.
- API pública bloqueada con HTTP 503 durante la ventana. Baseline usuario/rol activos, OAuth ACTIVA versión 5 y 21 códigos previos. No se comprobó aquí la versión OAuth posterior; no asumir que siguió en 5 después de un posible refresco durante el envío.
- Una petición privada POST /api/auth/recuperar/email, HTTP 200; aceptación Gmail observada, exactamente un código nuevo y cola histórica sin cambios. Código id 41, creado 2026-11-11 22:22:08, expira 22:27:08 según metadatos informados; intentos 0 y usado 0. No se guardan código, hash ni contenido del mensaje.
- Usuario confirmó recepción en el buzón. Validación privada del código satisfactoria; no se cambió contraseña ni se mostró el código. Validar recuperación no consume el código; no afirmar usado=true. No equivale a haber probado cambio de contraseña ni login completo.
- Cierre verificado por el controlador: Gmail false, Procedimientos DESHABILITADO, SMTP excluido, Nginx restaurado, staging7 detenido, sin secretos en salida.
- Estado systemctl compartido por usuario: staging active/running MainPID567507 NRestarts0; staging7 inactive/dead MainPID0 NRestarts0.
- Resultado: generación, transporte Gmail, recepción y validación de recuperación comprobados de extremo a extremo para el destinatario único autorizado dimabug507@gmail.com. No se habilitó operación general ni se probaron envíos de Procedimientos, recordatorios u otros destinatarios. Staging permanece activo en aislamiento; no realizar nuevos envíos sin autorización.
