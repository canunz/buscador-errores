# Frontend DimaBug

SPA Angular 19 standalone, TypeScript, RxJS y estilos propios. Consume Spring Boot; el acceso público no carga el shell administrativo.

- [Documentación vigente](../docs/README.md) y [arquitectura](../docs/03_Arquitectura/Arquitectura_Frontend.md).
- Instalación reproducible de dependencias: `npm ci`; desarrollo: `npm start` (4200, proxy API 9090).
- QA: `npm test -- --watch=false --browsers=ChromeHeadless`; build: `npm run build`.
- [Resultados y límites](../docs/07_Pruebas/Resultados_Pruebas.md).

Rutas públicas: `/autoservicio`, `/autoservicio/soluciones/:id`, alias `/autoservicio/:id` y `/chatbot`. Las rutas privadas conservan sus guards y permisos backend.

La base NFC/QR se configura mediante `publicPortalUrl` en los environments, o como preferencia administrativa del navegador. Sin base accesible se copia una ruta de prueba; no se exige un dominio definitivo ni se programan etiquetas físicas. No usar localhost para etiquetas físicas.

Producción utiliza mismo origen de API por defecto y no el proxy de ng serve. No introducir credenciales o tokens OAuth en los environments. Las guías QA antiguas en `docs/` son antecedentes fechados, no el estado vigente.
