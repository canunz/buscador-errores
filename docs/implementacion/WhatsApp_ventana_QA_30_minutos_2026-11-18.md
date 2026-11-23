# Ventana QA de 30 minutos: preparada, sin apertura

Se autoriza adaptar el lanzador local. La apertura requiere una autorizacion posterior y expresa; este documento no la concede.

## Parametros y controles

- `resources_linux.py --window-minutes 30` selecciona 30 minutos; el valor por defecto sigue siendo 20 para conservar el comportamiento anterior.
- La recepcion real requiere ademas `--public-approval AUTORIZO_RECEPCION_REAL_META_30_MINUTOS` y los parametros privados/TLS existentes. Una autorizacion de 20 minutos se rechaza para una ventana de 30 antes de crear recursos.
- Se mantienen HMAC, participantes autorizados, persistencia y deduplicacion; no se habilitan OCR, busqueda, trabajadores ni envio.
- El certificado QA existente debe tener vigencia restante de al menos la duracion seleccionada mas 10 minutos. Si no cumple, se bloquea; no se renueva automaticamente.
- El plazo sigue calculandose desde el inicio del proceso cloudflared, incluyendo propagacion y configuracion manual. El temporizador termina el conector; el supervisor detiene sus contenedores y verifica el cierre en `window-closure.json`.

## Procedimiento posterior a autorizacion

1. Confirmar manualmente el estado de publicacion de la app Meta y las restricciones indicadas en su panel. Verificar el webhook y suscribir messages no demuestra elegibilidad para mensajes reales. No publicar automaticamente la app.
2. Comprobar que la ventana anterior esta cerrada, configuracion privada completa y servicios QA disponibles. Ejecutar el lanzador con los mismos recursos aislados y los parametros de 30 minutos anteriores, solamente tras autorizacion expresa.
3. Comunicar la URL nueva y el vencimiento exacto en America/Santiago. Esperar la configuracion manual del usuario.
4. En Meta, actualizar la URL de devolucion a `https://<nuevo-host>.trycloudflare.com/webhooks/whatsapp`, utilizar el Verify Token privado existente, verificar y guardar. Confirmar la suscripcion messages v27.0.
5. Enviar un unico mensaje inocuo desde el participante autorizado. Comprobar en MySQL QA un evento TEXTO/PENDIENTE cifrado, intentos cero y ninguna respuesta. No mostrar contenido ni identificadores personales. Dos mensajes manuales distintos no prueban deduplicacion; esta se comprueba con la redelivery del mismo evento.
6. Al vencer el plazo, comprobar CLOSED, conector detenido y todos los contenedores propios detenidos. Para cierre anticipado se conserva `tunnel_client.py --run <directorio-de-la-nueva-ventana> --stop`.

## Validacion realizada

`python -m unittest test_tunnel test_meta_preparation`: 15 pruebas correctas, sin abrir tuneles ni iniciar contenedores. Incluye rechazo de autorizacion de 20 para 30 minutos, autorizacion obligatoria, validacion de binario y cierre de un proceso hijo mediante Windows Job Object.

No se ejecuto una ventana real de 30 minutos. No se modificaron Ubuntu, staging, produccion, DNS, certificados ni bases de datos.
