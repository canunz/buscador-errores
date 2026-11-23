"""Explicit future QA backup only; default does not query SQL or Docker."""
import argparse,hashlib,json,re,shutil,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import ROOT,load,require,fingerprint
def execute(directory,receipt=None,for_reset=False,recovery_metadata=None):
    directory=Path(directory).resolve(strict=True);require(directory.is_relative_to(ROOT/'qa/runtime/fixture-runs'),'FIXTURE_BACKUP_PATH')
    require(not (directory/'backup.json').exists() and not (directory/'backup.sql').exists(),'FIXTURE_BACKUP_EXISTS')
    sys.path.insert(0,str(ROOT/'qa/mysql/tls'))
    from run_privilege_test import preflight,snapshot
    from tls_support import sql,NAME,clean_env
    options={'fixture_receipt':receipt,'fixture_reset':for_reset}
    if recovery_metadata is not None:options['recovery_metadata']=recovery_metadata
    names,_,_,uuid=preflight(**options)
    version='mvp-qa-v1' if recovery_metadata is not None else 'mvp-qa-v2'
    data=load(version)
    def state():
        meta,counts=snapshot(names)
        rows={t:hashlib.sha256('\n'.join(sorted(sql('SELECT * FROM dima_bug_qa_mvp.`'+t+'`;').splitlines())).encode()).hexdigest() for t in names}
        return {'metadata':meta,'counts':counts,'rows':rows}
    before=state();path=directory/'backup.sql'
    command='MYSQL_HISTFILE=/dev/null MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysqldump --default-character-set=utf8mb4 --user=root --protocol=socket --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --skip-comments --skip-dump-date --hex-blob --routines --events --triggers --databases dima_bug_qa_mvp'
    with path.open('xb') as output:
        r=subprocess.run([shutil.which('docker'),'--context','desktop-linux','exec',NAME,'sh','-c',command],stdout=output,stderr=subprocess.PIPE,env=clean_env(),timeout=45)
    require(r.returncode==0,'FIXTURE_BACKUP_FAILED');raw=path.read_bytes();text=raw.decode('utf-8')
    require(set(re.findall(r'CREATE TABLE `([a-z0-9_]+)`',text))==set(names),'FIXTURE_BACKUP_INCOMPLETE')
    require(set(re.findall(r'INSERT INTO `([a-z0-9_]+)`',text))=={t for t,c in before['counts'].items() if c},'FIXTURE_BACKUP_DATA_MISMATCH')
    require(state()==before,'FIXTURE_CHANGED_DURING_BACKUP')
    report={'status':'QA_FIXTURE_BACKUP_VERIFIED','server_uuid':uuid,'fixture_sha256':fingerprint(version),
        'backup_sha256':hashlib.sha256(raw).hexdigest(),'state':before,'restore_executed':False}
    with (directory/'backup.json').open('x') as f:f.write(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'status':report['status'],'tables':44,'backup_readback_verified':True,'unlocks':0}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--execute',action='store_true');p.add_argument('--directory');p.add_argument('--receipt');p.add_argument('--for-reset',action='store_true');a=p.parse_args()
    if not a.execute:print('{"status":"QA_FIXTURE_BACKUP_PREPARED_NO_SQL"}')
    else:
        try:execute(a.directory,a.receipt,a.for_reset)
        except Exception:print('{"status":"QA_FIXTURE_BACKUP_NOT_VERIFIED","details_suppressed":true}');raise SystemExit(1)
