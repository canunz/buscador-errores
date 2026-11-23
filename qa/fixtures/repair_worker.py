"""Future explicitly authorized worker. Default invocation is inert."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import ROOT, require, fingerprint


def persist_exclusive(path, document):
    require(not path.exists(),'REPAIR_EVIDENCE_EXISTS')
    temporary=path.with_suffix(path.suffix+'.pending')
    with temporary.open('x',encoding='utf-8') as output:
        json.dump(document,output,indent=2)
        output.flush();os.fsync(output.fileno())
    # Windows rename refuses an existing destination. Never overwrite evidence.
    os.rename(temporary,path)


def execute(folder):
    from encoding_executor import run
    from encoding_controls import RecoveryControls
    from repair_lifecycle import Lifecycle
    from prepare_encoding_repair import prepare
    folder=Path(folder).resolve(strict=True)
    require(folder.is_relative_to(ROOT/'qa/runtime/backend-runs'),'REPAIR_WORKER_PATH')
    config=json.loads((folder/'repair-context.json').read_text(encoding='utf-8'))
    historical=Path(config['historical_backup']).resolve(strict=True)
    require(historical.is_relative_to(ROOT/'qa/runtime/fixture-runs'),'REPAIR_HISTORY_PATH')
    history=json.loads(historical.read_text(encoding='utf-8'))
    lifecycle=Lifecycle(folder,config['parent'],config['sentinel'],history['server_uuid'])
    controls=RecoveryControls(config['candidate'],config['directory'],historical,lifecycle)
    plan=prepare()[1]
    reviewed=json.loads((ROOT/'qa/fixtures/encoding-repair-v1-to-v2.json').read_text(encoding='utf-8'))
    require(plan==reviewed,'REPAIR_PLAN_CHANGED')
    # preflight lazily makes tls_support importable before factory is invoked.
    from repair_transport import factory
    result=run(factory,controls,plan)
    result.update({'candidate':config['candidate'],'server_uuid':history['server_uuid'],
        'plan_sha256':hashlib.sha256(json.dumps(plan,sort_keys=True).encode('utf-8')).hexdigest(),
        'fixture_sha256':fingerprint('mvp-qa-v2')})
    path=Path(config['directory'])/'repair-result.json'
    if result['success']:
        backup=json.loads((path.parent/'backup.json').read_text(encoding='utf-8'))
        from fixture_tools import schema_hashes,load
        # Independent verification already completed in run. Schema hash is
        # captured in controls using the same exact schema hashing implementation.
        receipt={'format':'dimabug-qa-fixture-receipt-v1','version':'mvp-qa-v2',
            'fixture_sha256':fingerprint('mvp-qa-v2'),'server_uuid':history['server_uuid'],
            'schema_sha256':controls.schema,'data_sha256':result['data_sha256'],
            'repair_candidate':config['candidate'],'repair_result':'repair-result.json',
            'backup_sha256':backup['backup_sha256'],'previous_failure_preserved':True}
        # Only the principal may promote this receipt after its own close AND
        # the independent sentinel close are confirmed.
        persist_exclusive(path.parent/'receipt.prepared.json',receipt)
    persist_exclusive(path,result)
    return result['success']


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--run-authorized-repair',action='store_true')
    parser.add_argument('--folder')
    args=parser.parse_args()
    if not args.run_authorized_repair:print('{"status":"QA_REPAIR_WORKER_PREPARED_NO_SQL"}')
    else:
        try:raise SystemExit(0 if execute(args.folder) else 1)
        except Exception:
            print('{"status":"QA_REPAIR_RESULT_UNKNOWN","details_suppressed":true,"automatic_retry":false}')
            raise SystemExit(1)
