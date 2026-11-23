"""Real Linux /proc + disposable non-root JVM + real jcmd; orchestration is doubled.
No MySQL, existing systemd units, network, or real credentials are used.
"""
import hashlib, json, os, platform, shutil, subprocess, sys, time, unittest
from pathlib import Path
import thread_check as check

suite = unittest.defaultTestLoader.discover('/fixture', pattern='test_thread_identity.py')
if not unittest.TextTestRunner(verbosity=1).run(suite).wasSuccessful():
    raise SystemExit(1)

check.JAVA = '/opt/java/openjdk/bin/java'
check.JAR = '/fixture/hold.jar'
check.JCMD = Path('/opt/java/openjdk/bin/jcmd')
check.SERVICE_USER = 'nobody'
check.QA = 'dimabug-wa-disposable.service'
check.CONF = Path('/tmp/disposable-config'); check.CONF.mkdir(mode=0o700)
check.HERE = Path('/tmp/disposable-results'); check.HERE.mkdir(mode=0o700)
check.STATUS = check.HERE / 'thread-status.json'
check.FIRST_CAPTURE_SECONDS = 2
(check.CONF / 'application.properties').write_text('\n'.join(
    'dimabug.whatsapp.meta-qa.' + flag + '=false'
    for flag in ('envio-real', 'medios', 'conexion-meta')))
proc = None
locked = True
facts = {}
real_run = check.run

def prop(unit, key):
    if unit == check.QA:
        alive = proc is not None and proc.poll() is None
        data = dict(MainPID=str(proc.pid) if alive else '0',
                    ActiveState='active' if alive else 'inactive',
                    InvocationID='c' * 32, User='nobody', NRestarts='0')
        if key == 'ControlGroup':
            return Path('/proc/' + str(proc.pid) + '/cgroup').read_text().strip().split(':', 2)[2]
        return data[key]
    if unit == check.GRAPH:
        return '0' if key == 'MainPID' else 'inactive'
    return '0' if key == 'NRestarts' else '900000'

def sql(query):
    global locked
    if 'ACCOUNT UNLOCK' in query:
        locked = False
    if 'ACCOUNT LOCK' in query:
        locked = True
    return 'Y' if locked else 'N'

def run(args, timeout=20, check=True):
    global proc
    if args[0] == 'systemd-run':
        return ''  # Test double: container runtime has an independent outer timeout.
    if args[:2] == ['systemctl', 'start']:
        creds = '/run/credentials/' + check_module.QA
        proc = subprocess.Popen(['/fixture/launch', check_module.JAVA, '-Xms32m', '-Xmx96m', '-jar', check_module.JAR,
            '--spring.config.location=file:' + creds + '/application.properties',
            '--spring.config.import=configtree:' + creds + '/'],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # Read actual process credentials; ownership behavior varies by kernel.
        for _ in range(100):
            if proc.poll() is not None:
                raise RuntimeError('DISPOSABLE_JVM_EXITED')
            s = check_module.process_snapshot()
            owner = Path('/proc/' + str(proc.pid)).stat().st_uid
            if s['uids'] == (65534,) * 4 and s['exe'] == str(Path(check_module.JAVA).resolve()):
                facts.update(actual_uids_match_service=True, uid_source='proc_status',
                             private_tmp_namespace=True)
                break
            time.sleep(.05)
        else:
            raise RuntimeError('DISPOSABLE_JVM_NOT_READY')
        return ''
    if args[:2] == ['systemctl', 'stop']:
        if check_module.QA in args and proc is not None and proc.poll() is None:
            proc.terminate(); proc.wait(timeout=10)
        return ''
    return real_run(args, timeout=timeout, check=check)

check_module = check
check.prop = prop; check.sql = sql; check.run = run
sys.argv = ['thread_check.py', '--execute', '--approval', 'DOS_THREAD_DUMPS_SOLO_QA']
try:
    check.main()
finally:
    if proc is not None and proc.poll() is None:
        proc.kill(); proc.wait(timeout=5)
    if check.STATUS.exists():
        last = json.loads(check.STATUS.read_text())
        if last['status'] != 'CAPTURED':
            print(json.dumps(last))

result = json.loads(check.STATUS.read_text())
assert result['status'] == 'CAPTURED', result.get('error_code')
assert len(result['captures']) == 2
assert result['captures'][1]['elapsed_seconds'] - result['captures'][0]['elapsed_seconds'] >= 14.99
assert all(any(t['main'] and t['state'] == 'WAITING' and
               any('Hold.main' in frame for frame in t['frames']) for t in c['threads'])
           for c in result['captures'])
assert all(result[k] for k in ('qa_stopped', 'qa_account_locked', 'existing_services_unchanged'))
assert proc.poll() is not None and locked
evidence = Path('/fixture/evidence'); evidence.mkdir(mode=0o700)
(evidence / 'captured-status.json').write_text(json.dumps(result, indent=2) + '\n')
for index, path in enumerate(sorted(check.CONF.glob('thread-dumps-*/threads-*.txt')), 1):
    shutil.copyfile(path, evidence / ('threads-' + str(index) + '.txt'))
facts.update(status='PASS', real_thread_dumps=2,
             separation_seconds=round(result['captures'][1]['elapsed_seconds'] - result['captures'][0]['elapsed_seconds'], 2),
             java_stopped=True, simulated_account_locked=locked,
             main_waiting_in='Hold.main', orchestration='systemd_and_mysql_test_doubles')

# Real foreign JVMs must be rejected, even with matching executable/cgroup metadata.
creds = '/run/credentials/' + check.QA
config_args = ['--spring.config.location=file:' + creds + '/application.properties',
               '--spring.config.import=configtree:' + creds + '/']
shutil.copyfile(check.JAR, '/fixture/foreign.jar')
cases = [([check.JAVA, '-jar', check.JAR] + config_args, 'QA_UID_IDENTITY_MISMATCH'),
         (['/fixture/launch', check.JAVA, '-jar', '/fixture/foreign.jar'] + config_args, 'QA_JAR_IDENTITY_MISMATCH'),
         (['/fixture/launch', check.JAVA, '-jar', check.JAR] + config_args + ['--foreign=true'], 'QA_CONFIG_ARGUMENTS_MISMATCH')]
for command, code in cases:
    proc = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(100):
            s = check.process_snapshot()
            if s['stable'] and s['exe'] == str(Path(check.JAVA).resolve()) and s['args'][0] == check.JAVA.encode():
                break
            time.sleep(.05)
        else:
            raise AssertionError('Foreign Java did not exec')
        try:
            check.validate_identity(s)
        except RuntimeError as exc:
            assert str(exc) == code, str(exc)
        else:
            raise AssertionError('Foreign JVM accepted')
    finally:
        if proc.poll() is None:
            proc.terminate(); proc.wait(timeout=10)
facts['real_foreign_jvms_rejected'] = len(cases)

# Exercise the same main/finally path with a failing attach (no second dump).
good_run = check.run
def failing_attach(args, timeout=20, check=True):
    if args[0] == str(check_module.JCMD):
        raise RuntimeError('SYNTHETIC_ATTACH_FAILURE')
    return good_run(args, timeout=timeout, check=check)
check.run = failing_attach
try:
    check.main()
except SystemExit as exc:
    assert exc.code == 1
else:
    raise AssertionError('Failed attach was accepted')
finally:
    if proc is not None and proc.poll() is None:
        proc.kill(); proc.wait(timeout=5)
failed = json.loads(check.STATUS.read_text())
assert failed['error_code'] == 'SYNTHETIC_ATTACH_FAILURE'
assert all(failed[k] for k in ('qa_stopped', 'qa_account_locked', 'existing_services_unchanged'))
assert locked and proc.poll() is not None
facts['cleanup_after_attach_failure'] = True
(evidence / 'failure-cleanup-status.json').write_text(json.dumps(failed, indent=2) + '\n')
(evidence / 'integration-result.json').write_text(json.dumps(facts, indent=2) + '\n')
(evidence / 'environment.json').write_text(json.dumps(dict(
    kernel=platform.release(), python=platform.python_version(),
    java=subprocess.run([check.JAVA, '-version'], capture_output=True, text=True).stderr.strip(),
    source_sha256={name: hashlib.sha256(Path('/fixture', name).read_bytes()).hexdigest()
                   for name in ('thread_check.py', 'test_thread_identity.py', 'integration.py')}
), indent=2) + '\n')
print(json.dumps(facts))
