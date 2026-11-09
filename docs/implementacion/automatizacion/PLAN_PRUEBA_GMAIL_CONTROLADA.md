> Informe/guía de su fase de implementación. El [estado consolidado](../../README.md) y las guías posteriores de recepción/portal distinguen las validaciones más recientes. Los defaults versionados no acreditan el estado privado de una instancia.

# Prueba controlada Gmail API — preparada, no ejecutada

Actualización: el mecanismo vigente es la sección administrativa independiente descrita en [PRUEBA_ADMINISTRATIVA_GMAIL_API.md](PRUEBA_ADMINISTRATIVA_GMAIL_API.md). Ya no es necesario crear el procedimiento QA propuesto originalmente en este documento. Se conserva este plan previo como contexto; no se ejecutaron sus pasos de envío.

## Diagnóstico previo

La revisión de solo lectura confirmó una cuenta OAuth ACTIVA, access y refresh tokens cifrados que se descifran correctamente y un registro de autorización. El token de acceso estaba vigente al comprobarlo. No se forzó una renovación, no se consultó Google y no se cambiaron tokens.

El proceso activo utiliza staging,gmail, DIMABUG_AUTOMATIZACION_MODO=SIMULADO y GMAIL_ENVIO_REAL_HABILITADO=false. Es correcto que los pasos sigan registrando comunicaciones simuladas: autorizar una cuenta no cambia el transporte.

En la consulta de preparación se encontraron cero comunicaciones PENDIENTE sin enviar y cero seguimientos de correos reales en ejecuciones en curso. Este resultado es una instantánea: debe repetirse antes de una futura activación.

La renovación se comprobó mediante mocks: antes del vencimiento se utiliza el acceso actual; cuando quedan menos de 60 segundos se renueva en backend, conserva el refresh si no se rota y exige reautorizar ante invalid_grant. Un fallo temporal conserva las credenciales. Esto no demuestra que un refresh real vaya a ser aceptado por Google en el futuro; no se probó para respetar la restricción de no cambiar tokens almacenados.

## Hallazgo y protección aplicada

Existía una solicitud simulada esperando evidencias con seguimiento pendiente, aún no vencido al revisar. El envío inicial ya registrado no se repite al reintentar la misma comunicación, pero el planificador no distinguía el origen simulado al preparar un recordatorio mediante un transporte real.

Se excluyeron las solicitudes con referencia SIMULADO- de la selección de vencimientos para transporte real. También se añadió una guarda en seguimiento y reserva del envío, para proteger frente a llamadas directas o eventos ya encolados. Se omiten sin actualizar estado, versión, reglas, fechas ni auditoría histórica. No se alteró JWT, OAuth ni el almacén de tokens.

## Mensaje concreto propuesto

- Destinatario Para único: usuario@dimarsa.cl, según la dirección indicada por la usuaria.
- CC: ninguno.
- Asunto: `[DimaBug] Prueba controlada Gmail API — un envío`.
- Mensaje: `Este es el único mensaje de la prueba controlada de Gmail API en DimaBug. Por favor, confirma manualmente su recepción. No contiene datos de procedimientos ni adjuntos.`

La dirección figura exclusivamente en este plan revisable; no se configura como destinatario permanente u obligatorio en la aplicación.

## Preparación posterior a autorización expresa

1. Confirmar explícitamente la autorización para un único envío al destinatario anterior. Esta autorización no se ha solicitado ni asumido en esta tarea.
2. Revisar que no haya otras comunicaciones preparadas sin enviar ni recordatorios reales pendientes durante la ventana de prueba. Las comunicaciones que ya tienen referencia SIMULADO- están excluidas, pero una solicitud sin envío aún no tiene esa marca y una comunicación real pendiente conserva su seguimiento normal. No operar ejecuciones históricas durante esa ventana.
3. Preparar un procedimiento QA separado con un único paso ENVIAR_CORREO, no crítico, condición ENVIO_REGISTRADO, sin aprobación adicional, sin evidencias y sin recordatorios. No reutilizar ni convertir una solicitud simulada o una ejecución histórica. No se creó este procedimiento ni una ejecución en staging durante esta tarea.
4. Configurar, solo después de la confirmación, GMAIL_ENVIO_REAL_HABILITADO=true y DIMABUG_AUTOMATIZACION_MODO=GMAIL_API en el entorno privado del backend. Reiniciar con la configuración local habitual conservando staging,gmail y las credenciales privadas existentes.
5. Comprobar cuenta conectada, habilitación y transporte efectivo. Abrir el paso QA y revisar que haya exactamente un Para, cero CC y el asunto/mensaje anteriores.
6. Pulsar Enviar correo una sola vez. Si el resultado es incierto, revisar el historial antes de realizar cualquier otro intento; no crear otra ejecución para insistir.
7. Verificar fecha/hora, destinatario, una única comunicación y eventos GMAIL_INICIADO/GMAIL_ACEPTADO con referencia Gmail. Un timeout no equivale a rechazo ni justifica un reenvío automático.
8. Comprobar manualmente la bandeja de entrada del destinatario. La aceptación por Gmail API no acredita llegada a la bandeja; gmail.send no permite leerla ni confirmar la recepción.
9. Volver a GMAIL_ENVIO_REAL_HABILITADO=false y SIMULADO al cerrar la ventana, y conservar el resultado y la referencia de auditoría.

No se activaron los valores del paso 4 ni se envió este mensaje.

## Mejoras de interfaz

La pantalla Administración → Conexión Gmail reutiliza tickets-comun.css: cabecera azul, tarjetas, botones primario/segundario/peligro y badges. Muestra por separado conexión y habilitación real de Gmail; esta última representa la bandera de autorización del envío y no reemplaza la selección del transporte en backend.

Se añadieron carga visible, botones bloqueados durante operaciones, hover/focus/disabled, enlace discreto de retorno y diseño en una o dos columnas. La revocación requiere un diálogo con nombre/descripción accesibles, foco contenido mediante CDK existente, Cancelar como foco inicial y cierre por Escape cuando no está revocando. No se añadieron bibliotecas.

Archivos: admin-gmail.component.ts, nuevo HTML y CSS, sus pruebas; SolicitudEvidenciaRepository, su adaptador, AutomatizarEvidenciaService y pruebas GmailOAuthHttpTest. No hay migración nueva.

## Resultados de validación

- 1.089 pruebas backend aprobadas con H2 y mocks. Incluyen exclusión en cola, rechazo de reserva de eventos simulados ya encolados y comparación del registro/auditoría antes y después para probar que omitir un recordatorio no modifica el historial. Los recordatorios de comunicaciones reales siguen cubiertos por pruebas con mocks.
- 417 pruebas Angular aprobadas y compilación correcta. Se verificaron estilos de botones, indicadores independientes, confirmación/Escape, bloqueo durante revocación y distribución sin desbordamiento a 360 y 1280 px.
- Persisten las advertencias previas de presupuesto del bundle y CSS. No se añadieron dependencias.
- Verificación de cuenta y cola en staging exclusivamente de lectura. No se inició OAuth, no se renovaron/revocaron tokens ni se enviaron correos reales. Tampoco se crearon procedimientos o ejecuciones de prueba en staging.
- Se amplió el helper local de reinicio para conservar el perfil staging,gmail, manteniendo SIMULADO y Gmail real deshabilitado; no cambia las credenciales privadas.
