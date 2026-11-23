import unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from deployment_sql_session import quarantine,SqlSessionError

class Fake:
 def __init__(self,bad=None,fail=None):self.calls=[];self.bad=bad;self.fail=fail
 def query(self,q):
  self.calls.append(q)
  if q==self.fail:raise SqlSessionError('SIMULATED_DISCONNECTION')
  if q=='SELECT @@server_uuid,DATABASE()':return [['uuid','staging']]
  return [['1' if q==self.bad else '0']] if q.startswith('SELECT COUNT') else []

class Controls(unittest.TestCase):
 def test_commit_only_after_all_checks(self):
  s=Fake();markers=[];quarantine(s,'staging','uuid',['UPDATE example','SELECT COUNT(*) check'],lambda:markers.append('attempt'),lambda:markers.append('confirmed'))
  self.assertEqual(s.calls[-1],'COMMIT');self.assertEqual(markers,['attempt','confirmed'])
 def test_bad_count_rolls_back_without_commit(self):
  s=Fake(bad='SELECT COUNT(*) check')
  with self.assertRaises(SqlSessionError):quarantine(s,'staging','uuid',['UPDATE example','SELECT COUNT(*) check'],lambda:self.fail(),lambda:self.fail())
  self.assertEqual(s.calls[-1],'ROLLBACK');self.assertNotIn('COMMIT',s.calls)
 def test_wrong_target_never_starts_transaction(self):
  s=Fake()
  with self.assertRaises(SqlSessionError):quarantine(s,'production','other',[],lambda:self.fail(),lambda:self.fail())
  self.assertNotIn('START TRANSACTION',s.calls)
 def test_unknown_commit_never_retries_or_claims_confirmation(self):
  s=Fake(fail='COMMIT');markers=[]
  with self.assertRaises(SqlSessionError):quarantine(s,'staging','uuid',['SELECT COUNT(*) check'],lambda:markers.append('attempt'),lambda:markers.append('confirmed'))
  self.assertEqual(s.calls.count('COMMIT'),1);self.assertNotIn('ROLLBACK',s.calls);self.assertEqual(markers,['attempt'])
 def test_failure_before_commit_rolls_back(self):
  s=Fake(fail='UPDATE example')
  with self.assertRaises(SqlSessionError):quarantine(s,'staging','uuid',['UPDATE example'],lambda:self.fail(),lambda:self.fail())
  self.assertEqual(s.calls[-1],'ROLLBACK');self.assertNotIn('COMMIT',s.calls)
 def test_no_unguarded_old_rollback_calls(self):
  s=(Path(__file__).parent/'deploy_universal.py').read_text(encoding='utf-8')
  self.assertNotIn('d.rollback(',s);self.assertNotIn('d.guardian(',s)
  self.assertLess(s.index("result['quarantine']=quarantine_before_legacy(ctx)"),s.index("atomic(JAR,(FOLDER/'backend.jar').read_bytes()"))
  self.assertIn("status='ROLLBACK_VERIFIED_MAINTENANCE'",s)
 def test_no_valid_otp_http_submissions(self):
  s=(Path(__file__).parent/'deploy_universal.py').read_text(encoding='utf-8')
  self.assertIn("('/api/auth/codigo',400,{})",s)
  self.assertNotIn("'email':email",s);self.assertNotIn('solicitarCodigo(',s)
 def test_service_scope_locked(self):
  s=(Path(__file__).parent/'deploy_universal.py').read_text(encoding='utf-8')
  self.assertIn("UNIT='dimabug@staging.service'",s)
  self.assertIn("'UNAUTHORIZED_SERVICE_OPERATION'",s)
  self.assertNotIn("['systemctl','stop','dimabug@staging7.service']",s)

if __name__=='__main__':unittest.main()
