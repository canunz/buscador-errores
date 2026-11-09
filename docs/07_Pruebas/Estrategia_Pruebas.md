# Estrategia de pruebas

- Unitarias para dominio, servicios y validación; mocks de integraciones externas.
- HTTP/Security para JWT, roles, publicación pública, pertenencia y errores.
- JPA/servicios sobre H2 efímero para transacciones, versionado, fotografías y auditoría. No se utilizan credenciales de staging para QA.
- Angular con Chrome Headless para servicios, formularios, guards/router, errores, estados de carga, prevención de doble clic y layouts a 360/1280 px.
- Backend `mvnw.cmd package`: compilar, ejecutar suites y empaquetar. Frontend `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build`.

No se envían correos reales durante pruebas automatizadas. Los transportes de ensayo y mocks validan errores/aceptación/idempotencia sin acreditar recepción en bandeja. La confirmación funcional de correo real aportada por el usuario se distingue del resultado automatizado.

La consolidación aísla variables reales y no ejecuta scripts SQL, herramientas de reparación ni reinicios contra staging. Los tests mantienen sus propias configuraciones H2, incluyendo los tests específicos de variables de entorno.

Límites: H2 no certifica equivalencia MySQL; no hay cobertura porcentual acreditada, escaneo NFC físico, UAT/pentest/carga de producción o restauración certificados. Se publican resúmenes saneados; logs/XML crudos con datos sintéticos de autenticación se conservan fuera de Git.

[Índice vigente](../README.md).
