"""Pure protocol/process simulations; never creates real subprocesses or sockets."""
import json
import queue
import re
import sys
import threading
import types
import unittest
from pathlib import Path
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parent))
from repair_transport import MaintenanceConnection
from repair_lifecycle import Lifecycle
from repair_cleanup import close_maintenance
from encoding_executor import Session
from encoding_controls import RecoveryControls


class Output:
    def __init__(self):self.queue=queue.Queue()
    def __iter__(self):return self
    def __next__(self):
        value=self.queue.get(timeout=3)
        if value is None:raise StopIteration
        return value
    def close(self):self.queue.put(None)


class Process:
    def __init__(self):
        self.stdout=Output();self.stdin=self;self.sent=[];self.disconnect=False
    def write(self,text):
        self.sent.append(text)
        if self.disconnect:
            self.stdout.queue.put(None)
            return
        marker=re.search(r'__QA_FRAME_[a-f0-9]+__',text)[0]
        if text.startswith('SELECT multiple'):self.stdout.queue.put('a\tb\n')
        if text.startswith('SELECT accented'):self.stdout.queue.put('Revisión técnica\n')
        self.stdout.queue.put(marker+('1' if text.startswith('UPDATE') else '-1')+'\n')
    def flush(self):pass
    def close(self):self.stdout.queue.put(None)
    def wait(self,timeout):return 0
    def kill(self):self.stdout.queue.put(None)


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.process=Process();self.popen=Mock(return_value=self.process)
        self.connection=MaintenanceConnection(popen=self.popen,docker_path='docker-test',environment={'PATH':'sanitized'})
    def tearDown(self):self.connection.close()
    def test_one_process_and_no_secrets(self):
        self.connection.execute('UPDATE t SET c=%s WHERE id=%s',('C3A9',3))
        self.connection.commit()
        self.popen.assert_called_once()
        argv=self.popen.call_args.args[0]
        self.assertIn('--skip-reconnect',argv[-1]);self.assertIn('utf8mb4',argv[-1])
        self.assertNotIn('C3A9',str(argv));self.assertNotIn('UPDATE t',str(argv))
        self.assertEqual(self.connection.rowcount,-1)
        self.assertEqual(self.popen.call_args.kwargs['env'],{'PATH':'sanitized'})
    def test_parameters_and_rowcount(self):
        self.connection.execute('UPDATE t SET c=%s WHERE id=%s',('C3A9',3))
        self.assertEqual(self.connection.rowcount,1)
        self.assertIn("c='C3A9' WHERE id=3",self.process.sent[-1])
    def test_parameter_injection_rejected(self):
        with self.assertRaises(RuntimeError):self.connection.execute('UPDATE t SET c=%s',("';DROP TABLE t",))
        self.assertEqual(len(self.process.sent),3)
    def test_parameter_number_rejected(self):
        with self.assertRaises(RuntimeError):self.connection.execute('UPDATE t SET c=%s',())
    def test_multicolumn_preserved(self):
        self.assertEqual(Session(self.connection).query('SELECT multiple'),'a\tb')
    def test_unicode_preserved(self):
        self.assertEqual(Session(self.connection).query('SELECT accented'),'Revisión técnica')
    def test_disconnect_no_retry(self):
        self.process.disconnect=True
        with self.assertRaisesRegex(RuntimeError,'REPAIR_TRANSPORT_UNKNOWN'):self.connection.commit()
        self.assertEqual(sum(t.startswith('COMMIT') for t in self.process.sent),1)
        self.assertTrue(self.connection.closed)
    def test_deadline_no_retry(self):
        with patch.object(self.connection.lines,'get',side_effect=queue.Empty):
            with self.assertRaises(RuntimeError):self.connection.execute('SELECT blocked')
        self.assertTrue(self.connection.closed)
    def test_close_idempotent(self):
        self.connection.close();self.connection.close()
        with self.assertRaisesRegex(RuntimeError,'CLOSED'):self.connection.execute('SELECT later')


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.life=Lifecycle.__new__(Lifecycle)
        self.life.deadline=200
        self.life.folder=Mock();self.life.folder.__truediv__=Mock(return_value=Mock(exists=Mock(return_value=False)))
        self.life.kernel=Mock();self.life.kernel.WaitForSingleObject.return_value=258
        self.life.handles=[1,2]
    def test_both_alive(self):
        with patch('repair_lifecycle.time.monotonic',return_value=100):self.life.check()
        self.assertEqual(self.life.kernel.WaitForSingleObject.call_count,2)
    def test_parent_died(self):
        self.life.kernel.WaitForSingleObject.side_effect=[0,258]
        with patch('repair_lifecycle.time.monotonic',return_value=100):
            with self.assertRaises(RuntimeError):self.life.check()
    def test_sentinel_died(self):
        self.life.kernel.WaitForSingleObject.side_effect=[258,0]
        with patch('repair_lifecycle.time.monotonic',return_value=100):
            with self.assertRaises(RuntimeError):self.life.check()
    def test_deadline(self):
        with patch('repair_lifecycle.time.monotonic',return_value=201):
            with self.assertRaises(RuntimeError):self.life.check()
    def test_requested_stop(self):
        self.life.folder.__truediv__.return_value.exists.return_value=True
        with patch('repair_lifecycle.time.monotonic',return_value=100):
            with self.assertRaises(RuntimeError):self.life.check()


class CleanupTests(unittest.TestCase):
    def setUp(self):
        self.module=types.ModuleType('tls_support')
        self.module.NAME='dimabug-qa-mvp-13318-mysql'
        self.module.sql=Mock();self.module.docker=Mock();self.module.close_window=Mock(return_value=True)
        self.commands=[]
    def docker(self,args):
        if args[0]=='context':return json.dumps('npipe://local-test')
        if '.Mounts' in args[2]:return json.dumps([{'Name':'dimabug-qa-mvp-13318-mysql-data','Destination':'/var/lib/mysql'}])
        if '.NetworkSettings' in args[2]:return json.dumps({'dimabug-qa-mvp-13318-net':{}})
        return json.dumps('mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b')
    def query(self,statement):
        self.commands.append(statement)
        if statement=='SELECT @@server_uuid;':return 'test-uuid'
        if statement.startswith('SELECT ID'):return '44\n45'
        if statement.startswith('SELECT COUNT'):return '0'
        return ''
    def test_kill_only_scoped_maintenance_then_lock(self):
        with patch.dict(sys.modules,{'tls_support':self.module}):
            self.assertTrue(close_maintenance('test-uuid',query=self.query,docker_call=self.docker))
        self.assertIn('KILL CONNECTION 44;',self.commands)
        self.assertIn("ALTER USER 'dimabug_qa_schema'@'localhost' ACCOUNT LOCK;",self.commands)
        self.module.close_window.assert_called_once()
    def test_wrong_uuid_no_kill_or_lock(self):
        with patch.dict(sys.modules,{'tls_support':self.module}):
            with self.assertRaises(RuntimeError):close_maintenance('wrong',query=self.query,docker_call=self.docker)
        self.assertEqual(self.commands,['SELECT @@server_uuid;'])
    def test_remote_engine_no_sql(self):
        with patch.dict(sys.modules,{'tls_support':self.module}):
            with self.assertRaises(RuntimeError):close_maintenance('test-uuid',query=self.query,docker_call=lambda args:json.dumps('tcp://remote'))
        self.assertEqual(self.commands,[])


    def test_lingering_sessions_reject(self):
        def query(statement):return '1' if statement.startswith('SELECT COUNT') else self.query(statement)
        with patch.dict(sys.modules,{'tls_support':self.module}):
            with self.assertRaises(RuntimeError):close_maintenance('test-uuid',query=query,docker_call=self.docker)
    def test_lock_not_confirmed(self):
        self.module.close_window.return_value=False
        with patch.dict(sys.modules,{'tls_support':self.module}):
            with self.assertRaises(RuntimeError):close_maintenance('test-uuid',query=self.query,docker_call=self.docker)
    def test_wrong_network_no_sql(self):
        def docker(args):return json.dumps({'unexpected':{}}) if '.NetworkSettings' in str(args) else self.docker(args)
        with patch.dict(sys.modules,{'tls_support':self.module}):
            with self.assertRaises(RuntimeError):close_maintenance('test-uuid',query=self.query,docker_call=docker)
        self.assertEqual(self.commands,[])


class BridgeTests(unittest.TestCase):
    def test_dead_supervisor_rejected_before_acl_or_preflight(self):
        controls=RecoveryControls.__new__(RecoveryControls)
        controls.candidate_id='simulated-candidate'
        controls.lifecycle=Mock(spec=Lifecycle)
        controls.lifecycle.check.side_effect=RuntimeError('REPAIR_GUARD_EXIT')
        with patch('encoding_controls.verify_candidate'):
            with self.assertRaises(RuntimeError):controls.candidate()
        controls.lifecycle.assert_private_directory.assert_not_called()
    def test_snapshot_uses_existing_transaction_query(self):
        life=Lifecycle.__new__(Lifecycle)
        session=Mock()
        module=types.ModuleType('run_privilege_test')
        module.snapshot=Mock(return_value=({'schema':'same'},{}))
        with patch.dict(sys.modules,{'run_privilege_test':module}):
            life.inspect_metadata(session,{'schema':'same'})
        self.assertIs(module.snapshot.call_args.kwargs['query'],session.query)


if __name__=='__main__':unittest.main()
