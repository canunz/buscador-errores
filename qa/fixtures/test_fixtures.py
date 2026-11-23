"""Offline fixture/schema/reset verification. All SQL callbacks are synthetic; no driver calls."""
import copy,hashlib,json,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import *
class Fixtures(unittest.TestCase):
    def setUp(self):self.data=load()
    def rejects(self,mutate):
        mutate(self.data)
        with self.assertRaises(RuntimeError):validate(self.data)
    def test_valid(self):self.assertEqual(sum(map(len,self.data['rows'].values())),67)
    def test_unknown_role(self):self.rejects(lambda d:d['rows']['u_rol'][0].update(rol_nombre='OTHER'))
    def test_real_email_rejected(self):self.rejects(lambda d:d['rows']['u_usuario'][0].update(usuario_email='outside@example.invalid'))
    def test_broken_role_reference(self):self.rejects(lambda d:d['rows']['u_usuario'][0].update(rol_id=999))
    def test_duplicate_email(self):self.rejects(lambda d:d['rows']['u_usuario'][1].update(usuario_email=d['rows']['u_usuario'][0]['usuario_email']))
    def test_invalid_enum(self):self.rejects(lambda d:d['rows']['co_conocimiento'][0].update(conocimiento_estado='OTHER'))
    def test_required(self):self.rejects(lambda d:d['rows']['co_conocimiento'][0].update(creado_por=None))
    def test_column_length(self):self.rejects(lambda d:d['rows']['cl_sistema'][0].update(sistema_nombre='x'*101))
    def test_step_order(self):self.rejects(lambda d:d['rows']['pr_paso'][1].update(paso_orden=1))
    def test_unknown_column(self):self.rejects(lambda d:d['rows']['cl_sistema'][0].update(not_a_column='x'))
    def test_gmail_data(self):self.rejects(lambda d:d['rows']['gm_cuenta_oauth'].append({'id':1}))
    def test_catalog_relation(self):self.rejects(lambda d:d['rows']['cl_modulo'][0].update(sistema_id=999))
    def test_reset_scope_dml_only(self):
        statements=[s for s,v in plan(self.data,True)]
        self.assertTrue(all(s.startswith(('DELETE FROM `dima_bug_qa_mvp`.','INSERT INTO `dima_bug_qa_mvp`.')) for s in statements))
        self.assertFalse(any('`gm_' in s or '`u_organizacion_control`' in s for s in statements))
    def test_reverse_dependencies(self):
        deletes=[s for s,v in plan(self.data,True) if s.startswith('DELETE')]
        self.assertLess(deletes.index('DELETE FROM `dima_bug_qa_mvp`.`ticket_adjunto`'),deletes.index('DELETE FROM `dima_bug_qa_mvp`.`ticket`'))
        self.assertLess(deletes.index('DELETE FROM `dima_bug_qa_mvp`.`u_usuario`'),deletes.index('DELETE FROM `dima_bug_qa_mvp`.`u_rol`'))
    def test_hashes_required_before_transaction(self):
        with self.assertRaises(RuntimeError):sql_transaction(self.data,{})
    def test_hex_encoding_not_sql_injection(self):
        value="public' ; SELECT arbitrary; --"
        encoded=literal(value,{})
        self.assertTrue(encoded.startswith('CONVERT(0x'));self.assertNotIn('SELECT',encoded)
    def test_readback_checks_all_tables(self):
        queried=[]
        def read(s):
            queried.append(s)
            if 'COUNT(*)' in s:return '0'
            table=s.split('FROM dima_bug_qa_mvp.`')[1].split('`')[0]
            records=copy.deepcopy(self.data['rows'][table])
            if table=='u_usuario':
                # Public syntactic hash fixture, never a deployable credential or plaintext password.
                for r in records:r['usuario_password_hash']='$2b$04$'+'.'*53
            return '\n'.join(json.dumps(r) for r in records)
        actual=read_rows(read,self.data);assert_rows(self.data,actual);self.assertEqual(len(queried),44)
        actual['u_usuario'][0]['usuario_estado']=False
        with self.assertRaises(RuntimeError):assert_rows(self.data,actual)
    def test_fixture_receipt_cannot_escape_private_root(self):
        with self.assertRaises((RuntimeError,FileNotFoundError)):verify_receipt(lambda s:(_ for _ in ()).throw(AssertionError('QUERY_MUST_NOT_RUN')),FILE)
if __name__=='__main__':unittest.main()
