> Informe/guía de su fase de implementación. El [estado consolidado](../../README.md) y las guías posteriores de recepción/portal distinguen las validaciones más recientes. Los defaults versionados no acreditan el estado privado de una instancia.

# SOLICITAR_EVIDENCIA: flujo funcional de desarrollo

Fecha: 7 de noviembre de 2026. Esta entrega sustituye el estado parcial del primer incremento para la acción **SOLICITAR_EVIDENCIA**. Se integra en los mismos procedimientos, pasos, ejecuciones, checklist, catálogo y avisos de DimaBug.

**Estado del correo: SIMULADO. La integración real con Gmail/OAuth no está implementada ni conectada.** No hay SMTP, llamadas a Gmail, lectura de buzones o credenciales inventadas. Seleccionar `GMAIL` no activa una integración ficticia: el inicio automático se rechaza por proveedor no disponible.

## Probar desde Angular

1. El backend local fue reiniciado con las clases nuevas, perfil `staging`, correo SIMULADO y salud HTTP 200 en `9090`. Recargar Angular en su dirección habitual. Para arranques posteriores, usar el mismo perfil y las variables de conexión/JWT existentes; detener la instancia actual antes de iniciar otra desde el IDE para evitar un conflicto de puerto. No ejecutar migraciones automáticas.
2. Entrar como administrador en **Administración → Sucursales y responsables**. Registrar una sucursal de desarrollo con responsable y un único correo `@dimarsa.cl`. Activar el registro. No usar fotografías personales o información de pago para la demostración.
3. Abrir el procedimiento publicado **Preparar POS**. Usar **Crear borrador con solicitud de evidencias**. Se crea una versión BORRADOR con los mismos pasos; el primero queda crítico y configurado como `VALIDACION_EXTERNA` / `SOLICITAR_EVIDENCIA`. El publicado original y sus ejecuciones se conservan. **Los materiales/archivos del original no se copian**; revisar el nuevo borrador y sus referencias antes de publicarlo.
4. Alternativa genérica: crear un procedimiento BORRADOR, agregar/editar un paso y elegir **SOLICITAR_EVIDENCIA · fotografías y aprobación** en «Acción del paso». Escribir el mensaje al responsable. La acción funciona por configuración del paso, sin buscar el nombre «POS» en el código.
5. Publicar el borrador mediante el control existente. Pulsar **Iniciar procedimiento**, seleccionar sucursal y comprobar el destinatario obtenido del catálogo. Pulsar **Iniciar y solicitar fotografías**.
6. En el checklist aparecerán sucursal, responsable, destinatario, ID/ronda, envío simulado, fecha, estado `ESPERANDO_EVIDENCIA` y próximo seguimiento. **No aparece «Marcar como hecho» en el paso externo.**
7. Pulsar **Simular recepción con tres imágenes de prueba**. Se generan fotografías sintéticas claramente rotuladas; el backend las valida, normaliza, guarda y asocia a esa ronda. El paso pasa a `EVIDENCIA_RECIBIDA`, conserva `cumplido=false` y aparece un aviso para el técnico.
8. Esperar que carguen las tres imágenes. Revisarlas y pulsar **Aprobar fotografías y continuar**. Una sola petición registra la decisión, completa el paso y permite avanzar. Angular vuelve a consultar el checklist y presenta el siguiente pendiente; **no envía un PATCH de marcado manual del paso aprobado**.
9. Para probar rechazo, recibir fotografías en otra ejecución, escribir el motivo y elegir **Rechazar y solicitar nuevas fotografías**. Se conserva la ronda anterior y se crea/envía lógicamente una nueva. Un rechazo definitivo cierra esa solicitud sin generar otra ronda.
10. Para probar plazos sin esperar días, expandir «Probar recordatorios» mientras la solicitud espera fotos. Los botones simulan 24/72/120 horas **solo para esa solicitud**; no cambian el reloj del servidor. Repetir un botón no duplica el envío lógico. Revisar «Actividad y trazabilidad» y los avisos de DimaBug.

El flujo manual anterior sigue disponible para definiciones MANUAL. No se convierte una instalación física, un comando o una carga de folios en actividad completada por el paso del tiempo.

## Configuración

El perfil `staging` conserva las variables de conexión y JWT previas. Añade estas opciones:

| Variable | Valor inicial | Uso |
|---|---|---|
| `DIMABUG_AUTOMATIZACION_MODO` | `SIMULADO` en staging; `DESHABILITADO` fuera de esa configuración | Habilita exclusivamente el proveedor de pruebas |
| `DIMABUG_RECORDATORIO_1_HORAS` | `24` | Primer seguimiento |
| `DIMABUG_RECORDATORIO_2_HORAS` | `72` | Segundo seguimiento |
| `DIMABUG_ESCALAMIENTO_HORAS` | `120` | Aviso/escalamiento al técnico |
| `DIMABUG_SEGUIMIENTO_INTERVALO_MS` | `60000` | Consulta periódica de vencimientos persistidos |

Los tres plazos deben ser positivos, crecientes y no superar 8760 horas. Se capturan en cada solicitud; cambiar el entorno afecta solicitudes nuevas. Son **horas transcurridas**, no días hábiles. Fechas de solicitud, evidencia y auditoría se guardan como instantes; el navegador las presenta en su zona horaria.

La simulación se bloquea con perfiles `prod`, `production` o `produccion` y ante una conexión MySQL remota. El modo predeterminado del adaptador es DESHABILITADO. El perfil staging habilita explícitamente la demostración local. Ninguna credencial Google es necesaria para el simulador.

Para fotos se permiten JPEG/PNG de hasta **2 MB y 4 megapíxeles por categoría**, con dimensiones máximas de 4000 por lado. Se requiere una fotografía para MUEBLE, RED y ELECTRICIDAD. La imagen se decodifica y reescribe a JPEG sin reutilizar sus metadatos. El límite global multipart es mayor para conservar otros materiales de hasta 10 MB; el endpoint de evidencias impone el límite específico de 2 MB.

## Persistencia y migración

[03_flujo_evidencias_desarrollo.sql](03_flujo_evidencias_desarrollo.sql) es aditivo. El usuario confirmó su aplicación con una cuenta administradora en `127.0.0.1:3307 / dima_bug_staging`; se verificaron después sus mapeos reales con Hibernate. La cuenta de aplicación no recibió privilegios permanentes de DDL.

- `pr_paso`: modo de ejecución y mensaje de evidencia. Los valores existentes conservan MANUAL.
- `se_ejecucion_paso`: reutiliza contrato capturado, estado detallado, revisión, fecha y proyección `cumplido` del primer incremento.
- `u_sucursal`: catálogo autorizado existente en el incremento anterior.
- `se_solicitud_evidencia`: contexto capturado de sucursal/responsable, política, ronda, estado, IDs simulados y seguimiento.
- `se_evidencia`: tres imágenes privadas de cada ronda, categoría, fecha y hash. Los bytes permanecen en MySQL, no en rutas públicas.
- `se_evento_ejecucion`: actividad automática/humana por solicitud y ejecución. Solo inserción por el adaptador; no se afirma inmutabilidad criptográfica frente a un administrador de base.

No se duplicó la tabla de pasos, la ejecución ni los archivos de conocimientos como fotos de una instancia. No se ejecutaron migraciones en producción, borrados de datos o backfills de evidencia histórica.

## Contratos principales

| Método/ruta | Función |
|---|---|
| `POST /api/procedimientos/{id}/ejecuciones` | Manual sin body; externo con sucursalId y claveInicio UUID |
| `GET /api/sucursales/{id}/destinatario` | Responsable/contacto para la selección autenticada |
| `POST /api/procedimientos/{id}/version-evidencia` | Solo administrador; nuevo BORRADOR revisable, sin alterar el PUBLICADO |
| `GET /api/ejecuciones/{id}/automatizacion` | Solicitudes/rondas, metadatos de fotos y actividad |
| `POST /api/ejecuciones/{id}/solicitudes-evidencia/{solicitud}/simular-respuesta` | Tres imágenes sintéticas; remitente esperado y mensajeId UUID |
| `POST .../{solicitud}/evidencias-simuladas` | Multipart: mueble, red, electricidad, remitente, mensajeId |
| `POST .../{solicitud}/decision` | Versión obligatoria, aprobar explícito, motivo para rechazo y definitiva opcional |
| `POST .../{solicitud}/simular-tiempo` | Seguimiento de desarrollo con horas virtuales por solicitud |
| `GET .../{solicitud}/fotos/{foto}` | Bytes JPEG autenticados, pertenencia y `Cache-Control: no-store` |
| `GET /api/ejecuciones/avisos-evidencia` | Fotos pendientes de revisión/escalamientos del técnico autenticado |

## Controles y fallos

- Inicio externo: clave única por actor, procedimiento y sucursal validada. Repetir la misma clave recupera la ejecución; reutilizarla con otro contexto responde 409. Se serializa con el bloqueo del procedimiento y la unicidad SQL. No se impone unicidad global por sucursal.
- Estado, aprobación, rechazo, cancelación y coordinación comparten el bloqueo de cabecera de ejecución. Una decisión lleva la versión de la solicitud y actúa solo sobre la ronda vigente.
- Un PATCH legado no completa pasos externos, y un técnico no puede marcar pasos manuales posteriores con predecesores pendientes. No se reabre indiscriminadamente una dependencia validada.
- Recibir fotografías nunca completa el paso. La aprobación es una acción humana explícita; el rechazo requiere motivo. El backend valida las tres categorías aunque se intente omitir la interfaz.
- Las respuestas de prueba requieren usuario interno autorizado, ejecución propia/administrador, ronda activa, remitente esperado y tres archivos válidos. El mismo mensajeId/huella no duplica fotos o eventos; otro contenido con ese ID se rechaza. Un correo del mismo dominio no da acceso a otra ejecución.
- **La identidad del jefe no está verificada por Google en el simulador.** La auditoría lo indica. No hay endpoint de recepción pública que confíe en From ni un bypass del JWT de DimaBug.
- Las fotografías se descargan mediante HttpClient y JWT; Angular usa URLs blob temporales que se revocan al destruir el panel. No se incorporan tokens a URLs ni se expone un directorio público.
- El worker vuelve a leer estado bajo bloqueo, guarda la regla aplicada y su auditoría en una transacción. Al recibir fotos, aprobar, cancelar o rechazar definitivamente se detienen los seguimientos correspondientes. Una ronda rechazada y reemplazada no continúa enviando recordatorios.
- Si hay varios vencimientos acumulados, solo se procesa el más reciente, evitando ráfagas. En simulación, envío lógico + estado + auditoría son parte del commit; no existe una llamada externa que pueda perder su confirmación.
- Se probaron fallos simulados del proveedor y rollback. **No se promete exactamente una vez para Gmail real**: ese adaptador necesitará outbox/conciliación de resultados inciertos y validación de identidad antes de habilitarse.

## Validación y límites

Los resultados definitivos y el inventario de archivos se registran en [RESULTADOS_FLUJO_EVIDENCIAS.json](RESULTADOS_FLUJO_EVIDENCIAS.json). **1003 pruebas Java correctas y 326 pruebas Angular correctas**, sin fallos; compilación de desarrollo y comprobación de tipos correctas. Incluyen HTTP con login/JWT y persistencia H2, mapeos de MySQL y un recorrido transaccional completo en MySQL local con rollback de fixtures sintéticas. No se enviaron correos reales. La prueba con rollback puede avanzar contadores AUTO_INCREMENT, pero no conserva filas sintéticas.

La comprobación visual con la habilidad de uso de computadora quedó bloqueada: la herramienta no pudo verificar la URL de Chrome y detuvo el control de ese navegador. No se intentó eludir ese control. Las pruebas de componentes y HTTP no se presentan como una sesión visual de aceptación terminada.

Pendientes para Gmail real: proyecto/cliente OAuth autorizado, buzón dedicado, gestión de secretos/refresh token, envío y lectura Gmail API, cursor de sincronización, correlación MIME, identidad externa verificada, cuarentena/antimalware, retención y operación de outbox con conciliación. El código actual proporciona el puerto y una simulación persistida funcional; **no contiene un adaptador Gmail real terminado**.

Otros límites del piloto: tres categorías fotográficas fijas para instalaciones; política de plazos por entorno capturada por solicitud; sin calendario laboral, pausa operativa o configuración arbitraria de formularios de evidencia. La creación de una versión copia pasos, no materiales/adjuntos. Las métricas agregadas y la automatización del correo de folios no forman parte de esta acción implementada.
