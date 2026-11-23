"""Pure mocked supervisor/close tests. NO real SQL, Docker, credentials or apps."""
import json
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parent))
from cleanup_sentinel import should_close,retry_close,run,ROOT
from close_qa_account import guard_sql,validate_identity

IMAGE='mysql:8.0.46@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b'

class SupervisorControls(unittest.TestCase):
    def metadata(self):return {'Config':{'Image':IMAGE},'Mounts':[
        {'Destination':'/var/lib/mysql','Name':'dimabug-qa-mvp-13318-mysql-data'},
        {'Destination':'/run/secrets/mysql_root_password','Source':'/run/desktop/mnt/host/c/Proyectos/DimaBug-MVP/qa/runtime/secrets/mysql-root.txt','RW':False}],
        'NetworkSettings':{'Networks':{'dimabug-qa-mvp-13318-net':{}}},'HostConfig':{'PortBindings':{}}}
    def call(self,statement,meta=None,context='npipe:////./pipe/dockerDesktopLinuxEngine'):
        values=[json.dumps(context),json.dumps(meta or self.metadata())]
        query=Mock(return_value='ok');docker=Mock(side_effect=values)
        try:return guard_sql(statement,docker=docker,query=query),query
        except Exception:
            self.assertFalse(query.called);raise
    def test_alive_before_deadline_not_closed(self):self.assertFalse(should_close(179,True,False))
    def test_parent_death_closes(self):self.assertTrue(should_close(0,False,False))
    def test_deadline_closes(self):self.assertTrue(should_close(180,True,False))
    def test_stop_request_closes(self):self.assertTrue(should_close(0,True,True))
    def test_close_success(self):self.assertTrue(retry_close(lambda:True,lambda _:None))
    def test_close_transient_failure_retries(self):
        action=Mock(side_effect=[RuntimeError(),False,True]);self.assertTrue(retry_close(action,lambda _:None));self.assertEqual(action.call_count,3)
    def test_permanent_failure_not_success(self):self.assertFalse(retry_close(lambda:False,lambda _:None))
    def test_exception_failure_not_success(self):self.assertFalse(retry_close(Mock(side_effect=RuntimeError()),lambda _:None))
    def test_only_exact_account_lock_accepted(self):self.assertEqual(self.call("ALTER USER 'dimabug_qa_app'@'172.21.0.3' ACCOUNT LOCK;")[0],'ok')
    def test_unlock_rejected(self):
        with self.assertRaises(RuntimeError):self.call("ALTER USER 'dimabug_qa_app'@'172.21.0.3' ACCOUNT UNLOCK;")
    def test_other_account_rejected(self):
        with self.assertRaises(RuntimeError):self.call("ALTER USER 'root'@'localhost' ACCOUNT LOCK;")
    def test_wrong_image_rejected(self):
        m=self.metadata();m['Config']['Image']='other'
        with self.assertRaises(RuntimeError):self.call('KILL CONNECTION 12;',m)
    def test_wrong_volume_rejected(self):
        m=self.metadata();m['Mounts'][0]['Name']='production'
        with self.assertRaises(RuntimeError):self.call('KILL CONNECTION 12;',m)
    def test_remote_context_rejected(self):
        with self.assertRaises(RuntimeError):self.call('KILL CONNECTION 12;',context='ssh://remote')
    def test_external_port_rejected(self):
        m=self.metadata();m['HostConfig']['PortBindings']={'3306/tcp':[{'HostPort':'3306'}]}
        with self.assertRaises(RuntimeError):self.call('KILL CONNECTION 12;',m)
    def test_wrong_secret_origin_rejected(self):
        m=self.metadata();m['Mounts'][1]['Source']='/other/password'
        with self.assertRaises(RuntimeError):self.call('KILL CONNECTION 12;',m)
    def test_network_rejected(self):
        m=self.metadata();m['NetworkSettings']['Networks']={'production':{}}
        with self.assertRaises(RuntimeError):self.call('KILL CONNECTION 12;',m)
    def test_injected_kill_rejected(self):
        with self.assertRaises(RuntimeError):self.call('KILL CONNECTION 12; DROP DATABASE example;')
    @unittest.skipUnless(sys.platform=='win32','Windows handles only')
    def test_real_parent_exit_runs_mocked_cleanup(self):
        base=ROOT/'qa/runtime/backend-runs';base.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=base) as temp:
            child=subprocess.Popen([sys.executable,'-I','-c','import time;time.sleep(1)'])
            action=Mock(return_value=True)
            try:self.assertTrue(run(Path(temp),child.pid,close_action=action))
            finally:child.wait(timeout=5)
            action.assert_called_once()
            self.assertEqual(json.loads((Path(temp)/'sentinel-final.json').read_text())['estado'],'CIERRE_VERIFICADO')

if __name__=='__main__':unittest.main()
