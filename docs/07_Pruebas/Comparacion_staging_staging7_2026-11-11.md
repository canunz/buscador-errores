# Comparación de staging y staging7 — 2026-11-11

Inspección de lectura: systemd, archivos de entorno mediante verificador privilegiado, metadatos JAR, journal acotado, Nginx y bytecode local con hashes coincidentes. No se iniciaron JARs, servicios, OAuth ni envíos; no se modificaron configuración, secretos ni MySQL. Solo se crearon herramientas e informes de análisis.

## Identidad y rutas

| Aspecto | staging | staging7 |
|---|---|---|
| Unidad | dimabug@staging.service | dimabug@staging7.service |
| Estado final | inactive/dead, MainPID 0 | inactive/dead, MainPID 0 |
| Habilitación | enabled | disabled |
| Directorio | /opt/dimabug/staging, existe | /opt/dimabug/staging7, ausente |
| JAR configurado | /opt/dimabug/staging/backend.jar, existe | /opt/dimabug/staging7/backend.jar, ausente |
| EnvironmentFile | /etc/dimabug/staging.env, existe, root:root 0600 | /etc/dimabug/staging7.env, ausente |
| Versión identificable | 0.0.1-SNAPSHOT | No determinable con evidencia disponible |
| Puerto | SERVER_PORT=8081 y últimos cinco arranques registrados en 8081 | Sin evidencia de puerto histórico |

Plantilla compartida: /etc/systemd/system/dimabug@.service; WorkingDirectory=/opt/dimabug/%i, EnvironmentFile=/etc/dimabug/%i.env. Drop-in exclusivo de staging: /etc/systemd/system/dimabug@staging.service.d/10-success-exit.conf. No se imprimieron órdenes de arranque ni entornos completos.

JAR staging SHA-256: 21df61ccb07090d36417fe3032583a38fe21a3c5721f2dac797387b2d82bd91f. Manifest Implementation-Version y últimos cinco inicios observados: 0.0.1-SNAPSHOT. Esa etiqueta reutilizable no identifica un commit ni demuestra que cada arranque usara el mismo binario. Sin metadatos build/git recuperados por el inspector. Journal limitado a últimas 1500 líneas por unidad; ausencia de resultados no demuestra que staging7 nunca se ejecutó.

Nginx /etc/nginx/sites-enabled/dimabug-staging: staging.dimabug.casol.cl, escucha 80/443; raíz /var/www/dimabug-staging; /api/ hacia http://127.0.0.1:8081; location / para frontend. No se encontró referencia a staging7 en las directivas permitidas de sites-enabled y conf.d consultadas. No fue una expansión exhaustiva de todos los includes de Nginx. El callback /login/oauth2/code/google no aparece como location dedicado en ese resumen; no se verificó su encaminamiento ni el OAuth remoto. El callback localhost usado en la recuperación es independiente.

## Dependencias y función de staging7

No hay Requires/Wants/BindsTo/PartOf entre las dos instancias. Ambas se ordenan After=mysql.service y red; After no equivale a dependencia funcional. Consulta de dependencias inversas de staging7 devuelve solo la propia unidad. staging7 está disabled, carece de directorio/JAR/env actuales y no aporta una ruta Nginx identificada en el alcance revisado. No hay evidencia de una función exclusiva actual, pero tampoco artefacto o historial suficiente para reconstruir su versión o función histórica. No se declara definitivamente obsoleta ni se elimina/modifica su unidad. No había escucha en 8081 en la comprobación final.

## Compatibilidad con tokens recuperados y flujo de códigos

Se halló una copia local en dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar, SHA-256 8c74de5641cf566e63cb011bb8957243101ec5062645bd234aaf5e7c0967966a. El JAR completo es distinto del remoto. Sin embargo, SHA-256 de las cinco clases analizadas en remoto coincide exactamente con su copia local: GmailConfig, CifradoGmailAes, GmailOAuthService, EnvioCodigoAccesoCorreoAdapter y SeguimientoEvidenciaWorker. Solo se extrapolan conclusiones de esas clases, no del JAR completo. Se inspeccionaron estáticamente sus métodos con javap local; no se ejecutó la aplicación.

REA inventarió la copia local: Evidence ev_9e4bfecbad29ccc36e75a0075de2f5707ec036fce59bed2a13e479f5c8b91c50, resumen ev_17c9d9593d37774a7626d68403c3d57c99739a6188cf4eeb4022cd1a706d8813. El inventario no acredita comportamiento en ejecución. La sesión se cerró.

Hallazgos de bytecode:

- EnvioCodigoAccesoCorreoAdapter.enviar y enviarRecuperacion delegan en despachar; gmailApiLista llama GmailOAuthService.disponible. enviarPorGmailApi construye MIME y llama GmailOAuthService.enviar. No requiere el método nuevo enviarAutenticacion.
- GmailOAuthService.disponible comprueba configurado, envioHabilitado y cuenta ACTIVA; enviar repite la comprobación y luego usa acceso/GoogleGmailPort.enviar.
- acceso descifra usando gmail:access; cuando corresponde renovar, usa gmail:refresh y preserva el refresco anterior si Google no devuelve otro.
- GmailConfig permite remitente Gmail personal mediante validación de formato general; configurado también exige OAuth habilitado, ID/secreto y callback/frontend válidos.
- CifradoGmailAes remoto y copia local SHA-256 df81ff27332df6236292e4c6871d3375bc1b9de845ff7a4612726ee7a7c1d7e3. Coincide además con la clase compilada usada por la utilidad de recuperación: AES-256-GCM, prefijo v1:, nonce 12 bytes y AAD de contexto. La custodia nueva ya descifró ambos tokens de versión 5; Google aceptó la renovación en el paso anterior.
- Compatibilidad estática del cifrado y del transporte de códigos confirmada. No se acreditó todavía envío completo, login HTTP ni compatibilidad integral de todo el despliegue.
- El JAR no contiene DespacharCodigosService ni CorreoGmailTransporte. Usa ejecutor asíncrono y captura errores de envío; no equivale al código local con cola persistente y controles nuevos. No dar por disponibles los controles locales de pausa.

## Riesgo de configuración antes de instalar

El perfil application-staging.properties empaquetado contiene dimabug.auth.codigo.usar-gmail-api=true, GMAIL_OAUTH_HABILITADO con default true y GMAIL_ENVIO_REAL_HABILITADO con default true. En staging.env no están las variables Gmail. La ausencia de una variable no significa false en ese JAR. La instalación futura debe fijar explícitamente GMAIL_ENVIO_REAL_HABILITADO=false y revisar la precedencia efectiva. Con false, el transporte Gmail queda indisponible deliberadamente; no esperar códigos enviados durante mantenimiento. No se activó ni modificó nada en esta revisión.

Próximo alcance, tras presentar resultados: solo staging; staging7 permanece detenido y fuera. Antes de instalar aún se debe implementar el traspaso seguro al archivo root 0600 con respaldo y validación de precedencia, sin arranque ni envío. No se requiere desplegar el transporte central nuevo solo para que el adaptador antiguo invoque Gmail API; un eventual despliegue de código sería otro alcance.
