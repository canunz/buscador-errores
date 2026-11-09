# Staging y despliegue

La aplicación se validó localmente con backend 9090/frontend 4200 y configuraciones externas de staging durante tareas anteriores. El usuario confirmó migraciones y recepción de correos/fotografías en esos recorridos. Esto no constituye una certificación integral de producción.

En esta consolidación Git no se cambia ninguna variable, cuenta OAuth, modo de envío o dato de staging, ni se reinician los servidores. Los SQL se revisan como fuente, no se aplican.

Para un despliegue posterior: preparar esquema/directorios/secretos con cuentas de mínimo privilegio, revisar migraciones individualmente, conservar adjuntos y claves de cifrado, configurar HTTPS/orígenes/callback exacto, probar restauración y validaciones de salud. La SPA debe resolver rutas profundas públicas y privadas. La autorización de cuenta Gmail no equivale a habilitar envío real.

El dominio/IP de NFC sigue configurable; no se generan etiquetas físicas en desarrollo. No se ha acreditado un pipeline CI remoto, pentest, UAT ni restauración de producción. Publicar el código en GitHub no despliega automáticamente DimaBug.

[Índice vigente](../README.md).
