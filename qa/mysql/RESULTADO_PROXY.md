# Aplicación controlada del proxy QA — 2026-11-12

Implementación autorizada aplicada. No se creó esquema ni usuarios del MVP, no se ejecutó SQL, no se arrancó frontend/backend, no se modificó staging/producción ni se habilitó correo.

- Imagen HAProxy oficial descargada por el digest aprobado, linux/amd64 verificado.
- Configuración HAProxy validada con exit 0 en contenedor temporal sin red, no root, solo lectura y sin secretos. La advertencia de DNS mysql en esa validación es esperada al carecer de red; el destino sí responde en runtime.
- Respaldo físico previo con MySQL limpiamente detenido, ACL usuario actual/SYSTEM, bajo qa/runtime/backups (ignorado). Integridad SHA-256 del archivo y comparación de sus 165 archivos con una segunda lectura del origen: satisfactorias. No se ensayó restauración arrancando otro MySQL. El respaldo es un tar local restringido, NO una copia cifrada portable: no subir ni compartir. La custodia de contraseñas sigue en sus copias cifradas anteriores.
- Recreado únicamente el contenedor QA con el mismo volumen y la misma imagen MySQL. No se eliminó/reinicializó el volumen ni se cambió la contraseña root.
- Proxy y MySQL QA activos. qa016 y qa0174 continúan detenidos.
- MySQL solo en red interna; sin publicación directa ni ruta predeterminada. Binario 8.0.46 verificado sin SQL.
- Proxy en dos redes, único puerto efectivo 127.0.0.1:13318; proceso no root, filesystem de solo lectura, capacidades eliminadas, no-new-privileges e ip_forward=0 comprobados. Único montaje: configuración de solo lectura.
- Desde Windows se recibe respuesta MySQL 1130 (host no autorizado), sin enviar usuario/contraseña. Esto confirma el trayecto TCP; NO demuestra autenticación funcional. No se concedió root remoto ni se añadieron usuarios para superar ese rechazo.
- Canario exclusivamente local: desde MySQL se abrió TCP al proxy por su dirección interna y se rechazó por su dirección en la red access. Sin conexiones a Internet ni DigitalOcean. No constituye una demostración universal frente a todos los servicios del host o a un proxy comprometido.

Controles pendientes: esquema base completo revisado, cuentas/grants mínimos acordes al origen del proxy, acceso autenticado desde Windows, pruebas de aplicación y restauración funcional del respaldo. Todos requieren su alcance autorizado antes de SQL/arranque del MVP.

Reversión ante incidente: detener únicamente proxy y MySQL QA, conservar volumen y custodia; no ejecutar down -v, prune ni bootstrap -Create. La definición previa puede recuperarse desde el respaldo pero volvería al estado aislado sin publicación efectiva. Revertirla requiere aprobación y conservar el volumen existente.
