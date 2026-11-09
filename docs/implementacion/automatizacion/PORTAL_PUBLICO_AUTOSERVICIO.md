# Portal público de autoservicio DimaBug

Fecha: 2026-11-08.

## Diagnóstico inicial

`/chatbot` estaba dentro del ShellLayout protegido por authGuard. Su POST `/api/chatbot/consultas` requería rol autenticado. Ya existía una búsqueda determinista SQL que filtra soluciones activas y con autoservicio, de conocimientos publicados; no utiliza un LLM ni envía consultas a un proveedor de IA externo.

Las soluciones almacenan `solucion_estado` y `solucion_autoservicio` en `co_solucion`. Los materiales existentes de `co_material_apoyo` pertenecen a conocimientos o pasos, no a soluciones; reutilizar indiscriminadamente esos adjuntos habría expuesto archivos internos. Se añadió una asociación independiente por solución con permiso explícito de publicación. No se migraron ni publicaron recursos existentes.

## Arquitectura pública y privada

El portal Angular tiene su propio layout con marca, buscador, enlace al asistente e inicio de sesión. No carga navegación administrativa. Se añade un acceso desde login.

Rutas públicas:
- `/autoservicio`: buscador y listado, hasta 50 resultados por consulta.
- `/autoservicio/:id`: instrucciones y materiales expresamente autorizados.
- `/chatbot`: componente de conversación existente, ahora con API pública.

API pública, sin JWT:
- GET `/api/publico/soluciones?q=...`
- GET `/api/publico/soluciones/{id}`
- GET `/api/publico/soluciones/{id}/materiales`
- GET `/api/publico/soluciones/{id}/materiales/{materialId}/archivo`
- POST `/api/publico/chatbot/consultas`, cuerpo `{ "consulta": "..." }`

Solo se permiten esos métodos/rutas públicos; las demás operaciones bajo `/api/publico/**` se deniegan. Las rutas privadas y `/api/admin/**` conservan JWT y roles. El interceptor no adjunta tokens a peticiones públicas y retira Authorization explícito de ellas. El antiguo `/api/chatbot/consultas` sigue protegido para compatibilidad; Angular utiliza la API pública.

Una solución pública debe estar activa, con autoservicio habilitado y conocimiento padre PUBLICADO. Se evalúa también al consultar por ID, listar materiales y descargar cada archivo. Una solución interna, inactiva, de un borrador o inexistente recibe 404 equivalente. Los DTO no incluyen autores, correos, asignaciones, comentarios internos ni la entidad de conocimiento. El chatbot reutiliza su buscador y contenido autorizado; reemplaza el nombre del conocimiento por «Autoservicio» para no publicar títulos internos del padre.

## Materiales y administración

En `/soluciones`, abrir el detalle como administrador permite agregar múltiples materiales mediante el formulario compartido `MaterialApoyoFormComponent`:
- Imágenes y PDF: carga local con validación de contenido, tipo y máximo 10 MB.
- Videos y enlaces externos: URL HTTP/HTTPS validada, sin credenciales, rutas locales ni direcciones internas evidentes.
- Cada material nuevo es privado salvo autorización explícita. El formulario incluye una casilla de autorización de publicación y el listado permite autorizar/retirar esa autorización.
- Que un material esté autorizado no basta si su solución o conocimiento no son públicos.
- No se reutilizan referencias físicas de archivos privados aportadas por el cliente. Los archivos se almacenan mediante `ArchivoMaterialService`, con compensación de rollback, descarga segura y `nosniff`/`attachment`.
- Los enlaces externos usan `noopener noreferrer`. Las instrucciones y títulos se interpolan como texto; no hay HTML inyectado ni iframes.

API administrativa de materiales: GET/POST `/api/admin/soluciones/{id}/materiales`, POST `/archivo` multipart, PATCH `/{materialId}/publicacion`, DELETE `/{materialId}` y GET `/{materialId}/archivo`.

El interruptor de AUTOSERVICIO es un botón nativo con role=switch, aria-checked, etiqueta accesible, estado verde/gris, texto Activado/Desactivado, indicador Actualizando y bloqueo mientras espera. No cambia visualmente antes de confirmar el backend, por lo que un rechazo conserva el estado anterior. Se impide habilitar autoservicio en soluciones inactivas tanto en frontend como backend; se permite retirar una clasificación anterior. Las reglas anteriores de estado, incluida la protección de última solución activa, se mantienen.

## Controles contra abuso

`LimiteAutoservicioFilter`: 10 consultas de chatbot/minuto por dirección, 60 lecturas/minuto por dirección, 600 peticiones públicas/minuto por instancia y mapa acotado. Usa la ruta servlet decodificada y la dirección remota, no X-Forwarded-For arbitrario. Devuelve 429 y Retry-After. Los cuerpos POST públicos están limitados a 8 KiB, también sin Content-Length; la consulta textual del chatbot admite 300 caracteres y el buscador 150. Las lecturas públicas usan Cache-Control:no-store para no conservar respuestas después de retirar publicación.

## Migración

`11_materiales_soluciones_autoservicio.sql`, aplicada por el usuario en staging y verificada por lectura: nueva tabla `co_material_solucion`, FK a solución, flag publicado=false por defecto. No cambia registros previos. La cuenta de aplicación no ejecutó DDL.

Verificación de staging antes/después: 0 materiales nuevos, 0 autorizados; 6 soluciones ya cumplían los criterios. No se habilitaron flags, copiaron adjuntos ni cambiaron procedimientos o ejecuciones. Gmail, credenciales OAuth y JWT permanecen intactos; los reinicios locales conservaron argumentos/entorno y se comprobaron colas sin correos pendientes o seguimientos vencidos.

## Archivos implementados

Backend nuevos:
- `conocimiento/domain/MaterialSolucion.java`
- `conocimiento/application/port/out/MaterialSolucionRepository.java`
- `conocimiento/application/service/AutoservicioPublicoService.java`
- `conocimiento/infrastructure/persistence/entity/MaterialSolucionEntity.java`
- `conocimiento/infrastructure/persistence/repository/MaterialSolucionJpaRepository.java`
- `conocimiento/infrastructure/persistence/adapter/MaterialSolucionRepositoryAdapter.java`
- `conocimiento/infrastructure/web/AutoservicioPublicoController.java`
- `conocimiento/infrastructure/web/AdminMaterialSolucionController.java`
- `conocimiento/infrastructure/web/dto/MaterialSolucionResponse.java`
- `shared/infrastructure/security/LimiteAutoservicioFilter.java`

Backend modificados: `SolucionRepository`, `SolucionJpaRepository`, `SolucionRepositoryAdapter` (búsqueda pública acotada), `AdministrarSolucionService` (regla de inactivas), `SecurityConfig` (allowlist explícita). Rutas relativas a `dima-bug-backend/src/main/java/cl/casol/backend/`.

Angular nuevos:
- `core/services/autoservicio.service.ts`
- `features/autoservicio/portal-layout.component.ts`
- `features/autoservicio/portal-soluciones.component.ts`, `.html`, `.css`
- `features/soluciones/materiales-solucion.component.ts`

Angular modificados: `app.routes.ts`, `core/config/api.ts`, `core/interceptors/auth.interceptor.ts`, `core/services/chatbot.service.ts`, `features/soluciones/soluciones.component.ts`, `.html`, `.css`, `features/auth/login/login.component.html`. Rutas relativas a `frontend-dimabug/src/app/`.

Pruebas nuevas: `AutoservicioPublicoHttpTest.java`, `LimiteAutoservicioFilterTest.java`, `features/autoservicio/portal-publico.component.spec.ts`. Pruebas actualizadas: `AdministrarSolucionServiceTest.java`, `auth.interceptor.spec.ts`, `chatbot.service.spec.ts`, `soluciones.component.spec.ts`. Más la migración y este informe.

## Reutilización y QA

Se reutilizaron el chatbot, su buscador SQL seguro y contrato de conversación; el flag de autoservicio y su endpoint administrativo; el formulario compartido de materiales; los validadores de tipos/contenido, FormData, almacenamiento y descarga existentes; botones/colores de DimaBug. No se incorporaron bibliotecas.

Resultado final: 1.117 pruebas backend y 451 Angular aprobadas, sin fallos ni errores. Build Angular correcto con advertencias CSS previas; `git diff --check` sin errores. Pruebas backend sobre H2 aislado y frontend con mocks; no hubo envíos reales ni publicación de contenido sintético en staging.

Cobertura: listado y búsqueda anónimos, detalle, publicación y descarga de archivo, retirada de permisos inmediata, padre/adjunto privado, solución inactiva y borrador, acceso por ID, mutaciones sin JWT, rol técnico sin privilegio de administración, chatbot sin fuentes privadas, URLs peligrosas, inputs largos, payload sin Content-Length, límites de consultas, navegación Angular sin sesión, conservación de guards privados, switch con error, teclado nativo/etiquetas y layouts a 360/1280 px.

Comprobación HTTP local sin token: listado 200 con 6 soluciones elegibles; detalle 200; materiales 200; chatbot POST 200; ID inexistente 404; administración 401; archivo privado anterior 401; PUT público 401. `/chatbot` y `/autoservicio` en 4200 responden 200. No se imprimió el contenido de las soluciones en las verificaciones.

## Riesgos y límites operativos

- La autorización de publicación es una decisión editorial del administrador: revisar instrucciones y archivos antes de autorizar su exposición. No se puede detectar automáticamente toda información interna o personal.
- El límite de tráfico es local a la instancia y a la dirección remota. NAT/proxy puede agrupar usuarios; para varias instancias o exposición a Internet se requiere control central de tráfico y configuración de proxy de confianza. No se abrió el equipo a Internet en esta tarea.
- Autorizar un enlace no cambia permisos de Google Drive u otros proveedores. Sus recursos deben contar con permisos de acceso apropiados, sin hacer públicos archivos privados de forma automática.
- No se añadió una IA generativa externa: el asistente sigue siendo búsqueda de contenido aprobado.

La compilación final quedó activa en 9090. Smoke final sin token: health/listado/chatbot 200, payload público excesivo 413, administración 401.

No quedan migraciones pendientes. La administración de materiales reales y su revisión editorial quedan a cargo del administrador; no se creó contenido de prueba en staging.
