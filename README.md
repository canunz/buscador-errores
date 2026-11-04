DimaBug

Plataforma web para la gestión del conocimiento, procedimientos y tickets del área de Soporte TI de Holding Dimarsa. Permite registrar errores con su solución, documentar procedimientos paso a paso y gestionar tickets de soporte con prioridades y avisos por correo.

Proyecto desarrollado para la asignatura Proceso de Portafolio de Título (APT), Escuela de Informática y Telecomunicaciones, Duoc UC.

Repositorio: https://github.com/canunz/buscador-errores.git

Equipo
Integrante	Rol
Fanny Araujo	Jefa de proyecto
Carolina Cheuquepil	Desarrollo backend
Catalina Núñez	Desarrollo frontend
Estructura del repositorio
buscador-errores/
├── frontend-dimabug/      Aplicación web en Angular
└── dima-bug-backend/      API REST en Spring Boot
Tecnologías
Capa	Tecnología
Frontend	Angular 19, TypeScript, Chart.js
Backend	Java 21, Spring Boot 4, Spring Security, Spring Data JPA
Autenticación	Código de acceso enviado al correo y token JWT
Base de datos	MySQL
Correo	JavaMailSender (SMTP)
Arquitectura	Hexagonal (puertos y adaptadores)
Módulos principales
Base de conocimiento: registro y búsqueda de errores con su solución.
Procedimientos: guías paso a paso con material adjunto.
Tickets: creación, seguimiento y cambio de prioridad. Cuando un ticket pasa a prioridad crítica se envía un aviso por correo a Soporte y Administrador.
Usuarios: administración de cuentas y roles, con correo de bienvenida al crear la cuenta.
Inicio de sesión: el usuario ingresa su correo y recibe un código de 6 dígitos para entrar.
Acceso a la base de datos

La base de datos MySQL del proyecto no está expuesta a internet. El equipo se conecta a ella a través de un túnel, por lo que el sistema no puede ejecutarse completo desde otro equipo solo descargando el repositorio.

Para revisar el funcionamiento se adjuntan:

Capturas y video de la demostración del sistema.
Los scripts de la base de datos en la carpeta Base de datos de las evidencias y en dima-bug-backend/src/main/resources/db/migration.

Si se quiere ejecutar en un equipo propio, se debe crear una base de datos MySQL local con esos scripts y apuntar la configuración del backend a ella.

Ejecución en el entorno del equipo
Requisitos
Java 21
Node.js 20 o superior y Angular CLI 19
Túnel hacia la base de datos activo (o una base MySQL local)
1. Configurar el backend

En dima-bug-backend/src/main/resources copiar application-example.properties como application.properties y completar los valores: conexión a la base de datos, jwt.secret (mínimo 32 caracteres), datos del correo SMTP y dimabug.app.url.

El archivo application.properties no se sube al repositorio porque contiene credenciales.

2. Levantar el backend
cd dima-bug-backend
mvnw spring-boot:run

La API queda disponible en http://localhost:9090.

3. Levantar el frontend
cd frontend-dimabug
npm install
npm start

La aplicación queda disponible en http://localhost:4200. Las llamadas a /api se redirigen al backend mediante proxy.conf.json.
