"""Offline coverage/security checks. Never imports a SQL driver or invokes Docker."""
import json
from pathlib import Path
import re
import hashlib

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]


def require(condition,message):
    if not condition: raise ValueError(message)


def check():
    sql=(HERE/'20_schema_final.sql').read_text()
    blocks={m[1]:m[2] for m in re.finditer(r'CREATE TABLE\s+(\w+)\s*\(([\s\S]*?)\) ENGINE',sql,re.I)}
    inventory=json.loads((HERE/'schema_inventory.json').read_text())
    expected={x['table'] for x in inventory['entity_tables']} | set(inventory['additional_tables'])
    require(len(blocks)==44 and set(blocks)==expected,'Table coverage')
    seen=set()
    for name,body in blocks.items():
        for parent in re.findall(r'REFERENCES\s+(\w+)\s*\(',body,re.I):
            require(parent in seen,'Foreign-key creation order: '+name)
        seen.add(name)
    for item in inventory['entity_tables']:
        source=(ROOT/item['entity']).read_text(encoding='utf-8-sig')
        require(hashlib.sha256((ROOT/item['entity']).read_bytes()).hexdigest()==item['sha256'],'Entity changed since inventory')
        # Explicit column/join mappings must be present; embedded/implicit fields also require manual review.
        columns=re.findall(r'@(Column|JoinColumn)\s*\(\s*name\s*=\s*"([^"]+)"',source)
        body=blocks[item['table']]
        for _,column in columns:
            # JoinTable inverse columns are in the dedicated collection table, not the owner table.
            if item['table']=='cl_hardware' and column=='sistema_id': continue
            if item['table']=='ticket' and column in {'ticket_id','etiqueta_id'}:
                require(column in blocks['ticket_ticket_etiqueta'],'Missing collection column')
                continue
            require(re.search(r'\b'+re.escape(column)+r'\b',body) is not None,'Missing column '+item['table']+'.'+column)
    for column in ['contrato_configuracion_json','contrato_version','contrato_critico']:
        require(column in blocks['se_ejecucion_paso'],'Missing embedded contract')
    require('usuario_ultimo_ingreso' in blocks['u_usuario'],'Missing native-query column')
    require('FULLTEXT KEY ft_conocimiento_contenido(contenido_busqueda)' in blocks['co_conocimiento_busqueda'],'Missing FULLTEXT')
    require(not re.search(r'\b(DROP|TRUNCATE|IF NOT EXISTS|FOREIGN_KEY_CHECKS)\b',sql,re.I),'Unsafe DDL')
    require('dima_bug_staging' not in sql and 'dima_bug_prod' not in sql,'Wrong target')
    privileges=json.loads((HERE/'privileges.json').read_text())
    for item in inventory['ddl_sources']:
        require(hashlib.sha256((ROOT/item['path']).read_bytes()).hexdigest()==item['sha256'],'DDL source changed since inventory')
    require(set(privileges['app']['per_table'])==expected,'Grant coverage')
    allowed={'SELECT','INSERT','UPDATE','DELETE'}
    require(all(set(p)<=allowed for p in privileges['app']['per_table'].values()),'App DDL/global privilege')
    for file in ['18_admin_grants.sql','40_app_grants.sql.template']:
        text=(HERE/file).read_text()
        grants=[line for line in text.splitlines() if line.startswith('GRANT ')]
        require(len(grants)==44,'Grant count')
        require(all(re.fullmatch(r"GRANT [A-Z, ]+ ON `dima_bug_qa_mvp`\.`\w+` TO .+;",line) for line in grants),'Nonliteral grant scope')
        require(not any('GRANT OPTION' in line or 'ALL PRIVILEGES' in line or 'DROP' in line for line in grants),'Excessive privilege')
        actual={}
        host="'localhost'" if file.startswith('18') else '{{QA_PROXY_HOST_SQL_LITERAL}}'
        user='dimabug_qa_schema' if file.startswith('18') else 'dimabug_qa_app'
        for line in grants:
            match=re.fullmatch(r"GRANT ([A-Z, ]+) ON `dima_bug_qa_mvp`\.`(\w+)` TO '"+user+r"'@"+re.escape(host)+r';',line)
            require(match is not None,'Wrong account or host')
            require(match[2] not in actual,'Duplicate grant')
            actual[match[2]]=set(match[1].split(', '))
        wanted={name:set(privileges['admin']['per_table']) for name in expected} if file.startswith('18') else {name:set(p) for name,p in privileges['app']['per_table'].items()}
        require(actual==wanted,'SQL grant differs from approved allowlist')
    users=(HERE/'15_create_users.sql.template').read_text()
    require(users.count('ACCOUNT LOCK')==2 and 'REQUIRE SSL' in users,'Account gates')
    require('BY {{QA_SCHEMA_PASSWORD_SQL_LITERAL}}' in users and 'BY {{QA_APP_PASSWORD_SQL_LITERAL}}' in users,'Secret placeholders')
    print(json.dumps({'status':'SCHEMA_PROPOSAL_STATIC_OK','entity_tables':41,'total_tables':44,
        'sql_executed':False,'runtime_type_validation_pending':True}))


if __name__=='__main__':
    try: check()
    except Exception as exc:
        print(json.dumps({'status':'SCHEMA_PROPOSAL_NOT_VERIFIED','reason':str(exc)}))
        raise SystemExit(1)
