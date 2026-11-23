# Proxy MySQL QA: plan aprobado y aplicado

Resultado y límites de verificación: ver `RESULTADO_PROXY.md`. Las instrucciones siguientes documentan el diseño y la secuencia aprobados; no repetirlos sobre recursos existentes.

## Archivos y alcance

`compose.qa-proxy.json` es una definición completa alternativa, no un override. No combinarla mediante varios `-f`: Compose puede conservar/concatenar puertos de la definición anterior. La definición anterior permanece intacta como evidencia y referencia del bootstrap, no debe ejecutarse de nuevo para resolver conectividad.

Se reutiliza EXCLUSIVAMENTE el volumen `dimabug-qa-mvp-13318-mysql-data` y la red `dimabug-qa-mvp-13318-net` mediante `external: true`. Aquí external significa recurso preexistente administrado fuera de este archivo, NO acceso a Internet. Se comprueba por separado que esa red tenga Internal=true. No se reutilizan recursos qa016/qa0174, staging o producción.

MySQL pierde el binding directo y mantiene su única red interna. El proxy usa esa red y una bridge nueva `dimabug-qa-mvp-13318-access`. Solo el proxy publica `127.0.0.1:13318:13318`. El backend Windows conserva su URL actual; no se cambia configuración Angular/Spring.

## Imagen y seguridad del proxy

Imagen oficial propuesta: `haproxy:3.2.26-alpine3.24@sha256:5378234d19457d645ed6d5a0ac994ef196614f9a2cabda8f6ccd6ac8815f9098`, linux/amd64. Manifiesto verificado por SHA-256 contra el registro oficial; no se descargaron capas. Índice multiarquitectura: `sha256:1c3568e520453cc8ba98c39501febcf450429c80e6cc27ecad779961d76e597c`.

Proceso UID/GID 65534, todas las capacidades eliminadas, no-new-privileges, filesystem de solo lectura, límites 0.5 CPU/128 MiB/64 procesos y reenvío IPv4 deshabilitado. Único montaje: configuración HAProxy de solo lectura. Sin custodia, credenciales, volumen MySQL, socket Docker, variables de entorno ni archivos de entorno. Sin logs de tráfico ni interfaz administrativa; logs del proceso limitados en tamaño. Estos ajustes requieren validación real con la imagen antes de aceptarlos como operativos.

`haproxy.qa-local.cfg` reenvía TCP exclusivamente a `mysql:3306`, sin terminación TLS ni PROXY protocol. Usa DNS interno Docker para adaptarse a cambios de IP. No conoce usuarios o contraseñas, aunque un proxy TCP sigue siendo un componente de confianza en el trayecto de autenticación. La conexión verá el origen del proxy: los futuros grants deben restringirse teniendo en cuenta ese origen. No se concede root remoto para probarlo.

La red normal permite salida al proxy. El objetivo es mantener MySQL sin salida directa, no afirmar que todos los componentes carecen de conectividad. No usar este proxy como proxy general, añadir destinos dinámicos ni IP forwarding. Internal=true tampoco sustituye un firewall completo frente a servicios del host/otros miembros de la misma red.

## Validaciones disponibles sin activar

- `python -I -B qa/mysql/verify_proxy_preparation.py`: controles estáticos sin Docker/SQL/credenciales.
- `python -I -B qa/mysql/test_proxy_preparation.py`: regresiones de seguridad mediante mutaciones en memoria.
- Compose config --quiet valida estructura; no prueba sintaxis HAProxy ni disponibilidad real.
- `--runtime` del verificador está reservado para después de la activación aprobada: solo lecturas de metadatos Docker y saludo MySQL por TCP; no autentica ni ejecuta SQL. Una conexión sin autenticar puede generar una advertencia de conexión abortada en MySQL. No prueba por sí solo bloqueo de salida ni identidad criptográfica del servidor.

## Secuencia posterior, requiere autorización distinta

1. Reconfirmar detenido el contenedor QA, identidad del volumen, red interna, ausencia de proxy/red access ajenos, puerto libre y custodia válida. Guardar metadatos filtrados y definición previa sin secretos. Confirmar respaldo recuperable del volumen antes de recrear MySQL; no ejecutar down -v, prune ni borrar el volumen.
2. Descargar exclusivamente el digest aprobado HAProxy y verificar arquitectura. Validar `haproxy -c` en un contenedor temporal sin red ni secretos, con configuración de solo lectura; requiere permiso para crear ese recurso temporal.
3. Recrear SOLO el contenedor MySQL QA para retirar su publicación, conservando imagen, custodia y volumen actuales; no reaplicar inicialización ni cambiar root. Crear red access y proxy. No arrancar backend/frontend, crear usuarios/esquema ni ejecutar migraciones.
4. Verificar runtime, configuración efectiva de seguridad y montajes, MySQL únicamente en red interna y proxy en ambas; confirmar que no hay publicación LAN/IPv6/puertos adicionales. Probar TCP y saludo 8.0.46 desde Windows. Autenticación con usuario mínimo es una fase posterior, porque todavía no existe.
5. Verificar rutas y ausencia de salida directa desde MySQL. Si hace falta una prueba activa, usar un destino canario local controlado y autorizado, sin conexiones a DigitalOcean ni Internet. Comprobar comunicación interna permitida y que no se ha habilitado reenvío IP a través del proxy.
6. Ante fallo, detener proxy y MySQL QA. Conservar datos y custodia; restaurar la definición anterior únicamente si se aprueba, siempre reutilizando el mismo volumen, sin up automático. Volver a la definición anterior no recupera el puerto: devuelve el estado detenido y aislado previo. No tocar contenedores antiguos ni configuración del motor global.

Fuentes: https://github.com/moby/moby/discussions/53256 ; https://docs.docker.com/desktop/features/networking/ ; https://hub.docker.com/_/haproxy ; https://docs.haproxy.org/3.2/configuration.html
