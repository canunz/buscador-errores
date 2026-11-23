"""No SQL, Docker, credentials or process startup: mocked close/report regressions."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import sys
import threading
import unittest
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parent))
from close_qa_account import close_report
from cleanup_sentinel import retry_close

class CloseReports(unittest.TestCase):
    def query(self,statement):
        if statement.startswith('ALTER USER '):return ''
        if statement.startswith('SELECT ID,USER'):return ''
        if 'information_schema.PROCESSLIST' in statement:return '0'
        return '2'
    def test_success(self):
        r=close_report(query=self.query);self.assertTrue(r['closed']);self.assertTrue(r['lock_acknowledged']);self.assertTrue(r['account_locked_verified'])
    def test_lock_error_unknown(self):
        r=close_report(query=Mock(side_effect=RuntimeError('SQL_ERROR_1227')))
        self.assertFalse(r['closed']);self.assertIsNone(r['lock_acknowledged']);self.assertEqual(r['failure_code'],'SQL_ERROR_1227')
    def test_lock_ack_then_verify_failure(self):
        def action(s):
            if 'mysql.user' in s:raise TimeoutError('synthetic-secret-canary')
            return self.query(s)
        r=close_report(query=action);self.assertTrue(r['lock_acknowledged']);self.assertIsNone(r['account_locked_verified']);self.assertFalse(r['closed']);self.assertNotIn('synthetic-secret-canary',str(r))
    def test_sessions_remain_not_confirmed(self):
        def action(s):return '1' if 'COUNT(*) FROM information_schema.PROCESSLIST' in s else self.query(s)
        r=close_report(query=action);self.assertFalse(r['app_sessions_zero_verified']);self.assertFalse(r['closed'])
    def test_accounts_not_locked(self):
        def action(s):return '1' if 'mysql.user' in s else self.query(s)
        r=close_report(query=action);self.assertFalse(r['account_locked_verified']);self.assertFalse(r['closed'])
    def test_session_disappeared_race(self):
        def action(s):
            if s.startswith('SELECT ID,USER'):return '42\tdimabug_qa_app'
            if s.startswith('KILL CONNECTION'):raise RuntimeError('SQL_ERROR_1094')
            return self.query(s)
        self.assertTrue(close_report(query=action)['closed'])
    def test_concurrent_idempotent_close(self):
        barrier=threading.Barrier(2)
        def action(s):
            if s.startswith('ALTER USER'):barrier.wait(timeout=5)
            return self.query(s)
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda _:close_report(query=action),range(2)))
        self.assertTrue(all(r['closed'] for r in results))
    def test_false_report_does_not_pass_retry(self):
        call=Mock(return_value={'closed':False,'failure_code':'UNKNOWN'})
        self.assertFalse(retry_close(call,wait=lambda _:None));self.assertEqual(call.call_count,3)
    def test_confirmed_report_stops_retry(self):
        call=Mock(return_value={'closed':True})
        self.assertTrue(retry_close(call,wait=lambda _:None));call.assert_called_once()

if __name__=='__main__':unittest.main()
