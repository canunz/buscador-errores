# Consolidación y preparación de publicación en GitHub

Fecha: 2026-11-09, America/Santiago. La publicación está **PENDIENTE de autorización para git push**. No se cambió de rama ni se reescribió historial.

## Destino y revisión inicial

Rama `codex/integracion-mvp`; remoto `origin`, `https://github.com/canunz/buscador-errores.git`. Base inicial `e0d63d961b87c33d77937821c53802e6387a783e`. Tras fetch inicial no había commits locales o remotos pendientes de integrar. No se utilizó push --force, reset de historial ni eliminación de ramas.

## Commits locales

- `904c4e1`: backend, procedimientos/versionado/hardware, evidencias, Gmail/OAuth, publicación y seguridad, con sus pruebas y perfiles de configuración permitidos.
- `28ab1ab`: frontend, formularios/ejecuciones/materiales, administración Gmail, portal anónimo, permisos y enlaces NFC/QR, con pruebas.
- Documentación/seguridad: README principal y de módulos, guías canónicas, estados IMPLEMENTADO/VALIDADO/PENDIENTE, migraciones y evidencia saneada; su hash se informa al cerrar el commit y en git log.

Los cambios de código provienen del trabajo acumulado anterior; esta actividad no añadió funcionalidades de negocio. Las advertencias de conversión LF/CRLF corresponden a la política Git existente y no se cambió esa configuración.

## Contenido preparado

[Manifiesto de archivos](PUBLICACION_ARCHIVOS_2026-11-09.json), comparado con la base inicial. Incluye fuentes, pruebas, migraciones revisadas, documentación y diagramas fuente. No incluye las copias generadas de entrega académica ni artefactos binarios de build.

Documentación vigente centralizada en [índice](README.md), [README principal](../README.md), instalación/variables, arquitectura, API, datos/migraciones, seguridad, manuales y QA. Los informes históricos quedan identificados como antecedentes; no se presentan conteos antiguos ni ausencia de Gmail/NFC/snapshots como estado actual. Se anonimiza la dirección personal de prueba que figuraba en una guía.

## Seguridad y exclusiones

Revisión automática de candidatos y blobs del índice, más revisión manual de perfiles, placeholders, fixtures, guías y .gitignore. Las coincidencias iniciales eran referencias de entorno, ejemplos o valores sintéticos de test; no se detectaron valores reales en los archivos preparados. No se imprimieron secretos. El análisis no garantiza ausencia absoluta de secretos ni sustituye revisión humana de futuras incorporaciones.

[Resumen saneado de revisión](07_Pruebas/REVISION_PUBLICACION_2026-11-09.json). Herramienta reproducible: `python docs/herramientas/revisar_publicacion.py --staged`.

Reglas de exclusión de seguridad: `.env`/variantes reales, `application.properties` privado, claves/certificados privados, JSON de credenciales/tokens, respaldos, logs y uploads. Las plantillas públicas de perfiles contienen variables, no sus valores. Los únicos secretos aparentes de fixtures son sintéticos y no sirven como configuración real.

Exclusiones de artefactos: target, dist, node_modules, cachés/bytecode Python y `entregables/`/`entrega_duoc/` generados. No se borraron esos archivos locales. No había archivos ya versionados que precisaran eliminarse del índice por estas reglas.

## Validación

- Backend `mvnw.cmd -q package`: 1.118 pruebas, 0 fallos/errores/omitidas; JAR generado.
- Angular: 464 pruebas aprobadas; build correcto con advertencias de presupuestos CSS.
- [Detalle QA](07_Pruebas/Resultados_Pruebas.md) y [JSON saneado](07_Pruebas/VALIDACION_CONSOLIDACION_2026-11-09.json).
- [Revisión de migraciones y hashes](07_Pruebas/REVISION_MIGRACIONES_2026-11-09.json): no se ejecutaron SQL. El script histórico de adjuntos contiene DROP y no debe aplicarse masivamente.
- Enlaces de las 31 guías canónicas revisados sin destinos locales ausentes/ignorados. Diff de código/índice revisado sin errores de whitespace.
- No se enviaron correos reales, modificaron datos de staging, cambiaron credenciales/modo de Gmail ni reiniciaron sus servidores.

## Problemas y pendientes explícitos

No hay DDL base completo ni restauración certificada; el diagrama relacional v2 se identifica como histórico y requiere ampliación para representar todas las tablas recientes. Siguen pendientes CI remoto, UAT/pentest/carga y despliegue definitivo, revisión editorial del contenido público, protección distribuida si se despliega en varias instancias, dominio y etiquetas/escaneo NFC físicos. Recuperación de contraseña backend, reportes reales y sincronización de funciones locales no se declaran terminados. La auditoría de dependencias antigua no se presenta como saneada ni actualizada por esta actividad.

Preparar/commitear código no despliega DimaBug. El remoto solo se actualizará después de la autorización expresa de `git push origin codex/integracion-mvp`; luego debe verificarse la igualdad de HEAD local/remoto.
