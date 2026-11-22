-- PROPUESTA QA VACIA: NO EJECUTADA. Revisar PLAN_ESQUEMA.md antes de aplicar.
USE `dima_bug_qa_mvp`;

CREATE TABLE u_rol (
 rol_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 rol_nombre VARCHAR(50) NOT NULL UNIQUE, rol_descripcion VARCHAR(200), rol_estado BOOLEAN NOT NULL,
 rol_fecha_creacion DATETIME(6) NOT NULL, rol_fecha_actualizacion DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE u_usuario (
 usuario_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, rol_id INT NOT NULL,
 usuario_nombre VARCHAR(100) NOT NULL, usuario_email VARCHAR(120) NOT NULL UNIQUE,
 usuario_password_hash VARCHAR(255) NOT NULL, usuario_estado BOOLEAN NOT NULL,
 usuario_fecha_creacion DATETIME(6) NOT NULL, usuario_fecha_actualizacion DATETIME(6) NOT NULL,
 usuario_ultimo_ingreso DATETIME(6), FOREIGN KEY (rol_id) REFERENCES u_rol(rol_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE u_departamento (
 departamento_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 departamento_nombre VARCHAR(255) NOT NULL, departamento_estado BOOLEAN NOT NULL,
 departamento_version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE u_departamento_contacto (
 contacto_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, departamento_id INT NOT NULL,
 contacto_tipo VARCHAR(50) NOT NULL, contacto_valor VARCHAR(100) NOT NULL,
 contacto_estado BOOLEAN NOT NULL, contacto_version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE u_responsable (
 responsable_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, departamento_id INT NOT NULL,
 responsable_nombre VARCHAR(255) NOT NULL, responsable_cargo VARCHAR(255),
 responsable_contacto VARCHAR(255), responsable_estado BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE u_organizacion_control (control_id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE cl_sistema (
 sistema_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, sistema_nombre VARCHAR(100) NOT NULL UNIQUE,
 sistema_descripcion VARCHAR(255), sistema_estado BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE cl_hardware (
 hardware_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, hardware_nombre VARCHAR(100) NOT NULL UNIQUE,
 hardware_sistema_operativo VARCHAR(150), hardware_estado BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE cl_hardware_sistema (
 hardware_id INT NOT NULL, sistema_id INT NOT NULL, PRIMARY KEY(hardware_id,sistema_id),
 FOREIGN KEY(hardware_id) REFERENCES cl_hardware(hardware_id),
 FOREIGN KEY(sistema_id) REFERENCES cl_sistema(sistema_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE cl_modulo (
 modulo_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, sistema_id INT,
 modulo_nombre VARCHAR(120) NOT NULL, modulo_estado BOOLEAN NOT NULL,
 FOREIGN KEY(sistema_id) REFERENCES cl_sistema(sistema_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE cl_frecuencia (
 frecuencia_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, frecuencia_nombre VARCHAR(50) NOT NULL,
 frecuencia_orden INT NOT NULL, frecuencia_estado BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_conocimiento (
 conocimiento_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_titulo VARCHAR(255) NOT NULL,
 conocimiento_descripcion TEXT NOT NULL,
 conocimiento_estado ENUM('BORRADOR','PUBLICADO','ELIMINADO') NOT NULL DEFAULT 'BORRADOR',
 hardware_id INT, sistema_id INT, modulo_id INT, frecuencia_id INT,
 conocimiento_comentario VARCHAR(500), creado_por INT NOT NULL, fecha_creacion DATETIME(6) NOT NULL,
 modificado_por INT, fecha_modificacion DATETIME(6),
 FOREIGN KEY(hardware_id) REFERENCES cl_hardware(hardware_id),
 FOREIGN KEY(sistema_id) REFERENCES cl_sistema(sistema_id),
 FOREIGN KEY(modulo_id) REFERENCES cl_modulo(modulo_id),
 FOREIGN KEY(frecuencia_id) REFERENCES cl_frecuencia(frecuencia_id),
 FOREIGN KEY(creado_por) REFERENCES u_usuario(usuario_id),
 FOREIGN KEY(modificado_por) REFERENCES u_usuario(usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_sintoma (
 sintoma_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT NOT NULL,
 sintoma_descripcion VARCHAR(300) NOT NULL, sintoma_orden INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_causa (
 causa_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT NOT NULL,
 causa_descripcion VARCHAR(300) NOT NULL, causa_orden INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_prueba (
 prueba_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, prueba_descripcion VARCHAR(500) NOT NULL UNIQUE,
 prueba_resultado_esperado VARCHAR(500), prueba_estado BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_conocimiento_prueba (
 conocimiento_id INT NOT NULL, prueba_id INT NOT NULL, prueba_orden INT NOT NULL,
 PRIMARY KEY(conocimiento_id,prueba_id), FOREIGN KEY(prueba_id) REFERENCES co_prueba(prueba_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_solucion (
 solucion_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT NOT NULL,
 solucion_descripcion TEXT NOT NULL, solucion_tipo ENUM('PASOS','DERIVACION') NOT NULL,
 solucion_orden INT NOT NULL, solucion_titulo VARCHAR(150),
 solucion_estado BOOLEAN NOT NULL DEFAULT TRUE, solucion_autoservicio BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_solucion_asignacion (
 asignacion_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, solucion_id INT NOT NULL,
 responsable_id INT, departamento_id INT, asignacion_principal BOOLEAN NOT NULL,
 FOREIGN KEY(solucion_id) REFERENCES co_solucion(solucion_id),
 FOREIGN KEY(responsable_id) REFERENCES u_responsable(responsable_id),
 FOREIGN KEY(departamento_id) REFERENCES u_departamento(departamento_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE pr_procedimiento (
 procedimiento_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 procedimiento_nombre VARCHAR(150) NOT NULL, procedimiento_descripcion TEXT,
 procedimiento_estado ENUM('BORRADOR','PUBLICADO') NOT NULL,
 creado_por INT NOT NULL, fecha_creacion DATETIME(6) NOT NULL,
 modificado_por INT, fecha_modificacion DATETIME(6), datos_editoriales_json TEXT, hardware_id INT,
 FOREIGN KEY(creado_por) REFERENCES u_usuario(usuario_id),
 FOREIGN KEY(modificado_por) REFERENCES u_usuario(usuario_id),
 CONSTRAINT fk_procedimiento_hardware FOREIGN KEY(hardware_id) REFERENCES cl_hardware(hardware_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE pr_paso (
 paso_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, procedimiento_id INT NOT NULL,
 paso_orden INT NOT NULL, paso_instruccion TEXT NOT NULL, paso_es_critico BOOLEAN NOT NULL,
 paso_modo_ejecucion VARCHAR(30) NOT NULL DEFAULT 'MANUAL', paso_mensaje_evidencia VARCHAR(2000),
 paso_configuracion_json TEXT, paso_config_historial TEXT,
 UNIQUE KEY uk_paso_orden(procedimiento_id,paso_orden)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_material_apoyo (
 material_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT, paso_id INT,
 material_nombre VARCHAR(150) NOT NULL, material_tipo ENUM('IMAGEN','PDF','VIDEO','ENLACE') NOT NULL,
 material_url VARCHAR(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE se_ejecucion (
 ejecucion_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, procedimiento_id INT NOT NULL, usuario_id INT NOT NULL,
 ejecucion_fecha_inicio DATETIME(6) NOT NULL, ejecucion_fecha_fin DATETIME(6),
 ejecucion_estado ENUM('EN_CURSO','COMPLETADA','CANCELADA') NOT NULL, ejecucion_observaciones VARCHAR(255),
 FOREIGN KEY(usuario_id) REFERENCES u_usuario(usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE se_ejecucion_paso (
 ejecucion_paso_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, ejecucion_id INT NOT NULL, paso_id INT NOT NULL,
 ejecucion_paso_cumplido BOOLEAN NOT NULL, ejecucion_paso_observacion VARCHAR(300), ejecucion_paso_fecha DATETIME(6),
 contrato_version INT, contrato_orden INT, contrato_instruccion TEXT, contrato_critico BOOLEAN,
 contrato_modo VARCHAR(30), contrato_configuracion_json TEXT, estado_detallado VARCHAR(30),
 revision BIGINT NOT NULL DEFAULT 0, UNIQUE KEY uk_ejecucion_paso(ejecucion_id,paso_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE se_resultado (
 resultado_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, solucion_id INT NOT NULL, usuario_id INT NOT NULL,
 resultado_funciono BOOLEAN NOT NULL, resultado_comentario VARCHAR(300), resultado_fecha DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE co_conocimiento_busqueda (
 conocimiento_id INT NOT NULL PRIMARY KEY, contenido_busqueda TEXT NOT NULL,
 FULLTEXT KEY ft_conocimiento_contenido(contenido_busqueda)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/02_sucursales_mysql.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE u_sucursal (
    sucursal_id INT NOT NULL AUTO_INCREMENT,
    sucursal_codigo VARCHAR(30) NOT NULL,
    sucursal_nombre VARCHAR(100) NOT NULL,
    responsable_nombre VARCHAR(100) NOT NULL,
    responsable_email VARCHAR(120) NOT NULL,
    sucursal_activa BOOLEAN NOT NULL DEFAULT TRUE,
    destinatarios_json TEXT NULL, ciudad VARCHAR(100) NULL, responsable_cargo VARCHAR(100) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (sucursal_id),
    CONSTRAINT uk_sucursal_codigo UNIQUE (sucursal_codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/03_flujo_evidencias_desarrollo.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE se_solicitud_evidencia (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 ejecucion_id INT NOT NULL,
 ejecucion_paso_id INT NOT NULL,
 ronda INT NOT NULL,
 sucursal_id INT NULL,
 sucursal_codigo VARCHAR(30) NULL,
 sucursal_nombre VARCHAR(100) NULL,
 responsable_nombre VARCHAR(100) NULL,
 responsable_email VARCHAR(120) NOT NULL,
 actor_id INT NOT NULL,
 procedimiento_id INT NOT NULL,
 clave_inicio VARCHAR(36) NULL,
 mensaje VARCHAR(2000) NOT NULL,
 recordatorio_1_horas INT NOT NULL,
 recordatorio_2_horas INT NOT NULL,
 escalamiento_horas INT NOT NULL,
 estado VARCHAR(30) NOT NULL,
 fecha_envio DATETIME(6) NULL,
 mensaje_enviado_id VARCHAR(100) NULL,
 regla_aplicada INT NOT NULL DEFAULT 0,
 proximo_seguimiento DATETIME(6) NULL,
 mensaje_recibido_id VARCHAR(36) NULL,
 huella_recibida VARCHAR(64) NULL,
 destinatarios_json TEXT NULL, configuracion_json TEXT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_solicitud_ronda UNIQUE(ejecucion_paso_id,ronda),
 CONSTRAINT uk_solicitud_inicio UNIQUE(actor_id,clave_inicio),
 INDEX ix_solicitud_seguimiento(estado,proximo_seguimiento),
 CONSTRAINT fk_solicitud_ejecucion FOREIGN KEY(ejecucion_id) REFERENCES se_ejecucion(ejecucion_id),
 CONSTRAINT fk_solicitud_paso FOREIGN KEY(ejecucion_paso_id) REFERENCES se_ejecucion_paso(ejecucion_paso_id),
 CONSTRAINT fk_solicitud_sucursal FOREIGN KEY(sucursal_id) REFERENCES u_sucursal(sucursal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/03_flujo_evidencias_desarrollo.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE se_evidencia (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 solicitud_id VARCHAR(36) NOT NULL,
 categoria VARCHAR(20) NOT NULL,
 sha256 VARCHAR(64) NOT NULL,
 datos MEDIUMBLOB NOT NULL,
 recibida DATETIME(6) NOT NULL,
 CONSTRAINT uk_evidencia_categoria UNIQUE(solicitud_id,categoria),
 CONSTRAINT fk_evidencia_solicitud FOREIGN KEY(solicitud_id) REFERENCES se_solicitud_evidencia(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/03_flujo_evidencias_desarrollo.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE se_evento_ejecucion (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ejecucion_id INT NOT NULL,
 solicitud_id VARCHAR(36) NULL,
 tipo VARCHAR(60) NOT NULL,
 actor_id INT NULL,
 detalle VARCHAR(1000) NOT NULL,
 fecha DATETIME(6) NOT NULL,
 INDEX ix_evento_ejecucion(ejecucion_id,id),
 CONSTRAINT fk_evento_ejecucion FOREIGN KEY(ejecucion_id) REFERENCES se_ejecucion(ejecucion_id),
 CONSTRAINT fk_evento_solicitud FOREIGN KEY(solicitud_id) REFERENCES se_solicitud_evidencia(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/06_edicion_administrativa_procedimientos.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE pr_auditoria_procedimiento (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  procedimiento_id INT NOT NULL,
  actor_id INT NOT NULL,
  fecha TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  accion VARCHAR(40) NOT NULL,
  antes_json LONGTEXT NULL,
  despues_json LONGTEXT NULL,
  INDEX ix_auditoria_procedimiento_fecha (procedimiento_id, fecha),
  CONSTRAINT fk_auditoria_procedimiento FOREIGN KEY (procedimiento_id) REFERENCES pr_procedimiento(procedimiento_id),
  CONSTRAINT fk_auditoria_procedimiento_actor FOREIGN KEY (actor_id) REFERENCES u_usuario(usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/09_gmail_oauth_cifrado.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE gm_cuenta_oauth (
  id INT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  estado VARCHAR(20) NOT NULL,
  access_token_cifrado TEXT NULL,
  refresh_token_cifrado TEXT NULL,
  revocacion_cifrada TEXT NULL,
  expira_en DATETIME(6) NULL,
  autorizado_por INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/09_gmail_oauth_cifrado.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE gm_estado_oauth (
  estado_hash VARCHAR(64) PRIMARY KEY,
  navegador_hash VARCHAR(64) NOT NULL,
  pkce_cifrado TEXT NOT NULL,
  actor_id INT NOT NULL,
  expira_en DATETIME(6) NOT NULL,
  version_cuenta BIGINT NOT NULL,
  consumido BIT NOT NULL DEFAULT 0,
  INDEX ix_gm_estado_actor_expira(actor_id,expira_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/09_gmail_oauth_cifrado.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE gm_auditoria_oauth (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id INT NULL,
  tipo VARCHAR(40) NOT NULL,
  fecha DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/10_prueba_gmail_administrativa.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE gm_prueba_correo (
 id VARCHAR(36) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 actor_id INT NOT NULL, remitente VARCHAR(120) NOT NULL, destinatario VARCHAR(120) NOT NULL,
 estado VARCHAR(20) NOT NULL, creada DATETIME(6) NOT NULL, intento DATETIME(6) NULL,
 finalizada DATETIME(6) NULL, mensaje_id VARCHAR(100) NULL, resultado VARCHAR(40) NULL,
 INDEX ix_gm_prueba_destino_fecha(destinatario,creada)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/10_prueba_gmail_administrativa.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE gm_prueba_evento (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, prueba_id VARCHAR(36) NOT NULL,
 actor_id INT NOT NULL, destinatario VARCHAR(120) NOT NULL, estado VARCHAR(20) NOT NULL,
 fecha DATETIME(6) NOT NULL, mensaje_id VARCHAR(100) NULL, resultado VARCHAR(40) NULL,
 INDEX ix_gm_prueba_evento_prueba(prueba_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/implementacion/automatizacion/11_materiales_soluciones_autoservicio.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE co_material_solucion (
 material_id INT NOT NULL AUTO_INCREMENT,
 solucion_id INT NOT NULL,
 nombre VARCHAR(150) NOT NULL,
 tipo VARCHAR(20) NOT NULL,
 url VARCHAR(500) NOT NULL,
 publicado BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(material_id),
 KEY ix_material_solucion(solucion_id),
 CONSTRAINT fk_material_solucion_padre FOREIGN KEY(solucion_id) REFERENCES co_solucion(solucion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE ticket (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    codigo              VARCHAR(20)  NOT NULL,
    titulo              VARCHAR(150) NOT NULL,
    descripcion         TEXT         NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'ABIERTO',
    prioridad           VARCHAR(10)  NOT NULL DEFAULT 'MEDIA',
    solicitante_id      INT          NOT NULL,
    asignado_id         INT          NULL,
    hardware_id         INT          NULL,
    sistema_id          INT          NULL,
    modulo_id           INT          NULL,
    conocimiento_id     INT          NULL,
    solucion            TEXT         NULL,
    fecha_creacion      DATETIME     NOT NULL,
    fecha_actualizacion DATETIME     NOT NULL,
    fecha_resolucion    DATETIME     NULL,
    fecha_cierre        DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_codigo (codigo),
    KEY idx_ticket_estado (estado),
    KEY idx_ticket_prioridad (prioridad),
    KEY idx_ticket_solicitante (solicitante_id),
    KEY idx_ticket_asignado (asignado_id),
    KEY idx_ticket_fecha_creacion (fecha_creacion),
    CONSTRAINT ck_ticket_estado CHECK (estado IN ('ABIERTO','EN_PROGRESO','EN_ESPERA','RESUELTO','CERRADO','CANCELADO')),
    CONSTRAINT ck_ticket_prioridad CHECK (prioridad IN ('BAJA','MEDIA','ALTA','CRITICA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE ticket_etiqueta (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(40) NOT NULL,
    color  VARCHAR(7)  NOT NULL,
    activa BOOLEAN     NOT NULL DEFAULT TRUE,
    nivel VARCHAR(15) NOT NULL DEFAULT 'NORMAL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_etiqueta_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE ticket_ticket_etiqueta (
    ticket_id   BIGINT NOT NULL,
    etiqueta_id BIGINT NOT NULL,
    PRIMARY KEY (ticket_id, etiqueta_id),
    KEY idx_tte_etiqueta (etiqueta_id),
    CONSTRAINT fk_tte_ticket   FOREIGN KEY (ticket_id)   REFERENCES ticket (id) ON DELETE CASCADE,
    CONSTRAINT fk_tte_etiqueta FOREIGN KEY (etiqueta_id) REFERENCES ticket_etiqueta (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE ticket_comentario (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT        NOT NULL,
    autor_id  INT           NOT NULL,
    texto     VARCHAR(1000) NOT NULL,
    interno   BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha     DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ticket_comentario_ticket (ticket_id, fecha),
    CONSTRAINT fk_ticket_comentario_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: dima-bug-backend/src/main/resources/db/migration/V20261017_01__modulo_tickets.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE ticket_historial_estado (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id       BIGINT       NOT NULL,
    estado_anterior VARCHAR(20)  NULL,
    estado_nuevo    VARCHAR(20)  NOT NULL,
    usuario_id      INT          NOT NULL,
    motivo          VARCHAR(255) NULL,
    fecha           DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ticket_historial_ticket (ticket_id, fecha),
    CONSTRAINT fk_ticket_historial_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: dima-bug-backend/src/main/resources/db/migration/V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE ticket_adjunto (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT       NOT NULL,
    comentario_id BIGINT       NULL,
    nombre        VARCHAR(150) NOT NULL,
    mime          VARCHAR(60)  NOT NULL,
    tamano        BIGINT       NOT NULL,
    referencia    VARCHAR(80)  NOT NULL,
    autor_id      INT          NOT NULL,
    fecha         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_adjunto_referencia (referencia),
    KEY idx_ticket_adjunto_ticket (ticket_id),
    CONSTRAINT fk_ticket_adjunto_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_adjunto_comentario FOREIGN KEY (comentario_id) REFERENCES ticket_comentario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: dima-bug-backend/src/main/resources/db/migration/V20261031_01__login_codigo_acceso.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE u_codigo_acceso (
    codigo_id   BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id  INT          NOT NULL,
    codigo_hash VARCHAR(100) NOT NULL,
    creado_en   DATETIME     NOT NULL,
    expira_en   DATETIME     NOT NULL,
    intentos    INT          NOT NULL DEFAULT 0,
    usado       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (codigo_id),
    KEY idx_codigo_acceso_usuario (usuario_id, usado),
    CONSTRAINT fk_codigo_acceso_usuario FOREIGN KEY (usuario_id) REFERENCES u_usuario (usuario_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fuente: docs/04_Base_Datos/V20261110_01__correo_codigo.sql (CREATE consolidado; no reaplicar ALTER historicos)
CREATE TABLE u_correo_codigo (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    codigo_id BIGINT NOT NULL,
    contenido_cifrado LONGTEXT NULL,
    estado VARCHAR(24) NOT NULL,
    intentos INT NOT NULL DEFAULT 0,
    expira DATETIME(6) NOT NULL,
    proximo_intento DATETIME(6) NOT NULL,
    actualizado_en DATETIME(6) NOT NULL,
    referencia VARCHAR(80) NULL,
    CONSTRAINT uq_correo_codigo UNIQUE (codigo_id),
    INDEX idx_correo_codigo_pendiente (estado, proximo_intento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
