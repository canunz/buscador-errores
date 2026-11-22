"""Proposed manual read-only probe. Never starts/stops units, signals, attaches, or writes.
No imports from server scripts; no environment, config secrets, payloads or raw argv output.
"""
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import subprocess
import time

UNIT = 'dimabug-wa-pilot.service'
USER = 'dimabug-wa-pilot'
JAR = b'/opt/dimabug-wa-pilot/whatsapp-qa.jar'
ROOT = Path('/home/carolina/wa_durable_0eb3331f3918')

def read_unit():
    keys = ['Type', 'User', 'MainPID', 'ActiveState', 'InvocationID', 'ControlGroup',
            'ExecStart', 'FragmentPath', 'DropInPaths', 'PrivateTmp', 'ProtectHome',
            'MemoryMax', 'MemoryHigh', 'MemorySwapMax', 'CPUQuotaPerSecUSec',
            'TasksMax', 'TimeoutStartUSec', 'TimeoutStopUSec', 'Restart',
            'StandardOutput', 'StandardError', 'LogRateLimitBurst', 'LogRateLimitIntervalUSec']
    args = ['systemctl', 'show', UNIT] + ['--property=' + key for key in keys]
    out = subprocess.check_output(args, text=True, timeout=10)
    return dict(line.split('=', 1) for line in out.splitlines() if '=' in line)

def safe_status():
    path = ROOT / 'thread-status.json'
    if not path.exists():
        return {'available': False}
    data = json.loads(path.read_text())
    safe = {'available': True, 'captures_count': len(data.get('captures', []))}
    for key in ['status', 'error_code']:
        value = data.get(key)
        if isinstance(value, str) and re.fullmatch('[A-Z0-9_]+', value):
            safe[key] = value
    for key in ['qa_stopped', 'qa_account_locked', 'existing_services_unchanged']:
        if type(data.get(key)) is bool:
            safe[key] = data[key]
    return safe

def live_sample():
    first = read_unit()
    pid = int(first.get('MainPID', '0'))
    if pid <= 1:
        return {'result': 'NOT_OBSERVABLE_QA_STOPPED', 'pid_present': False}
    proc = Path('/proc') / str(pid)
    def ticks():
        return (proc / 'stat').read_text().rsplit(')', 1)[1].split()[19]
    start = ticks()
    raw = (proc / 'cmdline').read_bytes()
    exe = str((proc / 'exe').resolve(strict=True))
    status = (proc / 'status').read_text()
    match = re.search(r'(?m)^Uid:\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$', status)
    if not match:
        return {'result': 'PROC_UID_STATUS_INVALID'}
    uids = tuple(map(int, match.groups()))
    expected_uid = pwd.getpwnam(USER).pw_uid
    owner = proc.stat().st_uid
    cgroup = (proc / 'cgroup').read_text().splitlines()
    stable_proc = (start == ticks() and raw == (proc / 'cmdline').read_bytes()
                   and exe == str((proc / 'exe').resolve(strict=True))
                   and re.search(r'(?m)^Uid:.*$', status).group() ==
                       re.search(r'(?m)^Uid:.*$', (proc / 'status').read_text()).group())
    last = read_unit()
    stable = stable_proc and all(first.get(k) == last.get(k)
                                  for k in ['MainPID', 'InvocationID', 'ControlGroup', 'User'])
    args = raw.rstrip(b'\0').split(b'\0') if raw else []
    pos = args.index(b'-jar') if args.count(b'-jar') == 1 else -1
    credentials = '/run/credentials/' + UNIT
    checks = dict(
        unit_user_matches=first.get('User') == USER,
        uid_real_matches=uids[0] == expected_uid,
        uid_effective_matches=uids[1] == expected_uid,
        uid_saved_matches=uids[2] == expected_uid,
        uid_filesystem_matches=uids[3] == expected_uid,
        cgroup_matches=bool(first.get('ControlGroup')) and cgroup == ['0::' + first['ControlGroup']],
        java_executable_matches=exe == str(Path('/usr/bin/java').resolve(strict=True)),
        java_argv0_matches=bool(args) and args[0] == b'/usr/bin/java',
        jar_matches=pos >= 0 and args[pos+1:pos+2] == [JAR],
        config_arguments_match=pos >= 0 and args[pos+2:] == [
            ('--spring.config.location=file:' + credentials + '/application.properties').encode(),
            ('--spring.config.import=configtree:' + credentials + '/').encode()])
    failed = [name for name, passed in checks.items() if not passed]
    return dict(result='STATE_CHANGED_DURING_READ' if not stable else ('MATCH' if not failed else 'MISMATCH'),
                stable=stable, proc_owner_matches_expected=owner == expected_uid,
                checks=checks, first_incompatible=failed[0] if stable and failed else None)

def main():
    if os.geteuid() != 0:
        raise SystemExit('ROOT_REQUIRED_FOR_READ_ONLY_PROC_ACCESS')
    unit = read_unit()
    # ExecStart is inspected in memory, never printed (could contain private arguments).
    launch = unit.pop('ExecStart', '')
    unit.pop('InvocationID', None)
    unit.pop('MainPID', None)
    safe_unit_keys = ['Type', 'ActiveState', 'PrivateTmp', 'ProtectHome', 'MemoryMax',
                      'MemoryHigh', 'MemorySwapMax', 'CPUQuotaPerSecUSec', 'TasksMax',
                      'TimeoutStartUSec', 'TimeoutStopUSec', 'Restart', 'StandardOutput',
                      'StandardError', 'LogRateLimitBurst', 'LogRateLimitIntervalUSec']
    result = dict(unit={k: unit[k] for k in safe_unit_keys if k in unit},
                  unit_user_matches=unit.get('User') == USER,
                  execstart_java_matches='path=/usr/bin/java ;' in launch,
                  execstart_jar_matches=bool(re.search(r'\s-jar /opt/dimabug-wa-pilot/whatsapp-qa\.jar(?:\s|;)', launch)),
                  dropins_present=bool(unit.get('DropInPaths')), previous_result=safe_status())
    script = ROOT / 'thread_check.py'
    result['remote_script_sha256'] = hashlib.sha256(script.read_bytes()).hexdigest() if script.exists() else None
    result['samples'] = []
    for index in range(3):
        try:
            sample = live_sample()
        except (FileNotFoundError, ProcessLookupError):
            sample = {'result': 'PROCESS_DISAPPEARED_DURING_READ'}
        except (PermissionError, KeyError):
            sample = {'result': 'PROC_ACCESS_OR_SERVICE_USER_UNAVAILABLE'}
        result['samples'].append(sample)
        if sample.get('result') == 'NOT_OBSERVABLE_QA_STOPPED':
            break
        if index < 2:
            time.sleep(.25)
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError):
        raise SystemExit('READ_ONLY_PROBE_FAILED_NO_PRIVATE_DETAILS')
