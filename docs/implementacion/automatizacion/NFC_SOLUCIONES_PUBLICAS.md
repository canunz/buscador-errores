# Enlaces individuales de soluciones para NFC y QR

Fecha: 2026-11-09, America/Santiago.

## Alcance confirmado

Implementación digital y pruebas de seguridad. El usuario confirmó que no se requiere todavía dominio/IP definitivo ni generación, lectura o modificación de etiquetas físicas. La programación y validación en un teléfono real se realizarán cuando DimaBug esté desplegado.

## Diagnóstico previo

No se localizó código que programe NDEF, genere destinos NFC o registre etiquetas físicas. La normalización Unicode Form.NFC no es una integración con etiquetas; las entidades llamadas Etiqueta pertenecen al módulo de tickets. Por eso no se acreditan etiquetas físicas existentes ni se modifican supuestos destinos.

El portal ya cuenta con detalle por solución y API `GET /api/publico/soluciones/{id}`. Un conocimiento puede tener múltiples soluciones. Cada solución tiene su propio ID estable, flags activo/autoservicio y pertenece a un conocimiento; los materiales públicos tienen autorización individual. La API verifica publicación tanto en detalle como en lectura/descarga de materiales.

## Implementación

- Ruta canónica individual: `/autoservicio/soluciones/:id`.
- Se reutiliza el mismo componente público, servicio y API existentes; no se duplica un endpoint bajo `/api/public`.
- La ruta anterior `/autoservicio/:id` sigue funcionando para el mismo ID. El listado público utiliza la ruta canónica nueva.
- Un ID inválido o un recurso no disponible presenta una página pública «Recurso no disponible», sin cargar otra solución ni revelar información interna.
- Instrucciones, título y materiales se conservan en el lector público. La descripción existente de la solución se presenta como instrucciones; no se inventa un campo paralelo.
- No hay controles administrativos en el portal público, con o sin sesión.

En `/soluciones`, abrir el detalle de una solución activa con autoservicio permite al administrador ver la sección **Enlace NFC y QR**. Esta consulta también comprueba el detalle público en backend, incluyendo que el conocimiento padre esté publicado. Si no es público no ofrece un enlace para copiar.

**Copiar enlace** usa el ID de la solución seleccionada, nunca el ID del conocimiento. La misma URL puede codificarse posteriormente en una etiqueta NFC o QR. No se programa hardware ni se genera una etiqueta física.

## Base configurable sin dominio obligatorio

`publicPortalUrl` se incorpora a `src/environments/environment.ts` y `environment.prod.ts`, vacío por defecto. Ejemplo futuro:

```
publicPortalUrl: 'https://soporte.empresa.cl'
```

Resultado para solución 8: `https://soporte.empresa.cl/autoservicio/soluciones/8`.

También puede configurarse la dirección desde el desplegable de la sección NFC en administración; se guarda solo la base pública en este navegador (`dimabug.portal.publicBase`), sin credenciales ni tokens. La configuración de despliegue tiene prioridad sobre la preferencia del navegador. Se permite una IP de red cuando sea apropiada para el futuro despliegue.

Sin una base válida, **Copiar enlace** copia `/autoservicio/soluciones/8` y lo identifica como ruta de prueba. No se genera una URL localhost para uso físico ni se exige configurar un dominio ahora. La interfaz advierte que la ruta relativa requiere una base definitiva antes de programar etiquetas. El portapapeles usa CDK Clipboard y comunica fallos con una alternativa de copia manual.

La base rechaza esquemas distintos de HTTP/HTTPS, credenciales, query/hash, localhost y loopback. No utiliza la URL del backend 9090 para generar destinos públicos del frontend.

## Compatibilidad

- Enlaces anteriores del portal `/autoservicio/{id}` conservados.
- Los enlaces internos `/conocimiento/{id}` siguen protegidos y no se sustituyen por una solución elegida arbitrariamente. No se alteró la navegación administrativa para intentar identificar un escaneo.
- No se asume correspondencia uno a uno entre conocimiento y solución. Si en el futuro se aportan etiquetas antiguas con destino a conocimiento, se debe revisar la URL grabada y resolver explícitamente la correspondencia; con múltiples soluciones corresponde un selector, no redirección automática a la primera.
- La permanencia se basa en el ID de solución conservado al editar/desactivar. Si se retira publicación, esa misma URL muestra no disponible; no se redirige silenciosamente a una solución diferente.

## Archivos modificados en esta tarea

Frontend:
- `frontend-dimabug/src/app/app.routes.ts`: ruta canónica manteniendo la anterior.
- `frontend-dimabug/src/app/features/autoservicio/portal-soluciones.component.ts` y `.html`: navegación canónica y manejo público de recurso no disponible/ID inválido.
- `frontend-dimabug/src/app/features/soluciones/enlace-nfc.component.ts`: generación/copia y base configurable; lector administrativo de publicación.
- `frontend-dimabug/src/app/features/soluciones/soluciones.component.ts` y `.html`: integración en el detalle administrativo.
- `frontend-dimabug/src/environments/environment.ts` y `environment.prod.ts`: base opcional del despliegue.
- `frontend-dimabug/src/app/features/soluciones/enlace-nfc.component.spec.ts`: pruebas de URL, publicación y copia.
- `frontend-dimabug/src/app/features/autoservicio/portal-publico.component.spec.ts`: enlaces directos con/sin sesión, compatibilidad e interfaz móvil.

Backend: solo se amplió `dima-bug-backend/src/test/java/cl/casol/backend/AutoservicioPublicoHttpTest.java` para comprobar que el detalle individual es idéntico con y sin sesión y no contiene datos administrativos. No cambió código productivo backend ni endpoints, JWT o configuración Gmail. No hay migraciones.

## QA

- Backend: 21 pruebas seleccionadas aprobadas (9 HTTP públicas, 4 límites de abuso y 8 seguridad del chatbot), sobre H2 aislado. Incluyen solución publicada, privada, inactiva, inexistente, padre borrador, materiales privados/autorizados, retirada de publicación y protección administrativa.
- Angular: 464 pruebas aprobadas; build correcto con advertencias de presupuesto CSS previas. `git diff --check` sin errores.
- Nuevas pruebas: enlace canónico sin sesión/con sesión, ID estable por solución, alias anterior, ID malformado, copia con/sin base, localhost/loopback/credenciales rechazados, dirección de red, errores de portapapeles y no generación para soluciones privadas. Se comprueban listado y detalle a 360 y 1280 px.
- No se enviaron correos ni se modificaron soluciones, materiales, procedimientos, ejecuciones o etiquetas en staging.

## Pendiente para el despliegue físico

Configurar la base definitiva accesible desde teléfonos, comprobar conectividad y permisos de materiales externos, programar las etiquetas y realizar un escaneo real. Esto queda expresamente fuera del alcance actual por indicación del usuario. La prueba responsive no se presenta como un escaneo NFC de hardware real.
