"""Write placeholder SQL templates from an explicit allowlist; no credentials or DB access."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def build():
    inventory = json.loads((HERE/'schema_inventory.json').read_text())
    names = sorted({item['table'] for item in inventory['entity_tables']} | set(inventory['additional_tables']))
    app = {name: ['SELECT','INSERT','UPDATE'] for name in names}
    for name in ['u_rol','u_organizacion_control']:
        app[name] = ['SELECT']
    for name in ['pr_auditoria_procedimiento','se_evento_ejecucion','se_evidencia','se_resultado','ticket_comentario','ticket_historial_estado']:
        app[name] = ['SELECT','INSERT']
    for name in [n for n in names if n.startswith('gm_')]:
        app[name] = ['SELECT']  # QA baseline: Gmail/OAuth disabled; writes require separate review.
    for name in ['co_material_apoyo','co_material_solucion','co_conocimiento_busqueda','pr_paso']:
        app[name].append('DELETE')
    for name in ['cl_hardware_sistema','ticket_ticket_etiqueta']:
        app[name] = ['SELECT','INSERT','DELETE']  # Hibernate collection replacement.
    admin = ['SELECT','INSERT','UPDATE','DELETE','CREATE','ALTER','INDEX','REFERENCES']
    rows = ['-- PROPUESTA NO EJECUTADA. Root local; cuentas nuevas bloqueadas.\n',
            '-- CREATE se concede por tabla exacta antes del DDL, sin patrones de esquema.\n']
    for name in names:
        rows.append('GRANT '+', '.join(admin)+' ON `dima_bug_qa_mvp`.`'+name+"` TO 'dimabug_qa_schema'@'localhost';\n")
    (HERE/'18_admin_grants.sql').write_text(''.join(rows),encoding='utf-8')
    rows = ['-- PLANTILLA NO EJECUTADA. Root local; aplicar DESPUES de crear las 44 tablas.\n',
            '-- HOST exacto revalidado, sin %, sin grant global ni privilegios de esquema comodin.\n']
    for name in names:
        rows.append('GRANT '+', '.join(app[name])+' ON `dima_bug_qa_mvp`.`'+name+"` TO 'dimabug_qa_app'@{{QA_PROXY_HOST_SQL_LITERAL}};\n")
    (HERE/'40_app_grants.sql.template').write_text(''.join(rows),encoding='utf-8')
    (HERE/'privileges.json').write_text(json.dumps({'status':'PROPOSED_NOT_EXECUTED','admin':{'user':'dimabug_qa_schema','host':'localhost','per_table':admin},
        'app':{'user':'dimabug_qa_app','host':'EXACT_PROXY_INTERNAL_IPV4_REVALIDATED','per_table':app},
        'gmail_write_grants_included':False,'global_privileges':[],'grant_option':False,'drop_included':False},indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':'PRIVILEGE_TEMPLATES_WRITTEN','tables':len(names),'sql_executed':False,'credentials_generated':False}))


if __name__=='__main__':
    build()
