"""Future close-only adapter. NO action at import/default invocation; never unlocks."""
import argparse
import json
from pathlib import Path
import re
import sys
import time
from datetime import datetime,timezone

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'qa/mysql/tls'))

def validate_identity(context,image,mounts,networks,ports):
    if not (context.startswith('npipe://') and image=='mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'):
        raise RuntimeError('QA_MYSQL_IDENTITY_REJECTED')
    data=[m for m in mounts if m['Destination']=='/var/lib/mysql']
    secret=[m for m in mounts if m['Destination']=='/run/secrets/mysql_root_password']
    if len(data)!=1 or data[0].get('Name')!='dimabug-qa-mvp-13318-mysql-data' or len(secret)!=1 or secret[0].get('RW') is not False:
        raise RuntimeError('QA_MYSQL_MOUNTS_REJECTED')
    source=secret[0]['Source'].replace('\\','/').lower()
    if not source.endswith('/dimabug-mvp/qa/runtime/secrets/mysql-root.txt'):
        raise RuntimeError('QA_MYSQL_SECRET_ORIGIN_REJECTED')
    if set(networks)!={'dimabug-qa-mvp-13318-net'} or any(ports.values()):raise RuntimeError('QA_MYSQL_NETWORK_REJECTED')

def guard_sql(statement,*,docker=None,query=None):
    # Existing local QA root mechanism; no new account, grant, secret, SQL target or remote context.
    if docker is None or query is None:
        from tls_support import docker as real_docker,sql
        docker=docker or real_docker;query=query or sql
    context=json.loads(docker(['context','inspect','desktop-linux','--format','{{json .Endpoints.docker.Host}}']))
    metadata=json.loads(docker(['inspect','--format','{{json .}}','dimabug-qa-mvp-13318-mysql']))
    validate_identity(context,metadata['Config']['Image'],metadata['Mounts'],metadata['NetworkSettings']['Networks'],metadata['HostConfig']['PortBindings'] or {})
    allowed={"ALTER USER 'dimabug_qa_app'@'172.21.0.3' ACCOUNT LOCK;",
        "SELECT ID,USER FROM information_schema.PROCESSLIST WHERE USER='dimabug_qa_app';",
        "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER='dimabug_qa_app';",
        "SELECT COUNT(*) FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') AND account_locked='Y';"}
    if statement not in allowed and not re.fullmatch(r'KILL CONNECTION [1-9][0-9]*;',statement):raise RuntimeError('QA_CLOSE_SQL_REJECTED')
    return query(statement)

def close():
    from tls_support import close_window
    return close_window(query=guard_sql)

def close_report(query=guard_sql,*,actor='adapter',run_id='unspecified'):
    from tls_support import close_window
    start=time.monotonic()
    report={'actor':actor,'run_id':run_id,'utc':datetime.now(timezone.utc).isoformat(),
        'estado':'QA_ACCOUNT_CLOSE_NOT_VERIFIED','closed':False,'phase':'LOCK','lock_attempted':False,
        'lock_acknowledged':None,'account_locked_verified':None,'app_sessions_zero_verified':None,
        'database_state':'DESCONOCIDO','failure_code':None,'manual_recovery_required':True}
    def tracked(statement):
        lock=statement=="ALTER USER 'dimabug_qa_app'@'172.21.0.3' ACCOUNT LOCK;"
        sessions=statement=="SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER='dimabug_qa_app';"
        accounts=statement=="SELECT COUNT(*) FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') AND account_locked='Y';"
        report['phase']='LOCK' if lock else 'VERIFY_SESSIONS' if sessions else 'VERIFY_ACCOUNTS' if accounts else 'SESSIONS'
        if lock:report['lock_attempted']=True
        result=query(statement)
        if lock:report['lock_acknowledged']=True
        if sessions:report['app_sessions_zero_verified']=result=='0'
        if accounts:report['account_locked_verified']=result=='2'
        return result
    try:
        close_window(query=tracked)
        report.update(estado='QA_ACCOUNT_CLOSE_VERIFIED',closed=True,phase='COMPLETE',database_state='CONFIRMADO',manual_recovery_required=False)
    except Exception as exc:
        # Numeric SQL codes only, never SQL/provider messages or tracebacks.
        match=re.fullmatch(r'SQL_ERROR_([0-9]+)',str(exc)) if isinstance(exc,RuntimeError) else None
        report['failure_code']='SQL_ERROR_'+match[1] if match else 'CONTROL_OR_TRANSPORT_ERROR'
    report['elapsed_ms']=round((time.monotonic()-start)*1000)
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--execute-close',action='store_true')
    parser.add_argument('--actor',choices=['principal','sentinel','respaldo','adapter'],default='adapter')
    parser.add_argument('--run-id',default='unspecified');args=parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,80}',args.run_id):raise SystemExit(2)
    if not args.execute_close:
        print(json.dumps({'estado':'QA_CLOSE_PREPARED_NOT_EXECUTED','sql_ejecutado':False}))
    else:
        try:report=close_report(actor=args.actor,run_id=args.run_id)
        except Exception:
            report={'estado':'QA_ACCOUNT_CLOSE_NOT_VERIFIED','closed':False,'actor':args.actor,'run_id':args.run_id,
                'phase':'PREPARE_ADAPTER','lock_attempted':None,'lock_acknowledged':None,'account_locked_verified':None,
                'app_sessions_zero_verified':None,'database_state':'DESCONOCIDO','failure_code':'DEPENDENCY_OR_CONTROL_ERROR',
                'manual_recovery_required':True,'utc':datetime.now(timezone.utc).isoformat()}
        print(json.dumps(report));raise SystemExit(0 if report['closed'] else 1)
