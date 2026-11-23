"""Versioned preflight receipts with synthetic query responses. No SQL, Docker or credentials."""
import copy,hashlib,json,tempfile,unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import *
class Receipts(unittest.TestCase):
    def setUp(self):
        self.data=load();self.actual=copy.deepcopy(self.data['rows'])
        for user in self.actual['u_usuario']:user['usuario_password_hash']='$2b$04$'+'.'*53
        self.uuid='public-synthetic-instance';self.schema_changed=False
        base=ROOT/'qa/runtime/fixture-runs';base.mkdir(exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(dir=base);self.path=Path(self.temp.name)/'receipt.json'
        self.receipt={'format':'dimabug-qa-fixture-receipt-v1','version':self.data['version'],'fixture_sha256':fingerprint(),
            'server_uuid':self.uuid,'schema_sha256':schema_hashes(self.query,self.data),
            'data_sha256':{t:hashlib.sha256('\n'.join(canonical_rows(rows)).encode()).hexdigest() for t,rows in self.actual.items()}}
        self.save()
    def tearDown(self):self.temp.cleanup()
    def save(self):self.path.write_text(json.dumps(self.receipt))
    def query(self,s):
        if s=='SELECT @@server_uuid;':return self.uuid
        if s.startswith('SHOW CREATE'):return s+(' PUBLIC_CHANGED' if self.schema_changed else '')+' AUTO_INCREMENT=1010'
        if s=='SELECT control_id FROM dima_bug_qa_mvp.u_organizacion_control;':return '1'
        if s=='SELECT usuario_email FROM dima_bug_qa_mvp.u_usuario;':return '\n'.join(u['usuario_email'] for u in self.actual['u_usuario'])
        table=s.split('dima_bug_qa_mvp.`')[1].split('`')[0]
        if 'COUNT(*)' in s:return str(len(self.actual[table]))
        return '\n'.join(json.dumps(row) for row in self.actual[table])
    def test_exact_fixture_accepted(self):self.assertTrue(verify_receipt(self.query,self.path))
    def test_changed_data_same_count_rejected(self):
        self.actual['ticket'][0]['titulo']='PUBLIC_CHANGED'
        with self.assertRaises(RuntimeError):verify_receipt(self.query,self.path)
    def test_wrong_instance_rejected(self):
        self.uuid='another-public-instance'
        with self.assertRaises(RuntimeError):verify_receipt(self.query,self.path)
    def test_changed_schema_rejected(self):
        self.schema_changed=True
        with self.assertRaises(RuntimeError):verify_receipt(self.query,self.path)
    def test_wrong_version_rejected(self):
        self.receipt['version']='unknown';self.save()
        with self.assertRaises(RuntimeError):verify_receipt(self.query,self.path)
    def test_wrong_fixture_digest_rejected(self):
        self.receipt['fixture_sha256']='0'*64;self.save()
        with self.assertRaises(RuntimeError):verify_receipt(self.query,self.path)
    def test_password_hash_change_rejected(self):
        self.actual['u_usuario'][0]['usuario_password_hash']='$2b$04$'+'/'*53
        with self.assertRaises(RuntimeError):verify_receipt(self.query,self.path)
    def test_reset_allows_synthetic_changes_only_explicitly(self):
        self.actual['ticket'][0]['titulo']='PUBLIC_QA_MUTATION'
        self.assertTrue(verify_reset_scope(self.query,self.path))
        with self.assertRaises(RuntimeError):verify_receipt(self.query,self.path)
    def test_reset_rejects_external_user(self):
        self.actual['u_usuario'][0]['usuario_email']='outside@example.invalid'
        with self.assertRaises(RuntimeError):verify_reset_scope(self.query,self.path)
    def test_reset_rejects_gmail_data(self):
        self.actual['gm_cuenta_oauth']=[{'id':1}]
        with self.assertRaises(RuntimeError):verify_reset_scope(self.query,self.path)
if __name__=='__main__':unittest.main()
