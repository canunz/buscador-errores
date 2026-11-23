"""Reuses the independent Windows cleanup sentinel; default inert."""
import argparse
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import ROOT,require


def execute(folder,parent):
    sys.path.insert(0,str(ROOT/'qa/backend-tls'))
    from cleanup_sentinel import run,emit
    from repair_cleanup import close_maintenance
    folder=Path(folder).resolve(strict=True)
    require(folder.is_relative_to(ROOT/'qa/runtime/backend-runs'),'REPAIR_SENTINEL_PATH')
    context=json.loads((folder/'repair-context.json').read_text(encoding='utf-8'))
    history=Path(context['historical_backup']).resolve(strict=True)
    require(history.is_relative_to(ROOT/'qa/runtime/fixture-runs'),'REPAIR_SENTINEL_HISTORY')
    uuid=json.loads(history.read_text(encoding='utf-8'))['server_uuid']
    closed=run(folder,parent,close_action=lambda:close_maintenance(uuid))
    # A missing worker report can never imply rollback or success.
    directory=Path(context['directory']).resolve(strict=True)
    require(directory.is_relative_to(ROOT/'qa/runtime/fixture-runs'),'REPAIR_SENTINEL_DIRECTORY')
    if not (directory/'repair-result.json').exists():
        emit(folder,'orphan-result.json',{'transaction':'UNKNOWN','security':'VERIFIED' if closed else 'UNKNOWN',
            'automatic_retry':False,'manual_review_required':True})
    return closed


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--run-authorized-window',action='store_true')
    parser.add_argument('--folder');parser.add_argument('--parent',type=int);args=parser.parse_args()
    if not args.run_authorized_window:print('{"status":"QA_REPAIR_SENTINEL_PREPARED_NO_SQL"}')
    else:
        try:raise SystemExit(0 if execute(args.folder,args.parent) else 1)
        except Exception:print('{"status":"QA_REPAIR_CLOSE_UNKNOWN","details_suppressed":true}');raise SystemExit(1)
