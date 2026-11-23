# DimaBug: aislamiento mínimo de correos para staging

Estado: implementado y compilado localmente; no desplegado. No se modificaron
staging, producción, staging7, MySQL ni la custodia OAuth. No hubo envíos reales.

## Cambio

| Emisor | Propiedad | Valor por defecto |
|---|---|---|
| Códigos y despachador | `dimabug.auth.correo.habilitado` | `true`, conserva el valor anterior; staging lo configura explícitamente |
| Bienvenida | `dimabug.correo.bienvenida.habilitado` | `false` |
| Tickets críticos | `dimabug.correo.ticket-critico.habilitado` | `false` |

El código utiliza la bandera existente del despachador también en el adaptador
que encola mensajes. Si es `false`, no prepara MIME, no cifra contenido ni guarda
un trabajo en `u_correo_codigo`. El contrato público uniforme de solicitud se
conserva; no se cambió el servicio que genera hashes de códigos.

Bienvenida y tickets críticos retornan antes de consultar el transporte, registrar
callbacks de commit o presentar tareas al ejecutor cuando están deshabilitados.
Los puertos siguen disponibles para creación de usuarios y manejo de tickets.
No se modificó el transporte Gmail, el cifrado, la autorización OAuth ni el JWT.

La base es la copia congelada de `staging-minimo-20261114T025917Z`, no la rama
de trabajo completa. Los cambios posteriores de WhatsApp/OCR no se incorporan.
También se aplicó el mismo parche mínimo a los tres adaptadores del árbol de
trabajo, conservando el resto de los cambios locales.

## Artefactos y compatibilidad

- Nuevo backend: `qa/artifacts/staging-auth-isolation-20261114/artifacts/backend.jar`.
- SHA-256: `a74f4750ee4c9b3c166449ab669655bd00bb14e2b234ccc3de0c903aa0597c8d`.
- Backend base desplegado: `ec789866bdce8087d05aff03452b6978b6b0b5eb1d5fcbc9d88503a8996ecb8f`.
- Frontend que se conserva: `5729501bfe32df4570418275f63a09ceef5d1825ad24cf1ef2a4400d65228949` (ZIP del release anterior).

El inventario de 653 entradas de aplicación y todas las bibliotecas del JAR se
conserva. Solo cambian las clases de los tres adaptadores y dos clases internas
asociadas. Controlador de autenticación, DTO, JWT y seguridad permanecen
idénticos a nivel binario. No cambian rutas, JSON, entidades ni consultas SQL.

Para compilar usar `build_release.py` y `pom-release-isolated.xml` dentro del
release congelado. El POM adicional excluye el perfil empaquetado
`application-staging.properties`, ausente en el JAR desplegado, para conservar
la configuración externa. No modifica el POM del proyecto de trabajo.

## Pruebas locales

155 pruebas satisfactorias, cero fallos, errores u omisiones: 10 de aislamiento
(8 combinaciones, valores por defecto sin propiedades y variables de entorno),
2 de notificaciones existentes, 3 de mantenimiento y 140 regresiones P0. Los
puertos son simulados; no se usaron MySQL, SMTP ni Gmail reales. Los resultados
se conservan en `evidence/tests-summary.json` del release.

## Configuración externa propuesta: todavía no aplicada

Conservar todas las credenciales y rutas actuales. Cambiar únicamente las
banderas de correo y explicitar los controles siguientes en el EnvironmentFile:

```properties
GMAIL_ENVIO_REAL_HABILITADO=true
DIMABUG_GMAIL_ENVIO_REAL_HABILITADO=true
DIMABUG_AUTH_CORREO_HABILITADO=true
DIMABUG_CORREO_BIENVENIDA_HABILITADO=false
DIMABUG_CORREO_TICKET_CRITICO_HABILITADO=false
DIMABUG_AUTOMATIZACION_MODO=DESHABILITADO
DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO=false
DIMABUG_AUTH_CORREO_MAXIMO_MINUTO=10
DIMABUG_AUTH_CORREO_INTERVALO_MS=10000
DIMABUG_AUTH_CODIGO_MINUTOS_VIGENCIA=5
DIMABUG_AUTH_CODIGO_SEGUNDOS_REENVIO=60
DIMABUG_AUTH_CODIGO_MAXIMO_15_MINUTOS=5
DIMABUG_AUTH_CODIGO_MAXIMO_DIA=20
DIMABUG_AUTH_LIMITE_IP_MINUTO=20
```

La variable canónica `DIMABUG_GMAIL_ENVIO_REAL_HABILITADO` es necesaria porque
el archivo externo actual fija directamente `dimabug.gmail.envio-real-habilitado`.
No basta cambiar únicamente `GMAIL_ENVIO_REAL_HABILITADO`. Conservar la exclusión
actual de `org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration`.
OAuth debe continuar habilitado con su configuración existente; no generar ni
reemplazar secretos. No reutilizar los límites temporales de la prueba única.

Los cinco intentos de validación, uso único, hash del código, contenido cifrado,
caducidad y límites por cuenta permanecen en las clases originales. El límite
por IP existente usa la dirección observada por el backend; detrás de Nginx
puede ser compartido. No se modifica la confianza en cabeceras reenviadas.

## Despliegue mínimo posterior, sujeto a autorización

1. Verificar identidad SSH con la huella de DigitalOcean ya confirmada,
   StrictHostKeyChecking y el manifiesto del nuevo release. Solo staging.
2. Abrir mantenimiento privado de staging y comprobar ausencia de escritores
   externos. Inspeccionar la cola sin descifrar contenidos: detenerse si hay
   trabajos históricos enviables o destinatarios que requieran revisión. No
   despachar una cola desconocida ni modificar sus estados manualmente.
3. Crear un respaldo ACTUAL privado: JAR integrado `ec7898...`, EnvironmentFile,
   properties externo, Nginx, manifiestos y un dump consistente de staging.
   Verificar hashes y lectura. Registrar esquema/privilegios y línea base.
   Mantener el frontend y los adjuntos sin reemplazos.
4. Reutilizar el modelo de vigilante y contención del despliegue verificado,
   adaptado a backend/configuración. No ejecutar sin adaptar el controlador
   anterior que reinstala también frontend y apunta a un JAR de rollback viejo.
5. Detener solo `dimabug@staging.service`, verificar cero sesiones de aplicación
   y sustituir solo el JAR. Primer arranque con Gmail y despacho aún `false`:
   verificar salud, Hibernate `validate`, MySQL y contratos sin pedir códigos.
6. Con el JAR nuevo verificado y la cola revisada, instalar la configuración
   propuesta, manteniendo custodia y propiedades de seguridad. Respaldar y
   conservar `root:root 0600` del env y los permisos externos ya corregidos.
7. Reiniciar solo staging y comprobar que las banderas persisten en el proceso,
   los nuevos emisores están deshabilitados y no queda un límite temporal de
   systemd. Conservar SMTP excluido, automatizaciones apagadas y staging7 parado.
8. Validar salud, MySQL, esquema/privilegios y datos antes de solicitudes de
   negocio. Restaurar Nginx de staging y comprobar HTTPS, Angular y `/api`.
9. Completar una solicitud desde el formulario Chrome con cuenta activa y
   destinatario autorizado; introducir el código solo en el formulario. Verificar
   estado `ENVIADO`, un consumo, JWT y permisos, sin mostrar códigos ni tokens.
   El acceso registra `usuario_ultimo_ingreso` y MySQL puede actualizar
   `usuario_fecha_actualizacion` mediante ON UPDATE: verificar exclusivamente
   esas marcas del usuario probado, sin ocultar otros cambios.

## Reversión

Si falla arranque, aislamiento o configuración, mantener mantenimiento y detener
solo staging. Restaurar juntos el JAR y los archivos de configuración del
respaldo ACTUAL, con Gmail y despacho deshabilitados, verificar lectura/hashes y
arrancar el backend anterior. No restaurar solamente el JAR antiguo conservando
Gmail habilitado, porque ese JAR no posee las nuevas barreras.

No restaurar automáticamente MySQL, adjuntos ni frontend. No usar como rollback
el JAR `21df61...` del respaldo previo al primer despliegue integrado; el estado
de partida de esta operación es `ec7898...`. Retirar mantenimiento únicamente
después de verificar salud, correo apagado y HTTPS. Si no se confirma el cierre,
detener e informar estado desconocido, sin reintentos automáticos.

## Pendientes

El despliegue, la persistencia real tras reinicio y la prueba del formulario
Chrome requieren la ejecución autorizada posterior. Las pruebas actuales son
locales, con transporte simulado, sin MySQL ni Gmail reales. No se declara
habilitado todavía el inicio de sesión habitual en staging.
