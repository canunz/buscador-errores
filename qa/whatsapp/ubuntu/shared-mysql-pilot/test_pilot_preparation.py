"""Pruebas de preparación; no conecta al servidor ni inicia unidades."""
from pathlib import Path
import importlib.util,subprocess,sys,unittest
from unittest.mock import patch
HERE=Path(__file__).resolve().parent

class PreparacionPiloto(unittest.TestCase):
    def test_limites_y_credenciales(self):
        s=(HERE/'dimabug-wa-pilot.service').read_text();g=(HERE/'dimabug-wa-pilot-graph.service').read_text()
        for value in ['MemoryMax=512M','MemorySwapMax=0','-Xmx192m','MaxMetaspaceSize=128m','Restart=on-failure','IPAddressDeny=any','IPAddressAllow=localhost','configtree:%d/','LoadCredential=application.properties:']:
            self.assertIn(value,s)
        self.assertIn('MemoryMax=96M',g);self.assertIn('--bind=127.0.0.1',g)
        self.assertNotIn('graph-token',s+g)
        self.assertNotIn('Wants=mysql.service',s)
        self.assertNotIn('mysql.service\nExecStart',s)
    def test_solo_schema_y_tls_sin_degradacion(self):
        s=(HERE/'application.properties.template').read_text()
        self.assertIn('127.0.0.1:3306/__QA_DATABASE__',s)
        self.assertIn('sslMode=VERIFY_IDENTITY',s)
        self.assertIn('spring.jpa.hibernate.ddl-auto=validate',s)
        for flag in ['envio-real','medios','conexion-meta']:self.assertIn('dimabug.whatsapp.meta-qa.'+flag+'=false',s)
        self.assertNotIn('graph-token=',s)
    def test_provision_requiere_autorizacion_sin_ejecutar(self):
        r=subprocess.run([sys.executable,str(HERE/'provision.py')],capture_output=True,text=True)
        self.assertEqual(r.returncode,0);self.assertEqual(r.stdout.strip(),'PLAN_ONLY')
        r=subprocess.run([sys.executable,str(HERE/'provision.py'),'--execute','--approval','NO'],capture_output=True,text=True)
        self.assertEqual(r.returncode,2)
    def test_piloto_plan_no_inicia_servicios(self):
        r=subprocess.run([sys.executable,str(HERE/'pilot.py')],capture_output=True,text=True)
        self.assertEqual(r.returncode,0);self.assertTrue(r.stdout.startswith('PLAN_ONLY'))
    def test_umbral_y_muestra_local_sin_datos_reales(self):
        spec=importlib.util.spec_from_file_location('pilot_test',HERE/'pilot.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        self.assertEqual(module.p95([{'staging_ms':i} for i in range(1,101)]),95)
        import json
        body=json.loads(module.event('sintetico'))
        self.assertEqual(body['entry'][0]['id'],'qa-pilot-waba')
        self.assertEqual(body['entry'][0]['changes'][0]['value']['messages'][0]['text']['body'],'Hola')
        with patch.object(module,'sample',return_value={'available_mib':1000}):
            with self.assertRaisesRegex(RuntimeError,'MEMAVAILABLE_BELOW_1_GIB'):module.observe(1)
    def test_reversion_no_reinicia_mysql_ni_borra_esquemas(self):
        s=(HERE/'rollback.sh').read_text()
        self.assertIn('ACCOUNT LOCK',s)
        self.assertNotIn('DROP ',s);self.assertNotIn('restart',s);self.assertNotIn('stop mysql',s)
    def test_uri_truststore_igual_en_comprobador_y_jdbc(self):
        spec=importlib.util.spec_from_file_location('repair_tls_test',HERE/'repair_tls.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
        text=(HERE/'application.properties.template').read_text()
        value=text.split('trustCertificateKeyStoreUrl=',1)[1].split('&',1)[0].replace('${CREDENTIALS_DIRECTORY}','/etc/dimabug-wa-pilot')
        self.assertEqual(value,m.jdbc_store_uri(Path('/etc/dimabug-wa-pilot/mysql-truststore.p12')))
        self.assertEqual(value,'file:/etc/dimabug-wa-pilot/mysql-truststore.p12')
    def test_supervisor_bloquea_cuenta_en_salida_y_corte(self):
        s=(HERE/'run_pilot.sh').read_text()
        self.assertIn('trap cleanup EXIT',s)
        self.assertIn('DETENER_Y_BLOQUEAR_SOLO_QA',s)
        self.assertIn('--on-active=45m',s)
        self.assertIn("ACCOUNT UNLOCK",s)
        self.assertIn('qa_account_locked',s)
    def startup_module(self):
        spec=importlib.util.spec_from_file_location('startup_check_test',HERE/'startup_check.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
    def test_ajuste_arranque_cambia_solo_timeout(self):
        m=self.startup_module();text=(HERE/'application.properties.template').read_text()
        result=m.timeout_config(text)
        self.assertEqual(result,text.replace('spring.datasource.hikari.connection-timeout=3000\n','spring.datasource.hikari.connection-timeout=30000\n'))
        self.assertEqual(m.timeout_config(result),result)
        with self.assertRaisesRegex(RuntimeError,'UNEXPECTED_QA_TIMEOUT_CONFIG'):m.timeout_config(text+'\nspring.datasource.hikari.connection-timeout=3000\n')
    def test_arranque_unico_sin_eventos_ni_procesamiento(self):
        m=self.startup_module();override=m.runtime_override((HERE/'dimabug-wa-pilot.service').read_text())
        self.assertIn('Restart=no',override)
        for k in ['recepcion','procesamiento','envio','envio-real','medios','conexion-meta']:self.assertIn('--dimabug.whatsapp.meta-qa.'+k+'=false',override)
        r=subprocess.run([sys.executable,str(HERE/'startup_check.py')],capture_output=True,text=True)
        self.assertEqual(r.returncode,0);self.assertTrue(r.stdout.startswith('PLAN_ONLY'))

if __name__=='__main__':unittest.main()
