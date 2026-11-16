# Instalación y ejecución local

## Requisitos

Java 21, Maven Wrapper del repositorio, Node/npm compatibles con Angular 19 y su lockfile, MySQL con esquema autorizado, y directorios privados de adjuntos. El frontend de desarrollo usa 4200 y el backend 9090.

## Preparar MySQL y configuración

El repositorio no tiene DDL inicial completo ni runner automático de migraciones. Obtener un esquema saneado/aprobado y revisar [migraciones](../04_Base_Datos/Migraciones.md) con el DBA. No importar un respaldo con datos sensibles ni usar Hibernate create/update sobre una base existente.

Configurar externamente el perfil staging y DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET y JWT_EXPIRATION. No se requieren secretos en archivos versionados. Mantener SPRING_JPA_HIBERNATE_DDL_AUTO=none y SPRING_SQL_INIT_MODE=never. El host/esquema y las credenciales deben ser los autorizados para ese entorno; los ejemplos no los sustituyen.

## Arranque

Desde `dima-bug-backend`, con las variables ya definidas: `mvnw.cmd spring-boot:run`. Otra opción después de `mvnw.cmd package`: `java -jar target/backend-0.0.1-SNAPSHOT.jar` con el mismo entorno. Verificar `/api/health` sin imprimir la configuración privada.

Desde `frontend-dimabug`: `npm ci` y `npm start`. El proxy reenvía `/api` a 9090. Para producción, configurar el origen de API y el servidor para devolver la SPA en rutas profundas; no se utiliza proxy de ng serve.

Rutas públicas: `/autoservicio`, `/chatbot` y `/autoservicio/soluciones/:id`. Rutas administrativas requieren usuario/rol existentes. No se crea una cuenta administradora o contraseña por defecto desde esta guía.

## Correo y pruebas

SIMULADO es el default de repositorio. El perfil gmail añade OAuth sin sustituir JWT; autorizar una cuenta no activa envíos. Seguir la guía Gmail para aprobación/configuración, sin activar real para probar el código.

Para QA usar `mvnw.cmd package`, Chrome Headless y build Angular con credenciales reales aisladas. Las suites usan H2/mocks; no ejecutar scripts de reparación o pruebas manuales que envíen correo como parte del build.

## Pendiente

Instalación desde cero con DDL base y restauración verificables, configuración final de hosting/HTTPS, dominio NFC y pruebas de operación/producción. Los arranques locales previos no certifican ese despliegue.

[Índice vigente](../README.md).
