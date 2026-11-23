"""Approved one-shot TLS/DML/denial test. Default is read-only preflight; no automatic retry."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import time
from datetime import datetime,timezone

sys.path.insert(0,str(Path(__file__).resolve().parent))
from tls_support import HERE,ROOT,NAME,ACCOUNT,PIN,require,docker,sql,public_cert,PinnedContext,connection,app_password,close_window,clean_env,emit,pymysql

MARKER=2000000001


def snapshot(names,query=None):
    query=sql if query is None else query
    queries={
        'tables':"SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp' ORDER BY TABLE_NAME;",
        'columns':"SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'<NULL>'),EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dima_bug_qa_mvp' ORDER BY TABLE_NAME,ORDINAL_POSITION;",
        'indexes':"SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,INDEX_TYPE,COLUMN_NAME,SEQ_IN_INDEX FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dima_bug_qa_mvp' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;",
        'foreign':"SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='dima_bug_qa_mvp' ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION;",
        'accounts':"SELECT User,Host,account_locked,plugin,ssl_type,max_user_connections FROM mysql.user ORDER BY User,Host;",
        'roles':"SELECT FROM_USER,FROM_HOST,TO_USER,TO_HOST FROM mysql.role_edges ORDER BY FROM_USER,TO_USER;",
        'grants':"SELECT GRANTEE,TABLE_SCHEMA,TABLE_NAME,PRIVILEGE_TYPE,IS_GRANTABLE FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE LIKE '''dimabug_qa_%' ORDER BY GRANTEE,TABLE_SCHEMA,TABLE_NAME,PRIVILEGE_TYPE;",
    }
    result={key:query(statement) for key,statement in queries.items()}
    counts=query(' UNION ALL '.join("SELECT '"+name+"',COUNT(*) FROM dima_bug_qa_mvp.`"+name+'`' for name in sorted(names))+';')
    result['counts']=counts
    return {key:hashlib.sha256(value.encode()).hexdigest() for key,value in result.items()},dict((name,int(count)) for name,count in (line.split('\t') for line in counts.splitlines()))


def preflight(fixture_receipt=None,fixture_reset=False,recovery_metadata=None):
    endpoint=json.loads(docker(['context','inspect','desktop-linux','--format','{{json .Endpoints.docker.Host}}']))
    require(endpoint.startswith('npipe://'),'NONLOCAL_ENGINE')
    require(json.loads(docker(['inspect','--format','{{json .Config.Image}}',NAME]))=='mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b','MYSQL_IMAGE_MISMATCH')
    mounts=json.loads(docker(['inspect','--format','{{json .Mounts}}',NAME]))
    require(any(m.get('Name')=='dimabug-qa-mvp-13318-mysql-data' and m['Destination']=='/var/lib/mysql' for m in mounts),'VOLUME_MISMATCH')
    archive=max((ROOT/'qa/runtime/backups').glob('pre-proxy-*/mysql-data.tar'),key=lambda p:p.parent.name)
    with tarfile.open(archive) as tar:
        entry=next(m for m in tar if m.name.endswith('/auto.cnf') or m.name=='auto.cnf')
        uuid=re.search(rb'server-uuid\s*=\s*([a-f0-9-]+)',tar.extractfile(entry).read())[1].decode()
    require(sql('SELECT @@version,@@server_uuid,CURRENT_USER();')=='8.0.46\t'+uuid+'\troot@localhost','MYSQL_IDENTITY_MISMATCH')
    require(set(json.loads(docker(['inspect','--format','{{json .NetworkSettings.Networks}}',NAME])))=={'dimabug-qa-mvp-13318-net'},'MYSQL_NETWORK_CHANGED')
    proxy=json.loads(docker(['inspect','--format','{{json .NetworkSettings.Networks}}','dimabug-qa-mvp-13318-proxy']))
    require(proxy['dimabug-qa-mvp-13318-net']['IPAddress']=='172.21.0.3','PROXY_ORIGIN_CHANGED')
    require(sql("SELECT COUNT(*) FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') AND account_locked='Y';")=='2','ACCOUNTS_NOT_LOCKED')
    accounts=sql("SELECT User,Host,ssl_type FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') ORDER BY User;")
    require(accounts=="dimabug_qa_app\t172.21.0.3\tANY\ndimabug_qa_schema\tlocalhost",'ACCOUNT_OR_TLS_CHANGED')
    allow=json.loads((HERE.parent/'schema/privileges.json').read_text())
    names=set(allow['app']['per_table']);require(len(names)==44,'TABLE_ALLOWLIST_CHANGED')
    for user,host,expected in [('dimabug_qa_app','172.21.0.3',allow['app']['per_table']),('dimabug_qa_schema','localhost',{n:allow['admin']['per_table'] for n in names})]:
        query="SELECT GRANTEE,TABLE_SCHEMA,TABLE_NAME,PRIVILEGE_TYPE,IS_GRANTABLE FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE LIKE '''"+user+"''@%';"
        actual={tuple(line.split('\t')) for line in sql(query).splitlines()}
        require(actual=={("'"+user+"'@'"+host+"'",'dima_bug_qa_mvp',n,p,'NO') for n,ps in expected.items() for p in ps},'GRANT_ALLOWLIST_MISMATCH')
    pred="GRANTEE LIKE '''dimabug_qa_schema''@%' OR GRANTEE LIKE '''dimabug_qa_app''@%'"
    for table in ['USER_PRIVILEGES','SCHEMA_PRIVILEGES','COLUMN_PRIVILEGES']:
        condition='('+pred+')'+(" AND PRIVILEGE_TYPE<>'USAGE'" if table=='USER_PRIVILEGES' else '')
        require(sql('SELECT COUNT(*) FROM information_schema.'+table+' WHERE '+condition+';')=='0','UNEXPECTED_PRIVILEGES')
    require(sql('SELECT @@global.mandatory_roles;')=='','MANDATORY_ROLES_EXIST')
    for table,field in [('role_edges','TO_USER'),('procs_priv','User'),('proxies_priv','User')]:
        require(sql('SELECT COUNT(*) FROM mysql.'+table+' WHERE '+field+" IN ('dimabug_qa_app','dimabug_qa_schema');")=='0','ROLE_ROUTINE_OR_PROXY_GRANT')
    for table in ['TRIGGERS','ROUTINES','EVENTS']:
        column={'TRIGGERS':'TRIGGER_SCHEMA','ROUTINES':'ROUTINE_SCHEMA','EVENTS':'EVENT_SCHEMA'}[table]
        require(sql('SELECT COUNT(*) FROM information_schema.'+table+" WHERE "+column+"='dima_bug_qa_mvp';")=='0','UNEXPECTED_SCHEMA_PROGRAM')
    require(sql("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND COMMAND<>'Daemon';")=='0','OTHER_CLIENT_CONNECTIONS')
    require(sql('SELECT @@global.general_log,@@global.log_bin,@@global.slow_query_log,@@global.event_scheduler;')=='0\t0\t0\tOFF','LOG_OR_AUTOMATION_CHANGED')
    require(sql("SELECT COUNT(*) FROM information_schema.PLUGINS WHERE PLUGIN_TYPE='AUDIT' AND PLUGIN_STATUS='ACTIVE' AND NOT(PLUGIN_NAME IN ('sha2_cache_cleaner','mysqlx_cache_cleaner') AND PLUGIN_LIBRARY IS NULL);")=='0','AUDIT_PLUGIN_UNREVIEWED')
    require(sql("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='dima_bug_qa_mvp' AND TABLE_NAME='co_conocimiento_busqueda' AND REFERENCED_TABLE_NAME IS NOT NULL;")=='0','SYNTHETIC_TABLE_HAS_FK')
    require(sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp' AND TABLE_NAME='co_conocimiento_busqueda' AND ENGINE='InnoDB';")=='1','SYNTHETIC_TABLE_ENGINE_MISMATCH')
    require(sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp' AND TABLE_NAME='cl_frecuencia';")=='1','EXISTING_CANARY_MISSING')
    require(sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp' AND TABLE_NAME='__qa_tls_objeto_inexistente__';")=='0','ABSENT_CANARY_EXISTS')
    require(sql("SELECT COUNT(*) FROM mysql.user WHERE User='__qa_tls_destino_inexistente__';")=='0','ABSENT_GRANTEE_EXISTS')
    baseline,counts=snapshot(names)
    if recovery_metadata is not None:
        require(fixture_receipt is None and not fixture_reset,'RECOVERY_SCOPE_CONFLICT')
        require(set(recovery_metadata)==set(baseline)-{'counts'},'RECOVERY_METADATA_KEYS')
        require({k:v for k,v in baseline.items() if k!='counts'}==recovery_metadata,'RECOVERY_METADATA_CHANGED')
        sys.path.insert(0,str(ROOT/'qa/fixtures'))
        from fixture_tools import load,read_rows,assert_rows
        original=load('mvp-qa-v1')
        assert_rows(original,read_rows(sql,original))
        require(sum(counts.values())==67,'RECOVERY_ROW_COUNT')
    elif fixture_receipt is None:
        require(len(counts)==44 and counts.get('u_organizacion_control')==1 and sum(counts.values())==1,'NON_SYNTHETIC_BASELINE')
    else:
        # Explicit versioned profile only. All identity, grant, logging, TLS and isolation checks above remain mandatory.
        sys.path.insert(0,str(ROOT/'qa/fixtures'))
        from fixture_tools import verify_receipt,verify_reset_scope
        require((verify_reset_scope if fixture_reset else verify_receipt)(sql,fixture_receipt),'FIXTURE_BASELINE_REJECTED')
    ca=public_cert('ca.pem');leaf=public_cert('server-cert.pem')
    require(hashlib.sha256(ssl_der(leaf)).hexdigest()==PIN,'APPROVED_CERT_CHANGED')
    return names,baseline,ca,uuid


def ssl_der(pem):
    import ssl
    return ssl.PEM_cert_to_DER_cert(pem)


def main(execute):
    start=time.monotonic();folder=None;guard=None;opened=False;closed=False;password=None;conn=None
    report={'estado':'PREPARACION','casos':[],'sql_secrets_logged':False}
    try:
        names,baseline,ca,uuid=preflight()
        report['preflight']='OK';report['baseline_sha256']=baseline
        report['ca_sha256']=hashlib.sha256(ssl_der(ca)).hexdigest()
        report['client']='PyMySQL 1.2.4 (isolated, hash-pinned wheel)'
        if not execute:
            print(json.dumps({'estado':'TLS_PRIVILEGIOS_PREFLIGHT_OK','ambas_cuentas_bloqueadas':True,'tablas':44}));return
        password=app_password()
        folder=ROOT/'qa/runtime/tls-runs'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        folder.mkdir(parents=True,exist_ok=False)
        report['run_id']=folder.name;report['started_utc']=datetime.now(timezone.utc).isoformat()
        guard=subprocess.Popen([sys.executable,'-I','-B',str(HERE/'watchdog.py'),'--dir',str(folder),'--parent',str(os.getpid())],
            stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
            env=clean_env(),creationflags=subprocess.CREATE_NO_WINDOW|subprocess.CREATE_NEW_PROCESS_GROUP)
        deadline=time.monotonic()+10
        while not (folder/'ready.json').exists():
            require(guard.poll() is None and time.monotonic()<deadline,'GUARD_NOT_READY');time.sleep(.1)
        require(json.loads((folder/'ready.json').read_text())['estado']=='LISTO','GUARD_INVALID')
        opened=True  # Finally must lock even if the unlock reply is lost.
        sql('ALTER USER '+ACCOUNT+' ACCOUNT UNLOCK;')
        def app_connect():
            require(time.monotonic()-start<120,'TEST_DEADLINE')
            ctx=PinnedContext(ca,PIN);c=connection(ctx,password)
            require(ctx.pin_verified,'PIN_NOT_VERIFIED')
            with c.cursor() as cursor:
                cursor.execute('SELECT DATABASE(),CURRENT_USER(),USER(),@@server_uuid,CONNECTION_ID()')
                db,account,origin,actual_uuid,identifier=cursor.fetchone()
                require(db=='dima_bug_qa_mvp' and account=='dimabug_qa_app@172.21.0.3' and origin=='dimabug_qa_app@172.21.0.3' and actual_uuid==uuid,'AUTHENTICATED_IDENTITY_MISMATCH')
                cursor.execute("SHOW SESSION STATUS LIKE 'Ssl_cipher'");require(bool(cursor.fetchone()[1]),'TLS_CIPHER_EMPTY')
            report['tls']={'version':ctx.negotiated_protocol,'cipher':ctx.negotiated_cipher,'pin_verified_before_auth':True,'leaf_sha256':PIN}
            return c
        conn=app_connect()
        try:
            conn.begin()
            with conn.cursor() as c:
                c.execute('SELECT control_id FROM u_organizacion_control WHERE control_id=1');require(c.fetchone()==(1,),'POSITIVE_SELECT_FAILED')
                report['casos'].append({'id':'SELECT','resultado':'OK'})
                c.execute('INSERT INTO co_conocimiento_busqueda(conocimiento_id,contenido_busqueda) VALUES(%s,%s)',(MARKER,'QA_TLS_SINTETICO_A'));require(c.rowcount==1,'POSITIVE_INSERT_FAILED')
                c.execute('SELECT contenido_busqueda FROM co_conocimiento_busqueda WHERE conocimiento_id=%s',(MARKER,));require(c.fetchone()==('QA_TLS_SINTETICO_A',),'INSERT_READBACK_FAILED')
                report['casos'].append({'id':'INSERT','resultado':'OK','filas':1})
                c.execute('UPDATE co_conocimiento_busqueda SET contenido_busqueda=%s WHERE conocimiento_id=%s',('QA_TLS_SINTETICO_B',MARKER));require(c.rowcount==1,'POSITIVE_UPDATE_FAILED')
                c.execute('SELECT contenido_busqueda FROM co_conocimiento_busqueda WHERE conocimiento_id=%s',(MARKER,));require(c.fetchone()==('QA_TLS_SINTETICO_B',),'UPDATE_READBACK_FAILED')
                report['casos'].append({'id':'UPDATE','resultado':'OK','filas':1})
                c.execute('DELETE FROM co_conocimiento_busqueda WHERE conocimiento_id=%s',(MARKER,));require(c.rowcount==1,'POSITIVE_DELETE_FAILED')
                report['casos'].append({'id':'DELETE','resultado':'OK','filas':1})
        finally:
            conn.rollback();conn.close();conn=None
        require(sql('SELECT COUNT(*) FROM dima_bug_qa_mvp.co_conocimiento_busqueda WHERE conocimiento_id=2000000001;')=='0','MARKER_PERSISTED')
        report['rollback_verified']=True
        conn=app_connect()
        # All destinations are fixed and checked before unlock; no business row can match.
        negatives=[]
        for table,column in [('gm_cuenta_oauth','version'),('gm_estado_oauth','version_cuenta'),('gm_auditoria_oauth','tipo'),('gm_prueba_correo','version'),('gm_prueba_evento','estado')]:
            negatives.extend([(table+'_UPDATE','UPDATE `'+table+'` SET `'+column+'`=`'+column+'` WHERE 1=0'),(table+'_DELETE','DELETE FROM `'+table+'` WHERE 1=0')])
        negatives += [('GMAIL_INSERT',"INSERT INTO gm_cuenta_oauth(id,version,estado) SELECT 2000000001,0,'DESCONECTADA' WHERE 1=0"),
            ('OTHER_DATABASE','SELECT 1 FROM mysql.user WHERE 1=0'),
            ('DDL_CREATE','CREATE TABLE cl_frecuencia(qa_col INT)'),
            ('DDL_ALTER','ALTER TABLE __qa_tls_objeto_inexistente__ ADD COLUMN qa_col INT'),
            ('DDL_DROP','DROP TABLE __qa_tls_objeto_inexistente__'),
            ('GLOBAL_CREATE_USER',"CREATE USER 'dimabug_qa_app'@'172.21.0.3'"),
            ('GRANT_OPTION',"GRANT SELECT ON dima_bug_qa_mvp.cl_frecuencia TO '__qa_tls_destino_inexistente__'@'localhost'")]
        with conn.cursor() as c:
            for case,statement in negatives:
                require(time.monotonic()-start<120,'TEST_DEADLINE')
                try:c.execute(statement)
                except pymysql.MySQLError as error:
                    number=error.args[0] if error.args and isinstance(error.args[0],int) else None
                    status='DENEGADO' if number in {1044,1142,1227} else 'INCONCLUSA'
                    report['casos'].append({'id':case,'resultado':status,'errno':number,
                        'sqlstate':None,'sqlstate_note':'PyMySQL does not expose SQLSTATE; authorization classification uses numeric errno only'})
                    require(status=='DENEGADO','NEGATIVE_INCONCLUSIVE_'+case)
                else:
                    report['casos'].append({'id':case,'resultado':'PERMITIDO_INESPERADO'})
                    raise RuntimeError('NEGATIVE_UNEXPECTED_SUCCESS_'+case)
        conn.close();conn=None
        report['estado']='PRUEBAS_COMPLETADAS'
    except Exception as error:
        msg=str(error)
        report['estado']='PRUEBA_NO_COMPLETA'
        report['control']=msg if re.fullmatch(r'[A-Z_0-9]+',msg) else type(error).__name__
    finally:
        if conn is not None:
            try:conn.rollback();conn.close()
            except Exception:pass
        if opened:
            try:
                closed=close_window()
                final,_=snapshot(names);require(final==baseline,'BASELINE_CHANGED')
                require(sql("SELECT COUNT(*) FROM mysql.user WHERE User='__qa_tls_destino_inexistente__';")=='0','CANARY_ACCOUNT_CREATED')
                ctx=PinnedContext(ca,PIN)
                try:
                    unexpected=connection(ctx,password);unexpected.close();raise RuntimeError('LOCKED_LOGIN_SUCCEEDED')
                except pymysql.MySQLError as error:
                    require(error.args[0]==3118,'LOCKED_LOGIN_NOT_CONFIRMED')
                    require(ctx.pin_verified,'FINAL_LOCK_CHECK_TLS_INVALID')
                report['final_baseline_unchanged']=True;report['locked_login_rejected']=True
                (folder/'disarm').write_text('closure_verified',encoding='ascii')
                guard.wait(timeout=10)
                require(json.loads((folder/'guard-final.json').read_text())['closed'] is True,'GUARD_FINAL_NOT_VERIFIED')
                report['guard_close_verified']=True
            except Exception:
                report['closure_or_baseline_verification_failed']=True
        password=None
        report['both_accounts_locked_and_no_app_sessions']=closed
        report['elapsed_seconds']=round(time.monotonic()-start,3)
        if folder:
            report['source_sha256']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),HERE/'tls_support.py',HERE/'watchdog.py',HERE/'requirements-client.txt']}
            emit(folder/'result.json',report)
        if execute:
            success=report.get('estado')=='PRUEBAS_COMPLETADAS' and closed and report.get('guard_close_verified') and not report.get('closure_or_baseline_verification_failed')
            print(json.dumps({'estado':'PRUEBA_TLS_PRIVILEGIOS_OK' if success else 'PRUEBA_TLS_PRIVILEGIOS_NO_COMPLETA','casos':report.get('casos',[]),'cierre_verificado':closed,'reporte':str(folder/'result.json') if folder else None,'secretos_mostrados':False}))
            if not success:raise SystemExit(1)
        elif report.get('estado')=='PRUEBA_NO_COMPLETA':
            print(json.dumps({'estado':'TLS_PRIVILEGIOS_PREFLIGHT_NO_VERIFICADO','control':report.get('control'),'sin_desbloqueos':True,'secretos_mostrados':False}))
            raise SystemExit(1)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--execute',action='store_true');args=parser.parse_args();main(args.execute)
