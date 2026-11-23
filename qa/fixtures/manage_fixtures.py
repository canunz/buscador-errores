"""Default offline plan. Explicit future execution only; never unlocks accounts or changes grants/DDL."""
import argparse,hashlib,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import *

def execute(phase,directory,hashes_path=None,receipt_path=None):
    directory=Path(directory).resolve(strict=True)
    require(directory.is_relative_to(ROOT/'qa/runtime/fixture-runs'),'FIXTURE_PRIVATE_DIRECTORY')
    if phase!='verify':require(not (directory/'receipt.json').exists(),'FIXTURE_RECEIPT_EXISTS_NO_RETRY')
    backup=None
    receipt_version=None
    if receipt_path is not None:
        private_receipt=Path(receipt_path).resolve(strict=True)
        require(private_receipt.is_relative_to(ROOT/'qa/runtime/fixture-runs'),'FIXTURE_RECEIPT_PATH')
        receipt_version=json.loads(private_receipt.read_text(encoding='utf-8'))['version']
        if phase=='reset':require(receipt_version=='mvp-qa-v2','FIXTURE_V1_REQUIRES_TARGETED_REPAIR')
    if phase!='verify':
        backup=json.loads((directory/'backup.json').read_text(encoding='utf-8'))
        require(backup['status']=='QA_FIXTURE_BACKUP_VERIFIED' and backup['fixture_sha256']==fingerprint() and
                hashlib.sha256((directory/'backup.sql').read_bytes()).hexdigest()==backup['backup_sha256'],'FIXTURE_BACKUP_REQUIRED')
    sys.path.insert(0,str(ROOT/'qa/mysql/tls'))
    from run_privilege_test import preflight
    from tls_support import sql
    if backup is not None:require(sql('SELECT @@server_uuid;')==backup['server_uuid'],'FIXTURE_BACKUP_INSTANCE')
    data=load(receipt_version) if receipt_version is not None else load()
    if phase=='load':preflight()
    else:
        require(receipt_path is not None,'FIXTURE_RECEIPT_REQUIRED')
        preflight(fixture_receipt=receipt_path,fixture_reset=phase=='reset')
    if phase=='verify':return {'status':'QA_FIXTURE_LIVE_VERIFIED','version':data['version']}
    require(hashes_path is not None,'FIXTURE_HASHES_REQUIRED')
    hashes_file=Path(hashes_path).resolve(strict=True)
    require(hashes_file.parent==directory,'FIXTURE_HASHES_PRIVATE_PATH')
    hashes=json.loads(hashes_file.read_text(encoding='utf-8'));transaction=sql_transaction(data,hashes,reset=phase=='reset')
    # A single connection/transaction, bounded to synthetic data; no SQL/passwords in args or reports.
    sql(transaction)
    actual=read_rows(sql,data);assert_rows(data,actual)
    receipt={'format':'dimabug-qa-fixture-receipt-v1','version':data['version'],'fixture_sha256':fingerprint(),
        'server_uuid':sql('SELECT @@server_uuid;'),'schema_sha256':schema_hashes(sql,data),
        'data_sha256':{t:hashlib.sha256('\n'.join(canonical_rows(rows)).encode()).hexdigest() for t,rows in actual.items()}}
    target=directory/'receipt.json'
    with target.open('x',encoding='utf-8') as f:f.write(json.dumps(receipt,indent=2)+'\n')
    return {'status':'QA_FIXTURE_'+phase.upper()+'_VERIFIED','version':data['version'],'rows':sum(map(len,data['rows'].values()))}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('phase',choices=['load','reset','verify','plan'],default='plan',nargs='?')
    p.add_argument('--execute',action='store_true');p.add_argument('--directory');p.add_argument('--hashes');p.add_argument('--receipt');a=p.parse_args()
    try:
        if not a.execute:
            d=load();print(json.dumps({'status':'QA_FIXTURE_PLAN_ONLY','version':d['version'],'rows':sum(map(len,d['rows'].values())),'sql_executed':False}));raise SystemExit(0)
        require(a.phase!='plan','FIXTURE_EXECUTION_PHASE_REQUIRED')
        print(json.dumps(execute(a.phase,a.directory,a.hashes,a.receipt)))
    except Exception:
        print('{"status":"QA_FIXTURE_OPERATION_NOT_VERIFIED","details_suppressed":true,"automatic_retry":false}');raise SystemExit(1)
