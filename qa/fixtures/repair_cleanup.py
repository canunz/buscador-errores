"""QA-only cleanup. No SQL at import; invoked only in a later authorized run."""
import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'mysql/tls'))


def close_maintenance(uuid, query=None, docker_call=None):
    from tls_support import sql, docker, NAME, close_window
    query = sql if query is None else query
    docker_call = docker if docker_call is None else docker_call
    # Verify container identity BEFORE running root maintenance commands.
    endpoint=json.loads(docker_call(['context','inspect','desktop-linux','--format','{{json .Endpoints.docker.Host}}']))
    if not endpoint.startswith('npipe://'):raise RuntimeError('REPAIR_NONLOCAL_ENGINE')
    image=json.loads(docker_call(['inspect','--format','{{json .Config.Image}}',NAME]))
    if image!='mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b':raise RuntimeError('REPAIR_IMAGE')
    mounts=json.loads(docker_call(['inspect','--format','{{json .Mounts}}',NAME]))
    if not any(item.get('Name')=='dimabug-qa-mvp-13318-mysql-data' and item.get('Destination')=='/var/lib/mysql' for item in mounts):raise RuntimeError('REPAIR_VOLUME')
    networks=json.loads(docker_call(['inspect','--format','{{json .NetworkSettings.Networks}}',NAME]))
    if set(networks)!={'dimabug-qa-mvp-13318-net'}:raise RuntimeError('REPAIR_NETWORK')
    if query('SELECT @@server_uuid;')!=uuid:raise RuntimeError('REPAIR_UUID')
    # Only the repair transport selects this database as its default. Other root
    # sessions are not killed. The ordinary preflight requires zero other clients.
    sessions=query("SELECT ID FROM information_schema.PROCESSLIST WHERE USER='root' AND DB='dima_bug_qa_mvp' AND ID<>CONNECTION_ID();")
    for identifier in sessions.splitlines():
        if not identifier.isdecimal():raise RuntimeError('REPAIR_SESSION_ID')
        try:query('KILL CONNECTION '+str(int(identifier))+';')
        except RuntimeError as error:
            if str(error)!='SQL_ERROR_1094':raise
    query("ALTER USER 'dimabug_qa_schema'@'localhost' ACCOUNT LOCK;")
    if close_window(query=query) is not True:raise RuntimeError('REPAIR_ACCOUNTS_NOT_CONFIRMED')
    if query("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER IN ('dimabug_qa_app','dimabug_qa_schema') OR (USER='root' AND DB='dima_bug_qa_mvp');")!='0':raise RuntimeError('REPAIR_SESSIONS_REMAIN')
    return True
