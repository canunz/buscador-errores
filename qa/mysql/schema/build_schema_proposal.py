"""Offline proposal generator: reads repository sources and writes SQL TEXT only. No DB driver."""
from pathlib import Path
import hashlib
import json
import re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
JAVA = ROOT / 'dima-bug-backend/src/main/java'
MIG = 'dima-bug-backend/src/main/resources/db/migration/'
AUTO = 'docs/implementacion/automatizacion/'

CORE = r'''
CREATE TABLE u_rol (
 rol_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 rol_nombre VARCHAR(50) NOT NULL UNIQUE, rol_descripcion VARCHAR(200), rol_estado BOOLEAN NOT NULL,
 rol_fecha_creacion DATETIME(6) NOT NULL, rol_fecha_actualizacion DATETIME(6) NOT NULL
);
CREATE TABLE u_usuario (
 usuario_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, rol_id INT NOT NULL,
 usuario_nombre VARCHAR(100) NOT NULL, usuario_email VARCHAR(120) NOT NULL UNIQUE,
 usuario_password_hash VARCHAR(255) NOT NULL, usuario_estado BOOLEAN NOT NULL,
 usuario_fecha_creacion DATETIME(6) NOT NULL, usuario_fecha_actualizacion DATETIME(6) NOT NULL,
 usuario_ultimo_ingreso DATETIME(6), FOREIGN KEY (rol_id) REFERENCES u_rol(rol_id)
);
CREATE TABLE u_departamento (
 departamento_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 departamento_nombre VARCHAR(255) NOT NULL, departamento_estado BOOLEAN NOT NULL,
 departamento_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE u_departamento_contacto (
 contacto_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, departamento_id INT NOT NULL,
 contacto_tipo VARCHAR(50) NOT NULL, contacto_valor VARCHAR(100) NOT NULL,
 contacto_estado BOOLEAN NOT NULL, contacto_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE u_responsable (
 responsable_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, departamento_id INT NOT NULL,
 responsable_nombre VARCHAR(255) NOT NULL, responsable_cargo VARCHAR(255),
 responsable_contacto VARCHAR(255), responsable_estado BOOLEAN NOT NULL
);
CREATE TABLE u_organizacion_control (control_id INT NOT NULL PRIMARY KEY);
CREATE TABLE cl_sistema (
 sistema_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, sistema_nombre VARCHAR(100) NOT NULL UNIQUE,
 sistema_descripcion VARCHAR(255), sistema_estado BOOLEAN NOT NULL
);
CREATE TABLE cl_hardware (
 hardware_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, hardware_nombre VARCHAR(100) NOT NULL UNIQUE,
 hardware_sistema_operativo VARCHAR(150), hardware_estado BOOLEAN NOT NULL
);
CREATE TABLE cl_hardware_sistema (
 hardware_id INT NOT NULL, sistema_id INT NOT NULL, PRIMARY KEY(hardware_id,sistema_id),
 FOREIGN KEY(hardware_id) REFERENCES cl_hardware(hardware_id),
 FOREIGN KEY(sistema_id) REFERENCES cl_sistema(sistema_id)
);
CREATE TABLE cl_modulo (
 modulo_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, sistema_id INT,
 modulo_nombre VARCHAR(120) NOT NULL, modulo_estado BOOLEAN NOT NULL,
 FOREIGN KEY(sistema_id) REFERENCES cl_sistema(sistema_id)
);
CREATE TABLE cl_frecuencia (
 frecuencia_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, frecuencia_nombre VARCHAR(50) NOT NULL,
 frecuencia_orden INT NOT NULL, frecuencia_estado BOOLEAN NOT NULL
);
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
);
CREATE TABLE co_sintoma (
 sintoma_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT NOT NULL,
 sintoma_descripcion VARCHAR(300) NOT NULL, sintoma_orden INT NOT NULL
);
CREATE TABLE co_causa (
 causa_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT NOT NULL,
 causa_descripcion VARCHAR(300) NOT NULL, causa_orden INT NOT NULL
);
CREATE TABLE co_prueba (
 prueba_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, prueba_descripcion VARCHAR(500) NOT NULL UNIQUE,
 prueba_resultado_esperado VARCHAR(500), prueba_estado BOOLEAN NOT NULL
);
CREATE TABLE co_conocimiento_prueba (
 conocimiento_id INT NOT NULL, prueba_id INT NOT NULL, prueba_orden INT NOT NULL,
 PRIMARY KEY(conocimiento_id,prueba_id), FOREIGN KEY(prueba_id) REFERENCES co_prueba(prueba_id)
);
CREATE TABLE co_solucion (
 solucion_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT NOT NULL,
 solucion_descripcion TEXT NOT NULL, solucion_tipo ENUM('PASOS','DERIVACION') NOT NULL,
 solucion_orden INT NOT NULL, solucion_titulo VARCHAR(150),
 solucion_estado BOOLEAN NOT NULL DEFAULT TRUE, solucion_autoservicio BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE co_solucion_asignacion (
 asignacion_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, solucion_id INT NOT NULL,
 responsable_id INT, departamento_id INT, asignacion_principal BOOLEAN NOT NULL,
 FOREIGN KEY(solucion_id) REFERENCES co_solucion(solucion_id),
 FOREIGN KEY(responsable_id) REFERENCES u_responsable(responsable_id),
 FOREIGN KEY(departamento_id) REFERENCES u_departamento(departamento_id)
);
CREATE TABLE pr_procedimiento (
 procedimiento_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 procedimiento_nombre VARCHAR(150) NOT NULL, procedimiento_descripcion TEXT,
 procedimiento_estado ENUM('BORRADOR','PUBLICADO') NOT NULL,
 creado_por INT NOT NULL, fecha_creacion DATETIME(6) NOT NULL,
 modificado_por INT, fecha_modificacion DATETIME(6), datos_editoriales_json TEXT, hardware_id INT,
 FOREIGN KEY(creado_por) REFERENCES u_usuario(usuario_id),
 FOREIGN KEY(modificado_por) REFERENCES u_usuario(usuario_id),
 CONSTRAINT fk_procedimiento_hardware FOREIGN KEY(hardware_id) REFERENCES cl_hardware(hardware_id)
);
CREATE TABLE pr_paso (
 paso_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, procedimiento_id INT NOT NULL,
 paso_orden INT NOT NULL, paso_instruccion TEXT NOT NULL, paso_es_critico BOOLEAN NOT NULL,
 paso_modo_ejecucion VARCHAR(30) NOT NULL DEFAULT 'MANUAL', paso_mensaje_evidencia VARCHAR(2000),
 paso_configuracion_json TEXT, paso_config_historial TEXT,
 UNIQUE KEY uk_paso_orden(procedimiento_id,paso_orden)
);
CREATE TABLE co_material_apoyo (
 material_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, conocimiento_id INT, paso_id INT,
 material_nombre VARCHAR(150) NOT NULL, material_tipo ENUM('IMAGEN','PDF','VIDEO','ENLACE') NOT NULL,
 material_url VARCHAR(500) NOT NULL
);
CREATE TABLE se_ejecucion (
 ejecucion_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, procedimiento_id INT NOT NULL, usuario_id INT NOT NULL,
 ejecucion_fecha_inicio DATETIME(6) NOT NULL, ejecucion_fecha_fin DATETIME(6),
 ejecucion_estado ENUM('EN_CURSO','COMPLETADA','CANCELADA') NOT NULL, ejecucion_observaciones VARCHAR(255),
 FOREIGN KEY(usuario_id) REFERENCES u_usuario(usuario_id)
);
CREATE TABLE se_ejecucion_paso (
 ejecucion_paso_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, ejecucion_id INT NOT NULL, paso_id INT NOT NULL,
 ejecucion_paso_cumplido BOOLEAN NOT NULL, ejecucion_paso_observacion VARCHAR(300), ejecucion_paso_fecha DATETIME(6),
 contrato_version INT, contrato_orden INT, contrato_instruccion TEXT, contrato_critico BOOLEAN,
 contrato_modo VARCHAR(30), contrato_configuracion_json TEXT, estado_detallado VARCHAR(30),
 revision BIGINT NOT NULL DEFAULT 0, UNIQUE KEY uk_ejecucion_paso(ejecucion_id,paso_id)
);
CREATE TABLE se_resultado (
 resultado_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, solucion_id INT NOT NULL, usuario_id INT NOT NULL,
 resultado_funciono BOOLEAN NOT NULL, resultado_comentario VARCHAR(300), resultado_fecha DATETIME(6) NOT NULL
);
CREATE TABLE co_conocimiento_busqueda (
 conocimiento_id INT NOT NULL PRIMARY KEY, contenido_busqueda TEXT NOT NULL,
 FULLTEXT KEY ft_conocimiento_contenido(contenido_busqueda)
);
'''


def source_creates(relative):
    text = (ROOT / relative).read_text(encoding='utf-8-sig')
    # Strip comments before extracting statements; no statement is executed.
    text = re.sub(r'--[^\n]*', '', text)
    return {m[1]: re.sub(r'CREATE TABLE IF NOT EXISTS', 'CREATE TABLE', m[0], flags=re.I)
            for m in re.finditer(r'CREATE TABLE(?: IF NOT EXISTS)?\s+(\w+)\s*\([\s\S]*?;', text, re.I)}


def build():
    statements = CORE
    provenance = []
    selections = [
        (AUTO+'02_sucursales_mysql.sql', ['u_sucursal']),
        (AUTO+'03_flujo_evidencias_desarrollo.sql', ['se_solicitud_evidencia','se_evidencia','se_evento_ejecucion']),
        (AUTO+'06_edicion_administrativa_procedimientos.sql', ['pr_auditoria_procedimiento']),
        (AUTO+'09_gmail_oauth_cifrado.sql', ['gm_cuenta_oauth','gm_estado_oauth','gm_auditoria_oauth']),
        (AUTO+'10_prueba_gmail_administrativa.sql', ['gm_prueba_correo','gm_prueba_evento']),
        (AUTO+'11_materiales_soluciones_autoservicio.sql', ['co_material_solucion']),
        (MIG+'V20261017_01__modulo_tickets.sql', ['ticket','ticket_etiqueta','ticket_ticket_etiqueta','ticket_comentario','ticket_historial_estado']),
        (MIG+'V20261017_02__tickets_adjuntos_y_nivel_etiqueta.sql', ['ticket_adjunto']),
        (MIG+'V20261031_01__login_codigo_acceso.sql', ['u_codigo_acceso']),
        ('docs/04_Base_Datos/V20261110_01__correo_codigo.sql', ['u_correo_codigo'])]
    for path, names in selections:
        blocks = source_creates(path)
        provenance.append({'path': path, 'sha256': hashlib.sha256((ROOT/path).read_bytes()).hexdigest(), 'tables': names})
        for name in names:
            block = blocks[name]
            if name == 'u_sucursal':
                block = block.replace('revision BIGINT', 'destinatarios_json TEXT NULL, ciudad VARCHAR(100) NULL, responsable_cargo VARCHAR(100) NULL,\n    revision BIGINT')
            if name == 'se_solicitud_evidencia':
                for column, sqltype in [('sucursal_id','INT'),('sucursal_codigo','VARCHAR(30)'),('sucursal_nombre','VARCHAR(100)'),('responsable_nombre','VARCHAR(100)')]:
                    block = block.replace(f'{column} {sqltype} NOT NULL', f'{column} {sqltype} NULL')
                block = block.replace('version BIGINT', 'destinatarios_json TEXT NULL, configuracion_json TEXT NULL,\n version BIGINT')
            if name == 'se_evento_ejecucion':
                block = block.replace('solicitud_id VARCHAR(36) NOT NULL', 'solicitud_id VARCHAR(36) NULL')
            if name == 'ticket_etiqueta':
                block = block.replace('PRIMARY KEY (id)', "nivel VARCHAR(15) NOT NULL DEFAULT 'NORMAL',\n    PRIMARY KEY (id)")
            statements += '\n-- Fuente: '+path+' (CREATE consolidado; no reaplicar ALTER historicos)\n'+block+'\n'
    # Uniform explicit storage/collation. No CREATE IF NOT EXISTS, DROP or staging USE is copied.
    statements = re.sub(r'\)\s*(?:ENGINE\s*=\s*InnoDB)?\s*(?:DEFAULT CHARSET\s*=\s*utf8mb4)?\s*(?:COLLATE\s*=\s*utf8mb4_unicode_ci)?\s*;',
        ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;', statements, flags=re.I)
    sql = '-- PROPUESTA QA VACIA: NO EJECUTADA. Revisar PLAN_ESQUEMA.md antes de aplicar.\nUSE `dima_bug_qa_mvp`;\n'+statements
    names = re.findall(r'CREATE TABLE\s+(\w+)', sql, re.I)
    assert len(names) == len(set(names)) == 44
    (HERE/'20_schema_final.sql').write_text(sql, encoding='utf-8')
    tables = []
    for path in sorted(JAVA.rglob('*.java')):
        text = path.read_text(encoding='utf-8-sig')
        if '@Entity' not in text:
            continue
        match = re.search(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"', text)
        if match:
            tables.append({'table':match[1], 'entity':path.relative_to(ROOT).as_posix(), 'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    assert set(names) == {t['table'] for t in tables} | {'cl_hardware_sistema','ticket_ticket_etiqueta','co_conocimiento_busqueda'}
    (HERE/'schema_inventory.json').write_text(json.dumps({'status':'PROPOSED_NOT_EXECUTED', 'entity_tables':tables,
        'additional_tables':['cl_hardware_sistema','ticket_ticket_etiqueta','co_conocimiento_busqueda'],
        'ddl_sources':provenance, 'table_count':44}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status':'SCHEMA_SQL_PROPOSAL_WRITTEN','tables':44,'sql_executed':False}))


if __name__ == '__main__':
    build()
