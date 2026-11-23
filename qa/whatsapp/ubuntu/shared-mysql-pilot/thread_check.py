"""Diagnostico QA autorizado: dos Thread.print, sin cambios de configuracion."""
import json, os, re, signal, subprocess, time
from pathlib import Path
from typing import NamedTuple

HERE = Path(__file__).resolve().parent
CONF = Path('/etc/dimabug-wa-pilot')
QA = 'dimabug-wa-pilot.service'
GRAPH = 'dimabug-wa-pilot-graph.service'
STATUS = HERE / 'thread-status.json'
JCMD = HERE / 'diagnostic-jdk/extracted/usr/lib/jvm/java-21-openjdk-amd64/bin/jcmd'
JAVA = '/usr/bin/java'
JAR = '/opt/dimabug-wa-pilot/whatsapp-qa.jar'
SERVICE_USER = 'dimabug-wa-pilot'
FIRST_CAPTURE_SECONDS = 95

def run(args, timeout=20, check=True):
    r = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError('COMMAND_FAILED_' + Path(args[0]).name.replace('-', '_'))
    return r.stdout.strip()

def prop(unit, key):
    return run(['systemctl', 'show', unit, '-p', key, '--value'])

def sql(query):
    r = subprocess.run(['mysql', '--protocol=socket', '--batch', '--skip-column-names'],
                       input=query, capture_output=True, text=True, timeout=15)
    if r.returncode:
        raise RuntimeError('QA_ACCOUNT_CONTROL_FAILED')
    return r.stdout.strip()

def identities():
    return {u: (prop(u, 'MainPID'), prop(u, 'NRestarts')) for u in
            ['mysql.service', 'nginx.service', 'dimabug@staging.service']}

def publish(result):
    STATUS.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    STATUS.chmod(0o644)

class ProcessIdentity(NamedTuple):
    pid: int
    start_ticks: int
    invocation: str

def validate_unit_identity(s, expected=None):
    identity = ProcessIdentity(s['pid'], s['start_ticks'], s['invocation'])
    if identity.pid <= 1 or (expected is not None and identity != expected):
        raise RuntimeError('QA_PID_CHANGED_OR_ABSENT')
    if not re.fullmatch('[a-f0-9]{32}', identity.invocation) or s['active'] != 'active':
        raise RuntimeError('QA_UNIT_NOT_ACTIVE')
    if s['unit_user'] != SERVICE_USER:
        raise RuntimeError('QA_SYSTEMD_USER_MISMATCH')
    if not s['group'] or s['cgroup'].splitlines() != ['0::' + s['group']]:
        raise RuntimeError('QA_CGROUP_IDENTITY_MISMATCH')
    return identity

def validate_identity(s, expected=None):
    """Pure fail-closed check; /proc ownership is NOT process credentials."""
    identity = validate_unit_identity(s, expected)
    if not s.get('stable', True):
        raise RuntimeError('QA_PROCESS_EXEC_TRANSITION')
    if s['uids'] != (s['expected_uid'],) * 4:
        raise RuntimeError('QA_UID_IDENTITY_MISMATCH')
    if s['exe'] != s['expected_exe']:
        raise RuntimeError('QA_JAVA_EXECUTABLE_MISMATCH')
    args = s['args']
    if not args or args[0] != JAVA.encode() or args.count(b'-jar') != 1:
        raise RuntimeError('QA_JAR_IDENTITY_MISMATCH')
    pos = args.index(b'-jar')
    if args[pos+1:pos+2] != [JAR.encode()]:
        raise RuntimeError('QA_JAR_IDENTITY_MISMATCH')
    credentials = '/run/credentials/' + QA
    if args[pos+2:] != [('--spring.config.location=file:' + credentials + '/application.properties').encode(),
                        ('--spring.config.import=configtree:' + credentials + '/').encode()]:
        raise RuntimeError('QA_CONFIG_ARGUMENTS_MISMATCH')
    return identity

def status_uids(text):
    matches = re.findall(r'(?m)^Uid:\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$', text)
    if len(matches) != 1:
        raise RuntimeError('QA_PROC_UID_STATUS_INVALID')
    return tuple(map(int, matches[0]))

def process_snapshot():
    import pwd
    pid = int(prop(QA, 'MainPID'))
    if pid <= 1:
        raise RuntimeError('QA_PID_CHANGED_OR_ABSENT')
    proc = Path('/proc') / str(pid)
    invocation = prop(QA, 'InvocationID')
    # starttime survives exec and detects reuse of the same numeric PID.
    def ticks():
        return int((proc / 'stat').read_text().rsplit(')', 1)[1].split()[19])
    first = ticks()
    raw = (proc / 'cmdline').read_bytes()
    s = dict(pid=pid, start_ticks=first, invocation=invocation,
             active=prop(QA, 'ActiveState'), unit_user=prop(QA, 'User'),
             uids=status_uids((proc / 'status').read_text()), expected_uid=pwd.getpwnam(SERVICE_USER).pw_uid,
             exe=str((proc / 'exe').resolve(strict=True)),
             expected_exe=str(Path(JAVA).resolve(strict=True)),
             args=raw.rstrip(b'\0').split(b'\0') if raw else [],
             group=prop(QA, 'ControlGroup'), cgroup=(proc / 'cgroup').read_text())
    s['stable'] = (raw == (proc / 'cmdline').read_bytes()
                   and s['exe'] == str((proc / 'exe').resolve(strict=True))
                   and s['uids'] == status_uids((proc / 'status').read_text()))
    if ticks() != first or prop(QA, 'MainPID') != str(pid) or prop(QA, 'InvocationID') != invocation:
        raise RuntimeError('QA_PROCESS_CHANGED_DURING_CHECK')
    return s

def verified_pid(expected=None):
    return validate_identity(process_snapshot(), expected)

def wait_for_java(snapshot=process_snapshot, clock=time.monotonic, sleep=time.sleep):
    """Type=simple may return before execve. A pre-exec child is never accepted."""
    deadline = clock() + 3
    initial = None
    while True:
        s = snapshot()
        current = validate_unit_identity(s, initial)
        if initial is None:
            initial = current
        try:
            return validate_identity(s)
        except RuntimeError as exc:
            # Retry only the initial systemd child, with correct unit/cgroup.
            # An unrelated Java executable/JAR is rejected immediately.
            transient = (str(exc) == 'QA_PROCESS_EXEC_TRANSITION' or
                         (s['exe'] in ('/usr/lib/systemd/systemd', '/lib/systemd/systemd')
                         and s['args'] in ([], [b'(java)'], [JAVA.encode()])
                         and len(s['uids']) == 4
                         and set(s['uids']) <= {0, s['expected_uid']}))
            if not transient or clock() >= deadline:
                raise
            sleep(0.1)

def safe_threads(raw):
    threads = []
    for block in re.split(r'(?m)^(?=")', raw):
        if not block.startswith('"'):
            continue
        state = re.search(r'java.lang.Thread.State: ([A-Z_]+)', block)
        frames = re.findall(r'(?m)^\s+at ([\w.$/]+\([^\r\n)]*\))\s*$', block)
        if state:
            threads.append({'main': block.startswith('"main"'), 'state': state.group(1),
                            'frames': frames[:35]})
    return threads

def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--approval')
    a = parser.parse_args()
    if not a.execute:
        print('PLAN_ONLY: dos capturas a 95s y 15s despues; corte 180s; parada y bloqueo QA')
        return
    if os.geteuid() != 0 or a.approval != 'DOS_THREAD_DUMPS_SOLO_QA':
        parser.error('Root y autorizacion exacta requeridos')
    for u in (QA, GRAPH):
        if prop(u, 'ActiveState') in ('active', 'activating', 'deactivating'):
            raise RuntimeError('QA_MUST_BE_STOPPED')
    if not JCMD.is_file():
        raise RuntimeError('JCMD_MISSING')
    config = (CONF / 'application.properties').read_text(encoding='utf-8')
    for flag in ('envio-real', 'medios', 'conexion-meta'):
        if not re.search(r'(?m)^dimabug\.whatsapp\.meta-qa\.' + flag + r'=false\s*$', config):
            raise RuntimeError('UNSAFE_QA_FEATURE_FLAGS')
    if sql("SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1'") != 'Y':
        raise RuntimeError('QA_ACCOUNT_MUST_BE_LOCKED')
    before = identities()
    marker = CONF / 'PILOT_APPROVED'
    if marker.exists():
        raise RuntimeError('UNEXPECTED_APPROVAL_MARKER')
    private = CONF / ('thread-dumps-' + time.strftime('%Y%m%d-%H%M%S', time.gmtime()))
    private.mkdir(mode=0o700)
    result = {'status': 'RUNNING', 'captures': [], 'events_sent': 0}
    publish(result)
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(RuntimeError('DIAGNOSTIC_TERMINATED')))
    def alarm(*_):
        raise RuntimeError('DIAGNOSTIC_180_SECONDS_LIMIT')
    signal.signal(signal.SIGALRM, alarm)
    try:
        run(['systemd-run', '--unit=dimabug-wa-thread-cutoff', '--on-active=180s',
             '/bin/sh', str(HERE / 'rollback.sh'), 'DETENER_Y_BLOQUEAR_SOLO_QA'])
        sql("ALTER USER 'wa_qa_pilot'@'127.0.0.1' ACCOUNT UNLOCK;")
        marker.touch(mode=0o600, exist_ok=False)
        start = time.monotonic()
        signal.alarm(175)
        run(['systemctl', 'start', QA])
        pid = wait_for_java()
        first_time = None
        for number in (1, 2):
            target = start + FIRST_CAPTURE_SECONDS if number == 1 else first_time + 15
            while time.monotonic() < target:
                verified_pid(pid)
                avail = int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines()
                                 if l.startswith('MemAvailable:'))) / 1024
                result['available_min_mib'] = min(result.get('available_min_mib', avail), avail)
                if avail < 1024:
                    raise RuntimeError('MEMAVAILABLE_BELOW_1_GIB')
                time.sleep(min(2, max(0, target - time.monotonic())))
            verified_pid(pid)
            when = time.monotonic()
            if first_time is None:
                first_time = when
            # Exact PID only, Thread.print only: no VM.command_line or properties.
            raw = run([str(JCMD), str(pid.pid), 'Thread.print'], timeout=12)
            path = private / ('threads-' + str(number) + '.txt')
            path.write_text(raw, encoding='utf-8'); path.chmod(0o600)
            threads = safe_threads(raw)
            if not threads:
                raise RuntimeError('EMPTY_THREAD_CAPTURE')
            result['captures'].append({'number': number, 'elapsed_seconds': round(when-start, 2),
                                       'threads': threads})
            publish(result)
        result['status'] = 'CAPTURED'
    except Exception as e:
        msg = str(e)
        result.update(status='FAIL', error_code=msg if re.fullmatch('[A-Z0-9_]+', msg) else type(e).__name__)
    finally:
        signal.alarm(0)
        try:
            run(['systemctl', 'stop', QA, GRAPH], timeout=100, check=False)
        finally:
            marker.unlink(missing_ok=True)
            result['qa_account_locked'] = sql("ALTER USER 'wa_qa_pilot'@'127.0.0.1' ACCOUNT LOCK; SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1';") == 'Y'
        result['qa_stopped'] = all(prop(u, 'MainPID') == '0' and prop(u, 'ActiveState') not in
                                  ('active', 'activating', 'deactivating') for u in (QA, GRAPH))
        result['existing_services_unchanged'] = identities() == before
        if result['qa_stopped'] and result['qa_account_locked']:
            run(['systemctl', 'stop', 'dimabug-wa-thread-cutoff.timer'], check=False)
        if not all(result[k] for k in ('qa_stopped', 'qa_account_locked', 'existing_services_unchanged')):
            result['status'] = 'FAIL'
        publish(result)
    print('Diagnostico terminado; resultado seguro en thread-status.json')
    if result['status'] != 'CAPTURED':
        raise SystemExit(1)

if __name__ == '__main__':
    main()
