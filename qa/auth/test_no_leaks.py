"""Static leak/packaging gates. No credentials, SQL, application startup or real delivery."""
import hashlib,json,re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class LeakGates(unittest.TestCase):
    def test_real_authentication_unchanged(self):
        before=json.loads((ROOT/'qa/history/QA-local-f823d61-7491ecf3fc55.json').read_text()) if (ROOT/'qa/history/QA-local-f823d61-7491ecf3fc55.json').exists() else json.loads((ROOT/'qa/candidate.json').read_text())
        target='dima-bug-backend/src/main/java/cl/casol/backend/identidad/application/service/AutenticarUsuarioService.java'
        expected=next(r['sha256'] for r in before['files'] if r['path']==target)
        self.assertEqual(hashlib.sha256((ROOT/target).read_bytes()).hexdigest(),expected)
    def test_adapter_no_logging_or_network(self):
        text=(ROOT/'qa/auth/src/dimabug/qa/otp/QaOtpAdapter.java').read_text()
        self.assertNotRegex(text,r'System\.(out|err)|Logger|log\.(info|debug|warn|error)|java\.net|RestController|RequestMapping|smtp|gmail\.com')
    def test_no_public_http_code_viewer(self):
        for p in (ROOT/'qa/auth/src').rglob('*.java'):
            self.assertNotRegex(p.read_text(),r'@(RestController|Controller|RequestMapping|GetMapping|PostMapping)')
    def test_transaction_gate_present(self):
        text=(ROOT/'qa/auth/src/dimabug/qa/otp/QaOtpAdapter.java').read_text()
        self.assertIn('isActualTransactionActive()',text);self.assertIn('afterCommit()',text);self.assertIn('afterCompletion(',text)
    def test_qa_controls_unchanged(self):
        text=(ROOT/'dima-bug-backend/src/main/resources/application-qa-local.properties').read_text()
        for key in ['dimabug.gmail.oauth-habilitado','dimabug.gmail.envio-real-habilitado','dimabug.auth.correo.habilitado','dimabug.automatizacion.seguimiento-habilitado']:
            self.assertIn(key+'=false',text)
        self.assertIn('dimabug.automatizacion.modo=DESHABILITADO',text)
    def test_ui_no_console_or_clipboard_output(self):
        text=(ROOT/'qa/auth/Show-QaOtp.ps1').read_text()
        self.assertNotRegex(text,r'Write-(Host|Output|Verbose|Debug)|Set-Clipboard|Clipboard\]')
        self.assertIn('ShortcutsEnabled=$false',text);self.assertIn('$null=$form.ShowDialog()',text)
    def test_no_generation_values_in_fixture(self):
        d=json.loads((ROOT/'qa/fixtures/mvp-v1.json').read_text())
        self.assertFalse(d['rows']['u_codigo_acceso']);self.assertFalse(d['rows']['u_correo_codigo'])
        self.assertTrue(all(u['usuario_password_hash'].startswith('@RUNTIME_BCRYPT:') for u in d['rows']['u_usuario']))
if __name__=='__main__':unittest.main()
