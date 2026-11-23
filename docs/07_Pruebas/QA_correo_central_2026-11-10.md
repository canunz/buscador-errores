# QA — Correo central y login por código

Fecha: 2026-11-10. Resultados locales consolidados por suite tras los cambios pertinentes. No se ejecutó la suite completa del repositorio ni una prueba de servidor real.

## Resultado

- Backend: **151 pruebas en 17 clases, cero fallos, errores u omitidas**. La ejecución ampliada tuvo un fallo de elegibilidad temporal del fixture; el cierre corrigió la preparación temporal y volvió a ejecutar los cinco grupos afectados (cola, identidad, aislamiento, límites y notificaciones), todos aprobados.
- Angular: **5 pruebas aprobadas** (login y administración de usuarios) en ChromeHeadless.
- Build Angular de producción: aprobado con salida temporal `dist-correo-qa`, eliminada después de verificar el resultado. Advertencias de presupuestos de bundle/CSS y CommonJS de Tesseract ajenas al cambio. En Karma hay avisos 404 de imágenes de fondo/logo; no impidieron las pruebas funcionales.
- `git diff --check`: aprobado, sin errores de espacios. Git avisa de conversión LF/CRLF en algunos archivos del árbol de trabajo.
- No se enviaron correos reales ni se ejecutaron migraciones de despliegue. Los DDL y datos de los tests se limitaron a H2 en memoria.

| Clase | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| AutenticarUsuarioServiceTest | 11 | 0 | 0 | 0 |
| Hu01SecurityIntegrationTest | 16 | 0 | 0 | 0 |
| AdministrarUsuariosServiceTest | 13 | 0 | 0 | 0 |
| BCryptPasswordEncoderAdapterTest | 1 | 0 | 0 | 0 |
| JwtTokenServiceAdapterTest | 4 | 0 | 0 | 0 |
| GoogleGmailHttpTest | 11 | 0 | 0 | 0 |
| CifradoGmailTest | 7 | 0 | 0 | 0 |
| GmailOAuthHttpTest | 29 | 0 | 0 | 0 |
| CorreoSinSucursalHttpTest | 13 | 0 | 0 | 0 |
| CorreoCodigoIntegracionTest | 12 | 0 | 0 | 0 |
| AislamientoCorreoAutenticacionTest | 4 | 0 | 0 | 0 |
| LimiteAutenticacionFilterTest | 2 | 0 | 0 | 0 |
| NotificacionesCorreoCentralTest | 2 | 0 | 0 | 0 |
| AutomatizacionHttpPersistenciaTest | 1 | 0 | 0 | 0 |
| EdicionProcedimientoAdminHttpTest | 7 | 0 | 0 | 0 |
| HardwareProcedimientoHttpTest | 12 | 0 | 0 | 0 |
| TicketsArquitecturaTest | 6 | 0 | 0 | 0 |

## Comandos reproducibles

Desde `dima-bug-backend`:

```powershell
.\mvnw.cmd -q "-Dtest=AutenticarUsuarioServiceTest,Hu01SecurityIntegrationTest,AdministrarUsuariosServiceTest,BCryptPasswordEncoderAdapterTest,JwtTokenServiceAdapterTest,GoogleGmailHttpTest,CifradoGmailTest,GmailOAuthHttpTest,CorreoSinSucursalHttpTest,CorreoCodigoIntegracionTest,AislamientoCorreoAutenticacionTest,LimiteAutenticacionFilterTest,NotificacionesCorreoCentralTest,AutomatizacionHttpPersistenciaTest,EdicionProcedimientoAdminHttpTest,HardwareProcedimientoHttpTest,TicketsArquitecturaTest" "-Ddimabug.auth.limite-ip-minuto=1000" test
```

El límite IP se amplía solo en el proceso de pruebas para los fixtures compartidos. La prueba dedicada comprueba el rechazo con un límite pequeño; los límites de producción no se amplían. El contexto de cola también declara su presupuesto de prueba para no consumirlo entre casos.

Cierre dirigido después de ajustar el fixture temporal y el inicio del scheduler:

```powershell
.\mvnw.cmd -q "-Dtest=CorreoCodigoIntegracionTest,NotificacionesCorreoCentralTest,AislamientoCorreoAutenticacionTest,LimiteAutenticacionFilterTest,AutenticarUsuarioServiceTest" test
```

Desde `frontend-dimabug`:

```powershell
npm.cmd run build -- --output-path=dist-correo-qa
npm.cmd test -- --watch=false --browsers=ChromeHeadless --include=src/app/features/auth/login/login.component.spec.ts --include=src/app/features/usuarios/usuarios.component.spec.ts
```

La ejecución inicial dentro del sandbox bloqueó el loopback del cliente HTTP de Java. Los tests posteriores se ejecutaron con permiso ampliado para loopback local. El primer build encontró un problema de permisos en una salida existente; pasó usando la carpeta temporal nueva sin tocar angular.json ni dependencias.

## Cobertura relevante

- Administrador y otros roles; dominio institucional/personal; inexistente e inactivo con respuesta uniforme.
- Hash, expiración, reutilización, intentos y límites acumulados; rechazo de comparación en texto plano.
- Dos emisiones simultáneas producen un solo código; dos verificaciones simultáneas lo consumen una sola vez.
- Rollback elimina código y cola; no se realiza red antes del commit ni dentro de una transacción de BD.
- Cola cifrada, aceptación con referencia, limpieza de contenido, caducidad/invalidación antes del despacho.
- Fallo de configuración, autorización, conectividad previa al envío, reintentos acotados y resultado incierto sin reenvío.
- Una denegación de códigos no borra la cuenta OAuth; evidencias conserva su política anterior.
- Evidencias: MIME/destinatarios, resultados, recordatorios, errores, duplicados, edición y hardware, además de contratos de arquitectura de tickets.
- Bienvenida: transporte compartido, ejecución después del commit y no envío sin disponibilidad.
- Angular: código antes de navegar, manejo de errores y creación de usuario sin contraseña.

## Evidencias y límites

Los reportes XML/TXT individuales están en `dima-bug-backend/target/surefire-reports`. Este documento conserva únicamente conteos y alcance; no copia propiedades, credenciales, MIME ni tokens de los reportes. Logs locales: `target/qa-correo-final.log`, `target/qa-correo-cierre.log`, `frontend-dimabug/qa-correo-build.log` y `frontend-dimabug/qa-correo-test.log`.

MySQL 8, configuración efectiva de staging, correo real, proxy, cuotas, recuperación administrativa y rotación de la contraseña SMTP quedan pendientes de una validación operativa autorizada. Los bloqueos de códigos incluyen lecturas con bloqueo para evitar snapshots antiguos bajo MySQL REPEATABLE READ, pero H2 no sustituye la prueba concurrente en MySQL.

No se modificaron modos operativos, variables de entorno, bases reales ni autorizaciones OAuth. No hay commits ni despliegue. El artefacto requiere aplicar primero la tabla aditiva en una ventana autorizada; no debe promoverse sin contingencia administrativa probada.

- [Arquitectura, configuración, despliegue y reversión](../implementacion/Correo_central_y_login_codigo.md).
- [SQL aditivo preparado, no ejecutado](../04_Base_Datos/V20261110_01__correo_codigo.sql).
- [Lista exacta de archivos de esta tarea](Cambios_correo_central_2026-11-10.txt).
