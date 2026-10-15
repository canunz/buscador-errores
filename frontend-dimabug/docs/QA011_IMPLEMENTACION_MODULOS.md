# QA-011 — Implementación de mejoras de Módulos

Fecha: 2026-10-13, America/Santiago. Trabajo exclusivamente local.

## 1. Archivos modificados

Raíz: `C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores`.

Angular, bajo `frontend-dimabug/`:

- src/app/shared/layout/shell-layout.component.html: enlace administrativo Módulos.
- src/app/features/admin/admin-catalogo.component.ts: filtros locales, lista visible y Sistema inicial de edición.
- src/app/features/admin/admin-catalogo.component.html: controles de filtro, contador, ausencia de coincidencias y selector.
- src/app/features/admin/admin-catalogo.component.css: estilos de filtros limitados a Módulos, adaptación móvil/tema oscuro.
- src/app/shared/layout/sistemas-navegacion.component.spec.ts: tres pruebas añadidas sin retirar las de QA-010.

Archivos nuevos:

- frontend-dimabug/src/app/features/admin/admin-modulos.component.spec.ts
- dima-bug-backend/src/test/java/cl/casol/backend/catalogo/application/service/ModulosQa011ServiceTest.java
- dima-bug-backend/src/test/java/cl/casol/backend/catalogo/infrastructure/persistence/adapter/ModuloReferenciasQa011Test.java
- frontend-dimabug/docs/QA011_IMPLEMENTACION_MODULOS.md

No se modificaron fuentes de producción Backend, entidades, DTO, endpoints, guards, configuración de seguridad, dependencias ni SQL.

## 2. Funcionalidades implementadas

- Módulos visible solo para ADMINISTRADOR en Catálogos del menú lateral. Enlaza a `/admin/catalogo/modulos`, reutiliza AdminCatalogoComponent y adminGuard. No se crea CRUD duplicado.
- Filtro por Sistema y estado Todos/Activos/Inactivos; se combinan con AND. El filtro permite seleccionar Sistemas inactivos para consultar sus Módulos.
- Limpiar filtros restablece todos los Sistemas y todos los estados. Contador muestra resultados visibles frente al total cargado.
- Los filtros trabajan sobre las colecciones administrativas existentes; no hacen peticiones ni requieren endpoints nuevos. Se mantienen después de recargar el catálogo; se restablecen al cambiar la ruta/tipo de catálogo.
- Cuando hay registros pero ninguno coincide, se muestra un mensaje específico, sin confundirlo con un catálogo vacío.
- Al editar, el Sistema asociado se precarga. Se registra su ID original, por lo que un Sistema inactivo originalmente asociado sigue disponible después de cambiar temporalmente a otro Sistema.
- Otros Sistemas inactivos permanecen deshabilitados. Guardar valida además elegibilidad y existencia en la colección cargada para impedir enviar una selección programática inválida desde el componente.
- Un formulario nuevo reinicia la excepción del Sistema original. Nombre, máximo 120, Sistema obligatorio y validaciones del Backend permanecen.
- Filtros y estilos solo se muestran/aplican al tipo modulos. Para Hardware, Sistemas y Frecuencias, la lista visible es la misma colección de antes; selección múltiple de Hardware QA-010 intacta.

## 3. Reglas de negocio preservadas

Las fuentes Backend no se modificaron. La aplicación mantiene:

- Módulo asociado a un Sistema obligatorio en el servicio.
- Nombre normalizado mediante trim, obligatorio y máximo 120.
- Duplicados por nombre sin distinción de mayúsculas dentro del mismo Sistema, incluidos inactivos; el ID editado no colisiona consigo mismo.
- Nombres iguales entre Sistemas diferentes permitidos por la validación de aplicación.
- Alta/traslado a Sistema inactivo rechazados. Conservar el Sistema inactivo original no equivale a permitir reactivar un Módulo bajo un Sistema inactivo: esa reactivación sigue rechazada por Backend.
- Traslado bloqueado si el Módulo aparece en cualquier conocimiento, incluidos ELIMINADOS; el conteo de referencias no filtra estado.
- Desactivar conserva ID, Sistema y referencias; no se modifican conocimientos o módulos automáticamente ni se exponen eliminaciones físicas.
- Administración solo ADMINISTRADOR; TECNICO conserva lectura operacional. No se cambian permisos existentes.

No se agregó descripción ni se preparó/ejecutó migración de cl_modulo.

## 4. Pruebas ejecutadas

### Backend

Java 21 y Maven Wrapper offline:

`mvnw.cmd -o test`

**565 pruebas; 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS.**

Se añadieron 13 pruebas: 12 de servicio para alta, nombre/límites, duplicados por Sistema, edición, asociaciones activas/inactivas, traslados y desactivación; una del contrato del adaptador que confirma que el conteo de referencias incluye conocimientos eliminados al no filtrar estado.

La regresión completa incluye seguridad ADMINISTRADOR/TECNICO, QA-009 y QA-010. Los tests usan servicios/repositorios/EntityManager simulados según la capa. No ejecutan SQL ni validan físicamente restricciones o locks de MySQL.

### Angular

`npm test -- --watch=false --browsers=ChromeHeadless`

**89 pruebas aprobadas.** Se añadieron 15: tres de menú/rol/navegación efectiva y doce de filtros, contador, limpieza, precarga, reversión al Sistema inactivo original, rechazo de nuevas asociaciones inactivas, validaciones y aislamiento de otros catálogos. La suite mantiene QA-009 y QA-010.

ChromeHeadless se ejecutó con permisos ampliados para iniciar el navegador local. Persisten avisos conocidos sobre disabled en formularios reactivos; no hubo fallos de pruebas.

### Compilación Angular

`npm run build -- --configuration production`

**Exitosa. Bundle inicial: 1,35 MB; transferencia estimada: 255,91 kB.**

Advertencias existentes de presupuesto CSS: Inicio 18,62 kB y Conocimiento 22,53 kB, frente a warning 16 kB. No hubo errores de compilación.

Archivo generado:

`C:/Users/carol/OneDrive/Desktop/cata-front/buscador-errores/frontend-dimabug/dist/buscador-errores/browser/index.html`

Comprobación Git de formato de los archivos modificados: sin incidencias. No se hizo commit ni ninguna operación destructiva.

## 5. Riesgos pendientes

- Las restricciones físicas MySQL de cl_modulo (FK, nulabilidad, índices y collation) siguen sin confirmar. La aceptación entre Sistemas distintos está probada en el servicio, no contra el esquema real.
- Persistencia física y concurrencia de traslado frente a creación de conocimientos necesitan un entorno aislado autorizado. No se cambió el protocolo de bloqueo.
- Un Sistema inactivo puede conservar Módulos activos; su disponibilidad operacional depende también del padre. La regla existente de reactivación debe explicarse al usuario mediante el error Backend.
- El aviso preventivo de impacto sobre conocimientos no forma parte de esta implementación. No se añadieron bloqueos ni cambios de negocio.
- Pendiente UAT visual humano en móvil, teclado y zoom. Se añadió CSS adaptable y se probaron DOM/navegación, sin afirmar una validación visual de dispositivos reales.
- El despliegue consolidado todavía requiere aplicar de forma autorizada la migración pendiente de QA-009 y coordinar sus contratos de título con Angular; no se ejecutó aquí.

## 6. Estado QA-009 y QA-010

Preservados. Se compararon antes/después huellas SHA256 agregadas de todas las fuentes Java y SQL de producción Backend y huellas individuales del servicio Conocimiento Angular, componente Soluciones y HTML/CSS de Hardware. Todas coinciden. Los cambios de administración compartida conservaron la selección múltiple e inactivos originales de QA-010; las suites completas anteriores siguen pasando.

La revisión QA-011 se limita a Módulos. No se modificaron STAGING/PRODUCCIÓN, no se ejecutaron migraciones, no se hicieron commits/reset/stash/clean y no se avanzó a Departamentos, Pruebas o Procedimientos.

**QA-011 completado localmente. Esperar autorización para el siguiente módulo.**
