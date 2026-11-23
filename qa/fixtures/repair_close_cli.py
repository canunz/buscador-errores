"""Explicit future principal cleanup adapter; default inert."""
import argparse
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import ROOT,require


def execute(folder):
    from repair_cleanup import close_maintenance
    folder=Path(folder).resolve(strict=True)
    require(folder.is_relative_to(ROOT/'qa/runtime/backend-runs'),'REPAIR_CLOSE_FOLDER')
    context=json.loads((folder/'repair-context.json').read_text(encoding='utf-8'))
    history=Path(context['historical_backup']).resolve(strict=True)
    require(history.is_relative_to(ROOT/'qa/runtime/fixture-runs'),'REPAIR_CLOSE_HISTORY')
    uuid=json.loads(history.read_text(encoding='utf-8'))['server_uuid']
    return close_maintenance(uuid)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--execute-close',action='store_true');parser.add_argument('--folder');args=parser.parse_args()
    if not args.execute_close:print('{"status":"QA_REPAIR_CLOSE_PREPARED_NO_SQL"}')
    else:
        try:
            closed=execute(args.folder)
            print(json.dumps({'closed':closed,'security':'VERIFIED' if closed else 'UNKNOWN'}))
            raise SystemExit(0 if closed else 1)
        except Exception:print('{"closed":false,"security":"UNKNOWN","details_suppressed":true}');raise SystemExit(1)
