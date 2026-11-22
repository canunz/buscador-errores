import base64,hashlib,hmac,json,os,subprocess,sys,unittest
from pathlib import Path
from unittest.mock import patch
import temporary_tunnel as t
import tunnel_client as client

class TunelSeguro(unittest.TestCase):
    def test_tls_validado_y_metricas_loopback(self):
        command=t.cloudflared_command('cloudflared.exe',12345,'ca.pem')
        self.assertIn('https://127.0.0.1:12345',command)
        self.assertIn('--origin-ca-pool',command);self.assertIn('--origin-server-name',command)
        self.assertNotIn('--no-tls-verify',command);self.assertNotIn('debug',command)
        self.assertEqual(command[command.index('--metrics')+1],'127.0.0.1:0')
    def test_duracion_no_autorizada_rechazada(self):
        with self.assertRaises(ValueError):t.Session(harness=None,props={},exe='inexistente',port=12345,minutes=90)
    def test_cliente_no_envia_secreto_por_http(self):
        cfg={'url':'http://test.trycloudflare.com'}
        with patch.object(client.http.client,'HTTPSConnection',side_effect=AssertionError('No conectar')):
            with self.assertRaises(ValueError):client.send(cfg,'GET','/')
    def test_hmac_sobre_bytes_y_basic_en_cabecera(self):
        cfg={'url':'https://test.trycloudflare.com','username':'qa','password':'solo-prueba','app_secret':'secreto-sintetico'}
        captured={}
        class Reply:
            status=200
            def read(self,n):return b'{}'
        class Connection:
            def __init__(self,*a,**k):pass
            def request(self,method,path,body,headers):captured.update(method=method,path=path,body=body,headers=headers)
            def getresponse(self):return Reply()
            def close(self):pass
        body=b' {"text":"sintetico"}\r\n'
        with patch.object(client.http.client,'HTTPSConnection',Connection):client.send(cfg,'POST','/webhooks/whatsapp',body,mutate=True)
        self.assertEqual(captured['body'],b' '+body)
        self.assertEqual(captured['headers']['X-Hub-Signature-256'],'sha256='+hmac.new(cfg['app_secret'].encode(),body,hashlib.sha256).hexdigest())
        self.assertEqual(captured['headers']['Authorization'],'Basic '+base64.b64encode(b'qa:solo-prueba').decode())
        self.assertNotIn('solo-prueba',captured['path'])
    @unittest.skipUnless(os.name=='nt','Windows Job Object')
    def test_cierre_job_termina_hijo_sin_red(self):
        child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(20)'],creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            job=t.KillOnCloseJob(child);job.close();child.wait(timeout=5)
            self.assertIsNotNone(child.returncode)
        finally:
            if child.poll() is None:child.terminate();child.wait(timeout=5)

if __name__=='__main__':unittest.main()
