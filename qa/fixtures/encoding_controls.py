"""Recovery bridge; imported safely. No live CLI or default lifecycle callbacks.

Watchdog/process/ACL checks must be supplied by the reviewed Windows supervisor.
The regular fixture load command must never invoke this bridge automatically.
"""
import hashlib
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from fixture_tools import ROOT, require, schema_hashes, load
from encoding_executor import verify_candidate


class RecoveryControls:
    def __init__(self, candidate, directory, historical_backup, lifecycle):
        self.candidate_id = candidate
        self.directory = Path(directory).resolve(strict=True)
        history = Path(historical_backup).resolve(strict=True)
        base = ROOT / 'qa/runtime/fixture-runs'
        require(self.directory.is_relative_to(base) and history.is_relative_to(base), 'REPAIR_PRIVATE_PATH')
        old = json.loads(history.read_text(encoding='utf-8'))
        self.metadata = {k:v for k,v in old['state']['metadata'].items() if k!='counts'}
        self.uuid = old['server_uuid']
        self.lifecycle = lifecycle
        self.names = None

    def candidate(self):
        verify_candidate(ROOT, self.candidate_id)
        self.lifecycle.check()
        self.lifecycle.assert_private_directory(self.directory)
        self.lifecycle.assert_apps_stopped()

    def preflight(self):
        sys.path.insert(0, str(ROOT / 'qa/mysql/tls'))
        from run_privilege_test import preflight
        names, metadata, _, uuid = preflight(recovery_metadata=self.metadata)
        require(uuid == self.uuid, 'REPAIR_INSTANCE')
        self.names = names
        return {'uuid': uuid, 'metadata': metadata}

    def backup(self, baseline):
        from preflight_backup import execute
        execute(self.directory, recovery_metadata=self.metadata)
        receipt = json.loads((self.directory/'backup.json').read_text(encoding='utf-8'))
        require(receipt['server_uuid']==baseline['uuid'] and receipt['state']['metadata']==baseline['metadata'], 'REPAIR_BACKUP_BASELINE')
        require(hashlib.sha256((self.directory/'backup.sql').read_bytes()).hexdigest()==receipt['backup_sha256'], 'REPAIR_BACKUP_DIGEST')

    def arm(self):
        self.lifecycle.arm_both_watchdogs()
        self.lifecycle.check()
        # Exclusive durable marker BEFORE any transaction connection; never reused.
        with (self.directory/'repair-attempt.json').open('x', encoding='utf-8') as file:
            json.dump({'candidate':self.candidate_id,'automatic_retry':False},file)
            file.flush()
            import os
            os.fsync(file.fileno())

    def check(self): self.lifecycle.check()

    def inspect(self, session, baseline):
        require(session.query('SELECT @@server_uuid;')==baseline['uuid'], 'REPAIR_INSTANCE')
        require(session.query("SELECT CONCAT(@@foreign_key_checks, CHAR(44), @@unique_checks);")=='1,1', 'REPAIR_CHECKS')
        # Complete metadata/grant comparison must use this session, never a new
        # connection (otherwise preflight would conflict with the active worker).
        self.lifecycle.inspect_metadata(session, baseline['metadata'])
        data=load('mvp-qa-v1')
        current=schema_hashes(session.query,data)
        if not hasattr(self,'schema'):self.schema=current
        require(current==self.schema,'REPAIR_SCHEMA_CHANGED')

    def finish(self): return self.lifecycle.close_and_confirm_locked_no_sessions()
