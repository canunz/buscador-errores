# Backend DimaBug

Spring Boot 4.1.1 / Java 21; servicios modulares, puertos/adaptadores, JPA/JDBC y MySQL. H2 solo para pruebas. JWT privado y autorización Gmail independiente.

- [Estado y documentación](../docs/README.md)
- [Arquitectura backend](../docs/03_Arquitectura/Arquitectura_Backend.md)
- [Instalación](../docs/08_Despliegue/Instalacion_Local.md) y [variables](../docs/08_Despliegue/Variables_Entorno.md)
- [Migraciones manuales](../docs/04_Base_Datos/Migraciones.md)
- [API](../docs/05_API/Endpoints.md) y [seguridad](../docs/06_Seguridad/Arquitectura_Seguridad.md)
- [QA](../docs/07_Pruebas/Resultados_Pruebas.md)

Con entorno y esquema autorizados: `mvnw.cmd spring-boot:run`. Para validar y empaquetar: `mvnw.cmd package`. No se versiona `application.properties` privado. Los perfiles compartidos contienen placeholders, no credenciales. No habilitar correo real durante pruebas ni utilizar DDL automático contra una base existente.
