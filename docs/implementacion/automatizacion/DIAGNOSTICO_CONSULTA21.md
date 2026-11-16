# Bloqueo y parpadeo de automatización: ejecución 21

## Causa identificada
La consulta se disparaba tanto desde ngOnChanges, ante cualquier objeto de paso recreado por el padre, como desde un temporizador cada cinco segundos. El temporizador también operaba cuando no existía ninguna solicitud. La ejecución 21 tiene seis pasos y, al diagnosticarla, cero solicitudes: no había una respuesta externa que esperar ni una comunicación que sondear.

La plantilla reutilizaba solicitud=null para dos situaciones distintas: aún no consultado y consulta correcta sin solicitud. Cada refresco ocultaba el panel «Pendiente de solicitud» y volvía a mostrar «Consultando solicitud…». Esto explica el parpadeo observado y la desaparición del botón.

El controlador de automatización componía una sola respuesta mediante llamadas públicas separadas al servicio: listar, eventos e historial, además de comprobaciones de disponibilidad. Estas llamadas abrían transacciones separadas y repetían autorización y lecturas. Los metadatos de fotos se obtenían cargando también sus bytes.

La conexión 127.0.0.1:3307 está atendida por ssh.exe: es un túnel hacia MySQL, no una instancia MySQL local. Se midieron unos 276–446 ms por consulta y 294–511 ms por COMMIT de lectura. Se capturó un hilo real HTTP dentro de ConnectionImpl.commit → HikariProxyConnection.commit → AutomatizarEvidenciaService.eventos → AutomatizacionEjecucionController.estado, esperando una respuesta del socket MySQL. No esperaba Gmail.

En capturas anteriores todos los hilos HTTP estaban libres y no había conexiones del proxy al backend; correspondían a intervalos entre peticiones. Chrome mostró Stalled y peticiones repetidas. Esto es compatible con la presión de consultas acumuladas; no se atribuye cada intervalo de Stalled a un bloqueo SQL, ni se afirma haber capturado toda la cola de Chrome.

## Corrección funcional
- ngOnChanges consulta solo al cambiar ejecución/paso o su revisión/estado, no por un objeto equivalente del padre.
- Sin solicitud no hay sondeo periódico: «Pendiente de solicitud» es un resultado válido y estable.
- Solo una solicitud existente que espera respuesta o revisión justifica el sondeo; se evita solapar lecturas.
- Los refrescos posteriores conservan los datos visibles. El indicador inicial depende de consultaRealizada, no de que exista una solicitud.
- Las lecturas se cancelan al cambiar de paso o salir. Las cachés compartidas de ejecución/pasos usan refCount para cancelar el HTTP cuando ya no hay suscriptores.
- El endpoint mantiene su ruta y contrato JSON, pero obtiene todo mediante consultarEstado: una autorización y una transacción de lectura.
- Historial y metadatos se reutilizan en la misma lectura. La proyección de fotos trae id/categoría/huella y no el BLOB; las fotos se descargan cuando se abren.
- Consultar estado no crea solicitudes ni envía mensajes. La disponibilidad del simulador no abre una transacción SQL por sí sola.

## Límites y recuperación
Como protección adicional, las consultas tienen un plazo de 15 segundos, errores anunciados y Reintentar. El sondeo se detiene tras un error hasta la recuperación. Un timeout no reenvía correos ni repite mutaciones. finalize limpia las banderas de carga y las suscripciones se cancelan al destruir la pantalla.

Staging incorpora límites de conexión/validación del pool, conexión/socket JDBC y consulta. Las lecturas de los servicios de ejecución tienen un límite transaccional de ocho segundos. El proxy tiene límites de 20 segundos. Estos límites son complementarios: no sustituyen la corrección del sondeo y las transacciones repetidas. No se modificó ni reinició el túnel SSH.

## Evidencia sobre datos reales
La comprobación de solo lectura invocó controladores y servicios con la identidad del propietario de la ejecución dentro de un contexto sin servidor, sin generar JWT, sin tareas programadas y con el envío expresamente prohibido.

| Lectura | Tiempo medido | Transacciones |
|---|---:|---:|
| listar + eventos + historial separados | 6802 ms | 3 |
| estado agregado y serialización JSON | 2458 ms | 1 |

La ejecución 21 se leyó correctamente, con seis pasos. El estado agregado contenía cero solicitudes y su JSON ocupaba 87 caracteres. No se crearon ejecuciones ni se enviaron correos en esta comprobación. No se modificaron datos o esquemas.

## Validación automatizada
- Java: 1020 pruebas aprobadas, sin errores ni fallos.
- Angular: 362 pruebas aprobadas; compilación correcta.
- Las nuevas pruebas reproducen refrescos equivalentes del padre durante 60 segundos: no generan más GET cuando no hay solicitud.
- Un GET lento mantiene un único HTTP activo y conserva visible el estado ya consultado.
- Se prueban timeout, cancelación, recuperación, backend no disponible, respuesta lenta, error de BD/503 y proveedor de correo no disponible.
- Una prueba JPA comprueba exactamente una transacción para el estado completo y que no se crean comunicaciones.
- El flujo de envío idempotente y su historial siguen cubiertos por las pruebas HTTP/JWT y persistencia aisladas; ningún test envía correo real.

## Archivos modificados
- frontend-dimabug/src/app/features/ejecucion/validacion-externa.component.ts y .html.
- frontend-dimabug/src/app/features/ejecucion/ejecucion-checklist.component.ts.
- frontend-dimabug/src/app/core/services/ejecucion.service.ts y automatizacion-ejecucion.service.ts.
- frontend-dimabug/src/app/core/http/plazo-consulta.ts y frontend-dimabug/proxy.conf.json.
- AutomatizarEvidenciaService, GestionarEjecucionService y AutomatizacionEjecucionController.
- FotoEvidenciaResumen, SolicitudEvidenciaRepository y SolicitudEvidenciaRepositoryAdapter.
- application-staging.properties y pruebas correspondientes.

## Runtime y comprobación de usuario
Se actualizaron únicamente las instancias identificadas: backend PID 17468 en 9090 y Angular PID 38276 en 4200. Salud directa y a través del proxy: 200. No se cambiaron puertos ni se finalizaron procesos desconocidos.

Pendiente: confirmar el resultado en la sesión autenticada de Chrome de la usuaria y, si decide ejecutar una simulación desde la ejecución 21, comprobar el evento y la fecha/hora de ese envío. No se afirma haber enviado un mensaje desde la sesión de la usuaria. La latencia del túnel SSH permanece como dependencia del entorno; hardware y otras pantallas pueden requerir diagnóstico propio si continúan lentas.
