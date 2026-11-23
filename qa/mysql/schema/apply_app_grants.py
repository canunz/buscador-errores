"""Approved phase: app allowlist only, both accounts remain locked. No credentials rendered."""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import tarfile

HERE=Path(__file__).resolve().parent
SPEC=importlib.util.spec_from_file_location('qa_reader',HERE/'apply_admin_grants.py')
READER=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(READER)
COMPLETED=0
HOST='172.21.0.3'


def require(condition,message):
    if not condition:raise ValueError(message)


def unchanged_accounts_and_scope():
    expected="dimabug_qa_app\t172.21.0.3\tY\tcaching_sha2_password\tANY\t4\ndimabug_qa_schema\tlocalhost\tY\tcaching_sha2_password\t\t2"
    actual=READER.sql("SELECT User,Host,account_locked,plugin,ssl_type,max_user_connections FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') ORDER BY User;")
    require(actual.replace('\r\n','\n')==expected,'ACCOUNT_STATE_CHANGED')
    predicate="GRANTEE LIKE '''dimabug_qa_schema''@%' OR GRANTEE LIKE '''dimabug_qa_app''@%'"
    for table in ['USER_PRIVILEGES','SCHEMA_PRIVILEGES','COLUMN_PRIVILEGES']:
        where='('+predicate+')'+(" AND PRIVILEGE_TYPE <> 'USAGE'" if table=='USER_PRIVILEGES' else '')
        require(READER.sql('SELECT COUNT(*) FROM information_schema.'+table+' WHERE '+where+';')=='0','UNEXPECTED_'+table)
    require(READER.sql('SELECT @@global.mandatory_roles;')=='','MANDATORY_ROLES_EXIST')
    for table,field in [('role_edges','TO_USER'),('proxies_priv','User'),('procs_priv','User')]:
        require(READER.sql('SELECT COUNT(*) FROM mysql.'+table+" WHERE "+field+" IN ('dimabug_qa_schema','dimabug_qa_app');")=='0','UNEXPECTED_'+table.upper())


def effective(user):
    return {tuple(row.split('\t')) for row in READER.privilege_rows('TABLE_PRIVILEGES',user).splitlines()}


def main(apply):
    global COMPLETED
    spec=importlib.util.spec_from_file_location('schema_static',HERE/'verify_schema_proposal.py')
    static=importlib.util.module_from_spec(spec);spec.loader.exec_module(static);static.check()
    endpoint=json.loads(READER.docker(['context','inspect','desktop-linux','--format','{{json .Endpoints.docker.Host}}']))
    require(endpoint.startswith('npipe://'),'NONLOCAL_ENGINE')
    image=json.loads(READER.docker(['inspect','--format','{{json .Config.Image}}',READER.NAME]))
    require(image=='mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b','IMAGE_CHANGED')
    mounts=json.loads(READER.docker(['inspect','--format','{{json .Mounts}}',READER.NAME]))
    require(any(m.get('Name')=='dimabug-qa-mvp-13318-mysql-data' and m['Destination']=='/var/lib/mysql' for m in mounts),'VOLUME_CHANGED')
    archive=max((READER.ROOT/'qa/runtime/backups').glob('pre-proxy-*/mysql-data.tar'),key=lambda p:p.parent.name)
    with tarfile.open(archive) as tar:
        entry=next(m for m in tar if m.name.endswith('/auto.cnf') or m.name=='auto.cnf')
        uuid=re.search(rb'server-uuid\s*=\s*([a-f0-9-]+)',tar.extractfile(entry).read())[1].decode()
    require(READER.sql('SELECT @@version,@@server_uuid,CURRENT_USER();')=='8.0.46\t'+uuid+'\troot@localhost','SQL_IDENTITY_MISMATCH')
    networks=json.loads(READER.docker(['inspect','--format','{{json .NetworkSettings.Networks}}',READER.NAME]))
    require(set(networks)=={'dimabug-qa-mvp-13318-net'},'MYSQL_NETWORK_CHANGED')
    proxy=json.loads(READER.docker(['inspect','--format','{{json .NetworkSettings.Networks}}','dimabug-qa-mvp-13318-proxy']))
    require(proxy['dimabug-qa-mvp-13318-net']['IPAddress']==HOST,'PROXY_ORIGIN_CHANGED')
    require(json.loads(READER.docker(['network','inspect','--format','{{json .Internal}}','dimabug-qa-mvp-13318-net'])) is True,'NETWORK_ISOLATION_CHANGED')
    unchanged_accounts_and_scope()
    data=json.loads((HERE/'privileges.json').read_text())
    app=data['app']['per_table']
    require(len(app)==44 and all(set(values)<={'SELECT','INSERT','UPDATE','DELETE'} for values in app.values()),'APP_ALLOWLIST_INVALID')
    expected_admin={("'dimabug_qa_schema'@'localhost'",'dima_bug_qa_mvp',name,p,'NO') for name in app for p in data['admin']['per_table']}
    require(effective('dimabug_qa_schema')==expected_admin,'ADMIN_GRANTS_CHANGED')
    require(not effective('dimabug_qa_app'),'APP_GRANTS_ALREADY_EXIST')
    template=re.sub(r'--[^\n]*','',(HERE/'40_app_grants.sql.template').read_text())
    statements=[x.strip()+';' for x in template.split(';') if x.strip()]
    planned={}
    for statement in statements:
        match=re.fullmatch(r"GRANT ([A-Z, ]+) ON `dima_bug_qa_mvp`\.`(\w+)` TO 'dimabug_qa_app'@\{\{QA_PROXY_HOST_SQL_LITERAL\}\};",statement)
        require(match is not None,'UNEXPECTED_STATEMENT')
        require(match[2] not in planned,'DUPLICATE_GRANT')
        planned[match[2]]=set(match[1].split(', '))
    require(planned=={name:set(values) for name,values in app.items()},'TEMPLATE_ALLOWLIST_MISMATCH')
    runtime_spec=importlib.util.spec_from_file_location('runtime_model',HERE/'verify_schema_runtime.py')
    runtime=importlib.util.module_from_spec(runtime_spec);runtime_spec.loader.exec_module(runtime);runtime.check()
    if not apply:
        print(json.dumps({'estado':'APP_GRANTS_PREFLIGHT_OK','grants_ejecutados':0,'ambas_cuentas_bloqueadas':True}));return
    for statement in statements:
        READER.sql(statement.replace('{{QA_PROXY_HOST_SQL_LITERAL}}',"'"+HOST+"'"));COMPLETED+=1
    unchanged_accounts_and_scope()
    require(effective('dimabug_qa_schema')==expected_admin,'ADMIN_GRANTS_CHANGED')
    expected_app={("'dimabug_qa_app'@'"+HOST+"'",'dima_bug_qa_mvp',name,p,'NO') for name,values in app.items() for p in values}
    require(effective('dimabug_qa_app')==expected_app,'APP_EFFECTIVE_GRANTS_MISMATCH')
    require(READER.sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp';")=='44','TABLE_COUNT_CHANGED')
    require(READER.sql('SELECT COUNT(*) FROM dima_bug_qa_mvp.u_organizacion_control WHERE control_id=1;')=='1','CONTROL_ROW_CHANGED')
    print(json.dumps({'estado':'APP_GRANTS_VERIFICADOS','grants_ejecutados':COMPLETED,'tablas_autorizadas':44,
        'concesiones_efectivas_verificadas':len(expected_app),'ambas_cuentas_bloqueadas':True,'tls_app_obligatorio':True,
        'sin_ddl_para_app':True,'sin_grants_globales_o_de_esquema':True,'sin_grant_option':True,'gm_solo_select':True,
        'sin_desbloqueos':True,'secretos_mostrados':False}))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    try:main(args.apply)
    except Exception as exc:
        message=str(exc);control=message if re.fullmatch(r'[A-Z_0-9]+',message) else type(exc).__name__
        print(json.dumps({'estado':'APP_GRANTS_NO_VERIFICADOS','control':control,'grants_completados':COMPLETED,
            'reintento_automatico':False,'secretos_mostrados':False}));raise SystemExit(1)
