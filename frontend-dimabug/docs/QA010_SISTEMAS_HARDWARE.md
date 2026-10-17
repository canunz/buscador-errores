# QA-010 — Sistemas y asociaciones Hardware–Sistema

Fecha: 2026-10-13, America/Santiago. Implementación exclusivamente local.

## Comportamiento final

- Sistemas aparece en Catálogos del menú lateral solo para ADMINISTRADOR, enlazando a `/admin/catalogo/sistemas`. Reutiliza AdminCatalogoComponent y adminGuard, sin nuevos CRUD ni cambios de permisos.
- Administrar Hardware se muestra como acción separada en la cabecera, con explicación de creación/edición/asociaciones, foco visible y adaptación a pantallas pequeñas.
- El formulario de Hardware tiene una sección Sistemas asociados identificada mediante leyenda, instrucciones, contador y checkboxes de selección múltiple.
- Al editar, se precargan los IDs asociados. Un Sistema inactivo previamente asociado puede desmarcarse y volver a marcarse antes de guardar. La elegibilidad utiliza la selección inicial del formulario, no la selección mutable.
- Un Sistema inactivo no asociado inicialmente permanece deshabilitado; tampoco se acepta mediante llamada directa al manejador Angular. Abrir un formulario nuevo reinicia la selección inicial.
- PUT de Hardware con sistemaIds omitido o null conserva los vínculos existentes; [] expresa desvinculación de todos; una lista con valores reemplaza el conjunto. POST omitido comienza sin asociaciones.
- Permanecen las validaciones Backend de IDs inexistentes, duplicados e inactivos nuevos. Las asociaciones inactivas existentes pueden conservarse.
- No se añadieron bloqueos de desvinculación ni se eliminaron/modificaron conocimientos o módulos. No hay cambios de esquema ni endpoints nuevos.

## Archivos modificados

Rutas relativas a `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores`.

Backend:

- dima-bug-backend/src/main/java/cl/casol/backend/catalogo/application/service/AdministrarCatalogosService.java
- dima-bug-backend/src/test/java/cl/casol/backend/catalogo/application/service/AdministrarCatalogosServiceTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/AdminCatalogoSecurityIntegrationTest.java

Angular:

- frontend-dimabug/src/app/shared/layout/shell-layout.component.html
- frontend-dimabug/src/app/features/hardware/hardware.component.html
- frontend-dimabug/src/app/features/hardware/hardware.component.css
- frontend-dimabug/src/app/features/admin/admin-catalogo.component.ts
- frontend-dimabug/src/app/features/admin/admin-catalogo.component.html
- frontend-dimabug/src/app/features/admin/admin-catalogo.component.css
- frontend-dimabug/src/app/features/admin/admin-catalogo.component.spec.ts

Pruebas Angular nuevas:

- frontend-dimabug/src/app/shared/layout/sistemas-navegacion.component.spec.ts
- frontend-dimabug/src/app/features/hardware/hardware.component.spec.ts

Informe nuevo:

- frontend-dimabug/docs/QA010_SISTEMAS_HARDWARE.md

## Validación

Backend, Java 21 y Maven Wrapper offline:

`mvnw.cmd -o verify`

**552 pruebas, 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS.** Se añaden siete casos de servicio para lista omitida/vacía/con valores y validaciones, más tres casos MVC para la deserialización y transmisión del contrato PUT. La suite completa incluye la regresión de QA-009 y seguridad existente.

Angular:

`npm test -- --watch=false --browsers=ChromeHeadless`

**74 pruebas aprobadas.** Se añaden doce casos: seis de selección DOM/múltiple/precarga/inactivos/lista vacía; cuatro de menú, permisos y navegación efectiva a la administración existente; dos del acceso a Administrar Hardware. La suite completa incluye QA-009.

`npm run build -- --configuration production`

**Compilación exitosa, bundle inicial 1,34 MB.** Permanecen las advertencias de presupuesto CSS conocidas: Conocimiento 22,53 kB e Inicio 18,62 kB, con warning 16 kB. Las pruebas también muestran avisos existentes sobre disabled en formularios reactivos; no hubo fallos de aplicación en la suite.

Los tests Backend usan repositorios/servicios simulados según la capa: no verifican persistencia física ni concurrencia real en MySQL. No se ejecutó UAT en STAGING ni revisión visual humana en dispositivos. El CSS incorpora adaptación móvil y las pruebas verifican controles DOM/navegación.

Se compararon SHA256 antes/después de siete archivos clave de QA-009 (dominio, servicios Backend, migración y servicio/componentes Angular); todas las huellas coinciden. Los cambios preexistentes de catálogos y Hardware se conservaron y ampliaron.

Artefactos generados:

- Backend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/dima-bug-backend/target/backend-0.0.1-SNAPSHOT.jar`
- SHA256: `6cedfe29f08c6b596c2982beff918d45cceeab6968a40c6edb7f3c33507d6816`
- Frontend: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/dist/buscador-errores/browser/index.html`

## Detección de vínculos utilizados: propuesta, NO implementada

Calcular los IDs retirados como asociaciones persistidas menos asociaciones solicitadas. No hacerlo sobre una lista parcial ni considerar un campo omitido como desvinculación.

El Backend puede consultar co_conocimiento por la pareja hardware_id/sistema_id, agrupando por estado. Se deben distinguir PUBLICADO, BORRADOR y ELIMINADO para preservar la información histórica. La consulta sería de solo lectura, usando parámetros:

```sql
SELECT sistema_id, conocimiento_estado, COUNT(*) AS cantidad
FROM co_conocimiento
WHERE hardware_id = ? AND sistema_id IN (/* parámetros de los IDs retirados */)
GROUP BY sistema_id, conocimiento_estado;
```

No se ejecutó esta consulta. Se propone exponer el impacto mediante un caso de uso de lectura y su puerto/adaptador, utilizando el controlador administrativo existente y autorización ADMINISTRADOR. Si hiciera falta una ruta de previsualización, sería adicional de lectura, no otro CRUD. Su diseño definitivo e implementación necesitan aprobación.

Antes de guardar una desvinculación con uso detectado, Angular podría mostrar:

"Estas asociaciones se utilizan en N conocimientos (X publicados, Y borradores y Z históricos). Al guardar se retirarán los vínculos del catálogo; los Sistemas, Módulos y Conocimientos se conservarán."

Ofrecer Cancelar y Confirmar desvinculación. No bloquear automáticamente. La previsualización es informativa y puede cambiar entre consulta y guardado; no equivale a una restricción transaccional.

## Riesgos y pendientes

- Las referencias individuales de conocimientos se conservan, aunque la pareja Hardware–Sistema deje de estar asociada. Mantener esta política histórica hasta una nueva decisión de negocio.
- Desactivar un Sistema conserva vínculos y Módulos; no implica desactivar Módulos automáticamente. Las consultas operacionales dejan de ofrecer el Sistema inactivo.
- Un guardado con lista completa puede sobrescribir otra edición concurrente de asociaciones. El tratamiento de concurrencia/conflictos no se amplió en este alcance.
- Las cachés de otra sesión pueden seguir temporalmente desactualizadas; la invalidación actual opera en la instancia que guarda.
- Verificar FK, unicidad de la pareja y persistencia real sobre un entorno aislado autorizado; no se propone migración sin evidencia del esquema.
- Antes de desplegar el conjunto local debe respetarse la migración pendiente y el despliegue coordinado de QA-009. QA-010 no ejecuta ese SQL.
- Pendientes UAT visual móvil/teclado y aprobación específica de la advertencia de impacto.

No se realizaron commits, resets, operaciones destructivas de Git, migraciones ni cambios en STAGING/PRODUCCIÓN. No se avanzó a otro módulo.
