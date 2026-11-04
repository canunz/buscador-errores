# DimaBug

Plataforma web para la gestión del conocimiento, procedimientos y tickets del área de Soporte TI de Holding Dimarsa.

Proyecto desarrollado para la asignatura Proceso de Portafolio de Título (APT), Escuela de Informática y Telecomunicaciones, Duoc UC.

---

## Equipo

| Integrante | Rol |
|---|---|
| Fanny Araujo | Jefa de proyecto |
| Carolina Cheuquepil | Desarrollo backend |
| Catalina Núñez | Desarrollo frontend |

---

## Estructura del repositorio

```
buscador-errores/
├── frontend-dimabug/      Aplicación web en Angular
└── dima-bug-backend/      API REST en Spring Boot
```

---

## Tecnologías

| Capa | Tecnología |
|---|---|
| Frontend | Angular 19, TypeScript, Chart.js |
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA |
| Autenticación | Código de acceso por correo y token JWT |
| Base de datos | MySQL |
| Correo | JavaMailSender (SMTP) |
| Arquitectura | Hexagonal (puertos y adaptadores) |

---

## Módulos principales

- **Base de conocimiento:** registro y búsqueda de errores con su solución.
- **Procedimientos:** guías paso a paso con material adjunto.
- **Tickets:** creación, seguimiento y cambio de prioridad. Si un ticket pasa a crítico, se avisa por correo a Soporte y Administrador.
- **Usuarios:** administración de cuentas y roles, con correo de bienvenida.
- **Inicio de sesión:** el usuario ingresa su correo y recibe un código de 6 dígitos.

---

## Acceso a la base de datos

La base de datos MySQL no está expuesta a internet. El equipo se conecta a ella mediante un túnel, por lo que el sistema no puede ejecutarse completo solo descargando el repositorio.

Para revisar el funcionamiento se adjuntan:

- Capturas y video de la demostración del sistema.
- Scripts de la base de datos en la carpeta `Base de datos` de las evidencias.

---

## Ejecución en el entorno del equipo

### Requisitos

- Java 21
- Node.js 20 o superior
- Angular CLI 19
- Túnel hacia la base de datos activo

### 1. Configurar el backend

En `dima-bug-backend/src/main/resources`, copiar `application-example.properties` como `application.properties` y completar:

- Conexión a la base de datos
- `jwt.secret` (mínimo 32 caracteres)
- Datos del correo SMTP
- `dimabug.app.url`

> El archivo `application.properties` no se sube al repositorio porque contiene credenciales.

### 2. Levantar el backend

```
cd dima-bug-backend
mvnw spring-boot:run
```

API disponible en `http://localhost:9090`

### 3. Levantar el frontend

```
cd frontend-dimabug
npm install
npm start
```

Aplicación disponible en `http://localhost:4200`
