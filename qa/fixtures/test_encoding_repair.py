"""Offline charset/repair regression. No SQL execution, credentials or applications."""
import copy,hashlib,importlib.util,json,sys,unittest
from pathlib import Path
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import ROOT,FILE,load,assert_rows,validate,canonical_rows
from prepare_encoding_repair import prepare,simulate

class EncodingRepair(unittest.TestCase):
    def actual(self):
        data=copy.deepcopy(load('mvp-qa-v1')['rows'])
        for u in data['u_usuario']:u['usuario_password_hash']='$2b$04$'+'.'*53
        return data
    def test_exact_bounded_scope(self):
        target,plan=prepare();self.assertEqual(len(plan['changes']),35);self.assertEqual(len({c['table'] for c in plan['changes']}),15)
        self.assertEqual(len({(c['table'],c['id'],c['column']) for c in plan['changes']}),35)
        self.assertFalse(plan['execution_authorized'])
    def test_generated_files_match_review(self):
        target,plan=prepare();self.assertEqual(FILE.with_name('mvp-v2.json').read_bytes(),target)
        self.assertEqual(json.loads(FILE.with_name('encoding-repair-v1-to-v2.json').read_text(encoding='utf-8')),plan)
    def test_v1_immutable_against_authorized_manifest(self):
        historical=ROOT/'qa/history/QA-local-f823d61-d4490e5e5ebf.json'
        before=json.loads((historical if historical.exists() else ROOT/'qa/candidate.json').read_text(encoding='utf-8'))
        old=next(x['sha256'] for x in before['files'] if x['path']=='qa/fixtures/mvp-v1.json')
        self.assertEqual(hashlib.sha256(FILE.read_bytes()).hexdigest(),old)
    def test_simulation_preserves_count_and_credentials(self):
        actual=self.actual();_,plan=prepare();fixed=simulate(actual,plan)
        assert_rows(load('mvp-qa-v2'),fixed)
        self.assertEqual(sum(map(len,actual.values())),67);self.assertEqual(sum(map(len,fixed.values())),67)
        self.assertEqual(fixed['u_usuario'],actual['u_usuario'])
        for a,b in zip(actual['u_rol'],fixed['u_rol']):
            self.assertEqual({k:v for k,v in a.items() if k!='rol_descripcion'},{k:v for k,v in b.items() if k!='rol_descripcion'})
    def test_drift_rejected_before_simulation(self):
        actual=self.actual();actual['ticket'][0]['titulo']='OTHER_QA_CHANGE'
        with self.assertRaises(RuntimeError):simulate(actual,prepare()[1])
    def test_second_application_rejected(self):
        fixed=simulate(self.actual(),prepare()[1])
        with self.assertRaises(RuntimeError):simulate(fixed,prepare()[1])
    def test_plan_tampering_rejected(self):
        plan=prepare()[1];plan['changes'][0]['column']='rol_nombre'
        with self.assertRaises(RuntimeError):simulate(self.actual(),plan)
    def test_only_guarded_update_statements(self):
        for c in prepare()[1]['changes']:
            statement=c['statement'];self.assertTrue(statement.startswith('UPDATE `dima_bug_qa_mvp`.'))
            self.assertIn('AND HEX(',statement);self.assertEqual(c['expected_affected_rows'],1)
            self.assertNotIn(c['column'],['usuario_password_hash','usuario_email','rol_nombre','rol_id','usuario_estado'])
    def test_no_mojibake_in_v2_and_utf8_roundtrip(self):
        target,_=prepare();text=target.decode('utf-8');self.assertNotIn('\u00c3',text);self.assertNotIn('\u00c2',text)
        self.assertEqual(text.encode('utf-8'),target);validate(json.loads(text))
    def test_v1_still_loadable_for_historical_verification(self):self.assertEqual(load('mvp-qa-v1')['version'],'mvp-qa-v1')
    def test_scope_unchanged_except_approved_texts(self):
        old=load('mvp-qa-v1');new=load('mvp-qa-v2');changes=prepare()[1]['changes']
        approved={(c['table'],c['id'],c['column']) for c in changes}
        from prepare_encoding_repair import KEYS
        for t,rows in old['rows'].items():
            for a,b in zip(rows,new['rows'][t]):
                for col,v in a.items():
                    if v!=b[col]:self.assertIn((t,a[KEYS[t]],col),approved)
    def test_mysql_option_without_process_or_sql(self):
        sys.path.insert(0,str(ROOT/'qa/mysql/tls'))
        import tls_support
        with patch.object(tls_support,'docker',return_value='public-synthetic-result') as fake:
            self.assertEqual(tls_support.sql('SELECT public_synthetic;'),'public-synthetic-result')
        command=fake.call_args.args[0][-1]
        self.assertIn('mysql --default-character-set=utf8mb4 ',command)
        self.assertNotIn('SET GLOBAL',command)
    def test_backup_option_without_process(self):
        text=(ROOT/'qa/fixtures/preflight_backup.py').read_text(encoding='utf-8')
        self.assertIn('mysqldump --default-character-set=utf8mb4 ',text)
    def test_transport_encoding_explicit(self):
        import tls_support
        with patch.object(tls_support,'clean_env',return_value={}),patch.object(tls_support.shutil,'which',return_value='not-executed'),patch.object(tls_support.subprocess,'run',return_value=Mock(returncode=0,stdout='áéíóú ñ —',stderr='')) as fake:
            self.assertEqual(tls_support.docker(['public-synthetic']), 'áéíóú ñ —')
        self.assertEqual(fake.call_args.kwargs['encoding'],'utf-8')
if __name__=='__main__':unittest.main()
