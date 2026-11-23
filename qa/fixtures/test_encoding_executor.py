"""No driver, Docker, network, credentials or SQL server used."""
import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parent))
import encoding_executor as engine
from fixture_tools import load
from prepare_encoding_repair import prepare


class Connection:
    def __init__(self, test):
        self.test = test
        self.local = copy.deepcopy(test.database)
        self.rowcount = 1
        self.closed = False
    def cursor(self): return self
    def execute(self, sql, parameters=()):
        self.sql = sql
        if sql=='START TRANSACTION' and self.test.fail=='start': raise OSError()
        if sql.startswith('UPDATE'):
            self.test.updates += 1
            if self.test.fail == 'update': raise RuntimeError('PRIVATE-CREDENTIAL')
            change = next(c for c in self.test.plan['changes'] if c['statement'] == sql and c['id'] == parameters[1] and c['replacement_utf8_hex'] == parameters[0])
            row = next(r for r in self.local[change['table']] if r[change['primary_key']] == parameters[1])
            if self.test.fail == 'rowcount': self.rowcount = 0
            else: row[change['column']] = bytes.fromhex(parameters[0]).decode('utf-8')
    def fetchall(self):
        return [('latin1,latin1,latin1' if self.test.fail == 'charset' else 'utf8mb4,utf8mb4,utf8mb4',)] if 'character_set' in self.sql else []
    def commit(self):
        self.test.commits += 1
        if self.test.fail == 'commit_before': raise OSError('PRIVATE-CREDENTIAL')
        self.test.database = copy.deepcopy(self.local)
        if self.test.fail == 'commit_after': raise OSError('PRIVATE-CREDENTIAL')
    def rollback(self):
        self.test.rollbacks += 1
        if self.test.fail == 'rollback': raise OSError('PRIVATE-CREDENTIAL')
    def close(self): self.closed = True


class Controls:
    def __init__(self, test): self.test = test
    def candidate(self): self.step('candidate')
    def preflight(self): self.step('preflight'); return {'instance': 'synthetic'}
    def backup(self, baseline): self.step('backup')
    def arm(self): self.step('arm')
    def check(self):
        self.step('watchdog')
        if self.test.fail=='guard_mid' and self.test.updates>=3:raise RuntimeError('PRIVATE-CREDENTIAL')
    def inspect(self, session, baseline): self.step('inspect')
    def finish(self): self.test.finished += 1; return self.test.fail != 'finish'
    def step(self, name):
        if self.test.fail == name: raise RuntimeError('PRIVATE-CREDENTIAL')


class ExecutorTests(unittest.TestCase):
    def setUp(self):
        self.plan = prepare()[1]
        self.database = copy.deepcopy(load('mvp-qa-v1')['rows'])
        for row in self.database['u_usuario']:
            row['usuario_password_hash'] = '$2a$10$' + 'a' * 53
        self.before = copy.deepcopy(self.database)
        self.fail = None
        self.connections = []
        self.updates = self.commits = self.rollbacks = self.finished = 0
    def factory(self):
        if len(self.connections) == 1 and self.fail == 'reconnect': raise OSError()
        conn = Connection(self); self.connections.append(conn); return conn
    def read(self, query, data):
        conn = query.__self__.connection
        result = copy.deepcopy(conn.local)
        if self.fail in ('validation', 'rollback') and self.updates and len(self.connections)==1:
            result['u_rol'][0]['rol_descripcion'] = 'unexpected'
        if self.fail == 'post' and len(self.connections) == 2:
            result['u_rol'][0]['rol_descripcion'] = 'unexpected'
        return result
    def run_engine(self):
        with patch.object(engine, 'read_rows', side_effect=self.read):
            result = engine.run(self.factory, Controls(self), self.plan)
        self.assertNotIn('PRIVATE-CREDENTIAL', str(result))
        self.assertEqual(self.finished, 1)
        return result
    def test_success(self):
        result = self.run_engine()
        self.assertTrue(result['success']); self.assertEqual(self.commits, 1)
        self.assertEqual(len(self.connections), 2); self.assertEqual(self.updates, 35)
        self.assertEqual(self.before['u_usuario'], self.database['u_usuario'])
        self.assertTrue(all(c.closed for c in self.connections))
    def test_prior_controls(self):
        for stage in ('candidate', 'preflight', 'backup', 'arm', 'watchdog'):
            with self.subTest(stage=stage):
                self.setUp(); self.fail = stage
                self.assertEqual(self.run_engine()['transaction'], 'NOT_STARTED')
                self.assertEqual(self.connections, [])
    def test_validation_rolls_back(self):
        self.fail = 'validation'; result=self.run_engine()
        self.assertEqual(result['transaction'], 'ROLLED_BACK')
        self.assertEqual(result['verification'], 'ROLLBACK_VERIFIED')
        self.assertEqual(self.commits, 0); self.assertEqual(self.database, self.before)
    def test_update_disconnect(self):
        self.fail = 'update'; self.assertEqual(self.run_engine()['transaction'], 'ROLLED_BACK')
    def test_start_disconnect(self):
        self.fail = 'start'; self.assertEqual(self.run_engine()['transaction'], 'ROLLED_BACK')
        self.assertEqual(self.updates,0)
    def test_rowcount(self):
        self.fail = 'rowcount'; self.assertEqual(self.run_engine()['transaction'], 'ROLLED_BACK')
    def test_guard_failure_during_transaction(self):
        self.fail='guard_mid';result=self.run_engine()
        self.assertEqual(result['transaction'],'ROLLED_BACK')
        self.assertEqual(result['verification'],'ROLLBACK_VERIFIED')
        self.assertEqual(self.updates,3);self.assertEqual(self.commits,0)
    def test_rollback_unknown(self):
        self.fail = 'rollback'; self.assertEqual(self.run_engine()['transaction'], 'UNKNOWN')
    def test_commit_before_unknown(self):
        self.fail = 'commit_before'; self.assertEqual(self.run_engine()['transaction'], 'UNKNOWN')
        self.assertEqual(self.commits, 1); self.assertEqual(self.rollbacks, 0)
    def test_commit_after_unknown(self):
        self.fail = 'commit_after'; self.assertEqual(self.run_engine()['transaction'], 'UNKNOWN')
        self.assertNotEqual(self.database, self.before); self.assertEqual(self.rollbacks, 0)
    def test_post_failure_committed(self):
        self.fail = 'post'; result = self.run_engine()
        self.assertEqual(result['transaction'], 'COMMITTED'); self.assertFalse(result['success'])
    def test_second_connection_failure(self):
        self.fail = 'reconnect'; result = self.run_engine()
        self.assertEqual(result['transaction'], 'COMMITTED'); self.assertEqual(self.commits, 1)
    def test_finish_unknown(self):
        self.fail = 'finish'; result = self.run_engine()
        self.assertEqual(result['security'], 'UNKNOWN'); self.assertFalse(result['success'])
    def test_charset_rejected(self):
        self.fail = 'charset'; self.assertEqual(self.run_engine()['transaction'], 'NOT_STARTED')
        self.assertEqual(self.updates, 0)
    def test_tampered_plan(self):
        self.plan['changes'][0]['column'] = 'usuario_password_hash'
        self.assertEqual(self.run_engine()['transaction'], 'NOT_STARTED')
        self.assertEqual(len(self.connections), 0)
    def test_second_application_rejected(self):
        self.assertTrue(self.run_engine()['success'])
        self.finished = 0; self.updates = 0
        result = self.run_engine()
        self.assertEqual(result['transaction'], 'ROLLED_BACK'); self.assertEqual(self.commits, 1)


if __name__ == '__main__': unittest.main()
