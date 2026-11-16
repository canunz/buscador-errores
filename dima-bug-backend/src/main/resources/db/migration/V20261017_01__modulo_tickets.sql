-- Módulo de tickets (DimaBug). Aplicar manualmente en dima_bug_dev.
-- Tablas nuevas con prefijo ticket_. Las referencias a otros módulos se guardan solo como id
-- (sin claves foráneas hacia u_usuario, cl_hardware, cl_sistema, cl_modulo ni co_conocimiento).

CREATE TABLE IF NOT EXISTS ticket (
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

CREATE TABLE IF NOT EXISTS ticket_etiqueta (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(40) NOT NULL,
    color  VARCHAR(7)  NOT NULL,
    activa BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_etiqueta_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_ticket_etiqueta (
    ticket_id   BIGINT NOT NULL,
    etiqueta_id BIGINT NOT NULL,
    PRIMARY KEY (ticket_id, etiqueta_id),
    KEY idx_tte_etiqueta (etiqueta_id),
    CONSTRAINT fk_tte_ticket   FOREIGN KEY (ticket_id)   REFERENCES ticket (id) ON DELETE CASCADE,
    CONSTRAINT fk_tte_etiqueta FOREIGN KEY (etiqueta_id) REFERENCES ticket_etiqueta (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ticket_comentario (
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

CREATE TABLE IF NOT EXISTS ticket_historial_estado (
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
