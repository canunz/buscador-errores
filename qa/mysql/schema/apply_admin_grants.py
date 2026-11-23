"""Approved phase: literal admin table grants only. Default is read-only preflight."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import tarfile

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
NAME='dimabug-qa-mvp-13318-mysql'
BASE=['docker','--context','desktop-linux']
COMPLETED=0


def require(condition,message):
    if not condition: raise ValueError(message)


def docker(args,stdin=None):
    result=subprocess.run(BASE+args,input=stdin,capture_output=True,text=True,timeout=20)
    if result.returncode:
        # Only numeric server error code; never print raw stderr or SQL.
        match=re.search(r'ERROR (\d+)',result.stderr)
        raise RuntimeError('SQL_ERROR_'+match[1] if match else 'DOCKER_OPERATION_FAILED')
    return result.stdout.strip()


def sql(statement):
    command='MYSQL_HISTFILE=/dev/null MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql --protocol=socket --user=root --batch --raw --skip-column-names --connect-timeout=5'
    return docker(['exec','-i',NAME,'sh','-c',command],statement+'\n')


def privilege_rows(table,user):
    predicate="GRANTEE LIKE '''"+user+"''@%'"
    return sql('SELECT GRANTEE,TABLE_SCHEMA,TABLE_NAME,PRIVILEGE_TYPE,IS_GRANTABLE FROM information_schema.'+table+' WHERE '+predicate+';')


def check_unchanged():
    expected="dimabug_qa_app\t172.21.0.3\tY\tcaching_sha2_password\tANY\t4\ndimabug_qa_schema\tlocalhost\tY\tcaching_sha2_password\t\t2"
    accounts=sql("SELECT User,Host,account_locked,plugin,ssl_type,max_user_connections FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') ORDER BY User;")
    require(accounts.replace('\r\n','\n')==expected,'ACCOUNT_STATE_CHANGED')
    require(sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp';")=='0','TABLES_EXIST')
    require(sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dima_bug_qa_mvp' AND DEFAULT_CHARACTER_SET_NAME='utf8mb4' AND DEFAULT_COLLATION_NAME='utf8mb4_unicode_ci';")=='1','SCHEMA_MISMATCH')
    require(sql('SELECT @@global.mandatory_roles;')=='','MANDATORY_ROLES_EXIST')
    require(sql("SELECT COUNT(*) FROM mysql.role_edges WHERE TO_USER IN ('dimabug_qa_schema','dimabug_qa_app');")=='0','ROLES_EXIST')
    require(sql("SELECT COUNT(*) FROM mysql.proxies_priv WHERE User IN ('dimabug_qa_schema','dimabug_qa_app');")=='0','PROXY_PRIVILEGES_EXIST')
    require(sql("SELECT COUNT(*) FROM mysql.procs_priv WHERE User IN ('dimabug_qa_schema','dimabug_qa_app');")=='0','ROUTINE_PRIVILEGES_EXIST')
    predicate="GRANTEE LIKE '''dimabug_qa_schema''@%' OR GRANTEE LIKE '''dimabug_qa_app''@%'"
    for table in ['USER_PRIVILEGES','SCHEMA_PRIVILEGES','COLUMN_PRIVILEGES']:
        where='('+predicate+')'+(" AND PRIVILEGE_TYPE <> 'USAGE'" if table=='USER_PRIVILEGES' else '')
        require(sql('SELECT COUNT(*) FROM information_schema.'+table+' WHERE '+where+';')=='0','UNEXPECTED_'+table)
    require(privilege_rows('TABLE_PRIVILEGES','dimabug_qa_app')=='','APP_PRIVILEGES_EXIST')


def main(apply):
    global COMPLETED
    endpoint=json.loads(docker(['context','inspect','desktop-linux','--format','{{json .Endpoints.docker.Host}}']))
    require(endpoint.startswith('npipe://'),'NONLOCAL_ENGINE')
    require(json.loads(docker(['inspect','--format','{{json .State.Running}}',NAME])) is True,'MYSQL_NOT_RUNNING')
    require(json.loads(docker(['inspect','--format','{{json .Config.Image}}',NAME]))=='mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b','IMAGE_MISMATCH')
    mounts=json.loads(docker(['inspect','--format','{{json .Mounts}}',NAME]))
    require(any(m.get('Name')=='dimabug-qa-mvp-13318-mysql-data' and m['Destination']=='/var/lib/mysql' for m in mounts),'VOLUME_MISMATCH')
    archive=max((ROOT/'qa/runtime/backups').glob('pre-proxy-*/mysql-data.tar'),key=lambda p:p.parent.name)
    with tarfile.open(archive) as tar:
        entry=next(m for m in tar if m.name.endswith('/auto.cnf') or m.name=='auto.cnf')
        uuid=re.search(rb'server-uuid\s*=\s*([a-f0-9-]+)',tar.extractfile(entry).read())[1].decode()
    require(sql('SELECT @@version,@@server_uuid,CURRENT_USER();')=='8.0.46\t'+uuid+'\troot@localhost','SQL_IDENTITY_MISMATCH')
    data=json.loads((HERE/'privileges.json').read_text())
    names=set(data['app']['per_table'])
    privileges={'SELECT','INSERT','UPDATE','DELETE','CREATE','ALTER','INDEX','REFERENCES'}
    require(set(data['admin']['per_table'])==privileges and len(names)==44,'ALLOWLIST_CHANGED')
    statements=[q.strip()+';' for q in re.sub(r'--[^\n]*','',(HERE/'18_admin_grants.sql').read_text()).split(';') if q.strip()]
    planned={}
    for statement in statements:
        match=re.fullmatch(r"GRANT ([A-Z, ]+) ON `dima_bug_qa_mvp`\.`(\w+)` TO 'dimabug_qa_schema'@'localhost';",statement)
        require(match is not None,'UNEXPECTED_STATEMENT')
        require(match[2] not in planned and set(match[1].split(', '))==privileges,'GRANT_CHANGED')
        planned[match[2]]=privileges
    require(set(planned)==names,'TABLE_ALLOWLIST_CHANGED')
    check_unchanged()
    require(privilege_rows('TABLE_PRIVILEGES','dimabug_qa_schema')=='','ADMIN_GRANTS_ALREADY_EXIST')
    if not apply:
        print(json.dumps({'estado':'ADMIN_GRANTS_PREFLIGHT_OK','sentencias_previstas':44,'grants_ejecutados':0}));return
    for statement in statements:
        sql(statement);COMPLETED+=1
    check_unchanged()
    rows=privilege_rows('TABLE_PRIVILEGES','dimabug_qa_schema').splitlines()
    actual={tuple(row.split('\t')) for row in rows}
    expected={("'dimabug_qa_schema'@'localhost'",'dima_bug_qa_mvp',name,p,'NO') for name in names for p in privileges}
    require(actual==expected,'EFFECTIVE_GRANTS_MISMATCH')
    print(json.dumps({'estado':'ADMIN_GRANTS_VERIFICADOS','grants_ejecutados':COMPLETED,'tablas_autorizadas':44,
        'concesiones_por_tabla_verificadas':len(actual),'ambas_cuentas_bloqueadas':True,'app_sin_permisos':True,
        'sin_privilegios_globales':True,'sin_grant_option':True,'sin_drop':True,'tablas_creadas':0,'secretos_mostrados':False}))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    try:main(args.apply)
    except Exception as exc:
        # All program-generated errors are fixed control names; avoid exception dumps.
        message=str(exc)
        control=message if re.fullmatch(r'[A-Z_0-9]+',message) else type(exc).__name__
        print(json.dumps({'estado':'ADMIN_GRANTS_NO_VERIFICADOS','control':control,
            'grants_completados':COMPLETED,'reintento_automatico':False,'secretos_mostrados':False}))
        raise SystemExit(1)
