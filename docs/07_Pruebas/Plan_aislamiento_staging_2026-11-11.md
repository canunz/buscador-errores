# Preparación del aislamiento de correo y seguimiento — staging

Estado: propuesta preparada, no aplicada. Ambos servicios permanecen detenidos. staging7 queda fuera del alcance. No se accedió a MySQL, modificó configuración remota ni ejecutó la aplicación.

## Evidencia

Se amplió solo el auditor de lectura. El usuario lo ejecutó con sudo; informe sanitizado recogido por SSH. La biblioteca spring-boot-mail-4.1.1.jar del JAR remoto y las clases AutomatizacionConfiguration, CorreoSimuladoAdapter y AutomatizarEvidenciaService coinciden por SHA-256 con la copia local analizada mediante javap. La biblioteca contiene org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration.

El bytecode confirma: CorreoSimuladoAdapter solo marca disponible=true cuando el modo es SIMULADO. Su condición de registro admite cualquier modo distinto de SMTP/GMAIL_API. En modo DESHABILITADO existe el adaptador, pero disponible=false. AutomatizarEvidenciaService.vencidas devuelve lista vacía al no estar disponible el transporte y no invoca el repositorio de vencidas en esa rama. El temporizador sigue registrado; este aislamiento evita el procesamiento operativo de solicitudes, no elimina el scheduler.

En clases propias inspeccionadas no se halló creación explícita de JavaMailSenderImpl ni un bean JavaMailSender propio mediante el escaneo estático acotado. Los adaptadores SMTP conocidos reciben ObjectProvider<JavaMailSender>, por lo que la ausencia del bean está contemplada. El análisis estático no equivale a observar el contexto Spring arrancado; habrá que verificarlo en el futuro arranque autorizado.

## Cambio propuesto solo en /etc/dimabug/staging.env

- Mantener GMAIL_ENVIO_REAL_HABILITADO=false y la custodia sin alteraciones.
- Añadir DIMABUG_AUTOMATIZACION_MODO=DESHABILITADO.
- Añadir org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration a SPRING_AUTOCONFIGURE_EXCLUDE, preservando cualquier exclusión previa.
- Antes de editar, revisar en memoria si SPRING_APPLICATION_JSON declara spring.autoconfigure.exclude o el modo de Procedimientos; si existe una colisión no cambiar JSON a ciegas ni afirmar precedencia. Resolverla expresamente preservando el resto de configuración. Comprobar también alias y fuentes systemd/JVM.
- Crear un respaldo nuevo del estado actual (que ya contiene la custodia), verificar contenido idéntico y conservar root:root 0600 en directorio root 0700.
- Instalar atómicamente y comprobar que solo cambiaron esas dos variables, que el flag Gmail sigue false, que la custodia y SPRING_APPLICATION_JSON permanecen idénticos y que ambas unidades siguen inactive/dead con MainPID=0.

No se propone spring.task.scheduling.enabled como interruptor general: no se ha comprobado que desactive el @EnableScheduling explícito de este JAR. Tampoco usar el nuevo flag de seguimiento del código local, ausente en el binario instalado.

## Efectos y límites

Procedimientos queda temporalmente sin envío real ni simulado y sin procesamiento de seguimientos vencidos por este trabajador. Gmail queda bloqueado. SMTP deja de disponer del transporte autoconfigurado, aunque sus credenciales se conservan. Bienvenida y avisos de tickets no podrán enviar por ese mecanismo; ciertas acciones podrían registrar avisos de no configuración o fallar según sus controles. No es un bloqueo de red a nivel del sistema operativo ni una garantía contra código ajeno al alcance revisado. No implica cero escrituras de toda la aplicación: no se revisaron aquí todas las tareas de inicialización de persistencia o acciones de usuarios.

## Verificación posterior, solo tras otra autorización de arranque

Comprobar servicio, salud, flag efectivo Gmail false, transporte de Procedimientos indisponible y ausencia de JavaMailSender sin exponer configuración. Mantener cerradas las pruebas de envío y operaciones de negocio. No arrancar staging7. Si el contexto no inicia por dependencia de correo, detener la recuperación y revisar el error sanitizado; no reactivar SMTP como solución automática.

## Reversión

Con servicio detenido, restaurar el respaldo inmediato anterior al aislamiento o revertir solo las dos variables a su presencia/ausencia y valores previos, manteniendo custodia nueva y Gmail false. No restaurar un respaldo anterior a la instalación de la nueva custodia. La reversión no incluye arranque automático. No cambiar MySQL ni revocar OAuth.

Referencias oficiales: https://docs.spring.io/spring-boot/reference/using/auto-configuration.html (spring.autoconfigure.exclude), https://docs.spring.io/spring-boot/reference/features/external-config.html (precedencia). El nombre exacto de la clase se comprobó en la biblioteca 4.1.1 empaquetada, no se tomó de otra versión de documentación.


## Ejecución posterior autorizada

Propuesta aplicada únicamente a staging.env, con resultado AISLAMIENTO_INSTALADO_VERIFICADO. Respaldo /etc/dimabug/custodia-backups/staging.env.20261111T215141Z.564647.bak conserva la custodia previa al aislamiento. Ambas unidades siguen detenidas. No se autorizó ni ejecutó aún el arranque. Detalle en Recuperacion_Gmail_2026-11-11.md.
