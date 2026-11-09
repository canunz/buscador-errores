# Arquitectura general

Angular consume una API Spring Boot en un monolito modular. Los módulos principales son identidad, catálogos, conocimiento, procedimientos, ejecución, seguimiento, tickets y correo. MySQL persiste datos; un filesystem privado conserva archivos. Los tests de integración utilizan H2 efímero y transportes/mocks de ensayo.

La aplicación separa tres contextos: portal anónimo de consultas, funcionalidades privadas autenticadas con JWT y autorización OAuth de una cuenta Gmail para transporte de correo. OAuth no reemplaza el login ni crea una sesión de usuario DimaBug.

Los contratos de pasos se capturan al iniciar una ejecución. Las revisiones administrativas generan nuevas definiciones y copian materiales; la ejecución mantiene sus IDs de procedimiento/paso originales. Gmail y filesystem son recursos externos a la transacción SQL; se utiliza compensación de archivos y reserva/registro de comunicaciones, con estados de envío incierto que no provocan reintentos ciegos.

No se acredita arquitectura distribuida, despliegue de producción ni outbox universal para todos los módulos.

[Estado global y evidencia](../README.md).
