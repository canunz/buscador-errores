import subprocess,sys,tempfile,unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from etapa_b_config import prepare,TOKEN_PROPERTY

class EtapaB(unittest.TestCase):
    def props(self):
        return {'dimabug.whatsapp.autoatencion.habilitada':'true','dimabug.whatsapp.meta-qa.medios':'false','dimabug.whatsapp.meta-qa.conexion-meta':'false','dimabug.whatsapp.meta-qa.envio-real':'false'}
    def test_token_solo_montaje_lectura_no_en_propiedades(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'token.txt';p.write_text('TOKEN_SINTETICO_NO_REAL\n',encoding='utf-8');initial=self.props()
            with patch('meta_graph_preflight.subprocess.run',return_value=SimpleNamespace(returncode=128)):
                props,mount=prepare(initial,p)
            self.assertEqual(initial['dimabug.whatsapp.meta-qa.envio-real'],'false')
            self.assertEqual(props['dimabug.whatsapp.meta-qa.envio-real'],'true')
            self.assertNotIn(TOKEN_PROPERTY,props)
            self.assertNotIn('TOKEN_SINTETICO_NO_REAL',str(props)+str(mount))
            self.assertIn('readonly',mount[1]);self.assertIn('/run/qa-secrets/'+TOKEN_PROPERTY,mount[1])
            self.assertEqual(props['spring.config.import'],'configtree:/run/qa-secrets/')
            self.assertEqual(props['dimabug.whatsapp.meta-qa.graph-version'],'v27.0')
    def test_medios_y_conexion_no_pueden_activarse(self):
        for key in ['medios','conexion-meta']:
            p=self.props();p['dimabug.whatsapp.meta-qa.'+key]='true'
            with self.assertRaisesRegex(ValueError,'MEDIA_MUST_REMAIN_DISABLED'):prepare(p,'inexistente')
    def test_autoatencion_obligatoria(self):
        p=self.props();p['dimabug.whatsapp.autoatencion.habilitada']='false'
        with self.assertRaisesRegex(ValueError,'AUTOATENCION_REQUIRED'):prepare(p,'inexistente')
    def test_archivo_git_rechazado(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'token.txt';p.write_text('TOKEN_SINTETICO_NO_REAL')
            with patch('meta_graph_preflight.subprocess.run',return_value=SimpleNamespace(returncode=0)):
                with self.assertRaisesRegex(ValueError,'TOKEN_FILE_INSIDE_GIT'):prepare(self.props(),p)
    def test_bom_y_espacios_rechazados_sin_exponer_token(self):
        for text in [' TOKEN_SINTETICO_NO_REAL','TOKEN_SINTETICO_NO_REAL ','\ufeffTOKEN_SINTETICO_NO_REAL']:
            with tempfile.TemporaryDirectory() as d:
                p=Path(d)/'token.txt';p.write_text(text,encoding='utf-8')
                with patch('meta_graph_preflight.subprocess.run',return_value=SimpleNamespace(returncode=128)):
                    with self.assertRaisesRegex(ValueError,'TOKEN_FILE_EXTRA_WHITESPACE'):prepare(self.props(),p)
    def test_autorizacion_a_no_inicia_etapa_b(self):
        r=subprocess.run([sys.executable,str(Path(__file__).with_name('Run-AutoatencionEtapaA.py')),'--stage','B','--token-file','inexistente','--approval','AUTORIZO_AUTOATENCION_QA_ETAPA_A_60_MINUTOS'],capture_output=True,text=True)
        self.assertEqual(r.returncode,2);self.assertIn('Autorizacion expresa requerida',r.stderr)

if __name__=='__main__':unittest.main()
