"""Pruebas sinteticas: no lectura /proc, HTTP, SSH ni medicion real."""
import contextlib,io,json,unittest
from unittest.mock import patch
import baseline_24h as b

class BaselineSeguro(unittest.TestCase):
    def test_por_defecto_plan_sin_lectura_o_red(self):
        out=io.StringIO()
        with patch.object(b,'read_sample',side_effect=AssertionError('No medir')),patch.object(b,'probe',side_effect=AssertionError('No conectar')),contextlib.redirect_stdout(out):
            self.assertEqual(b.main([]),0)
        result=json.loads(out.getvalue());self.assertEqual(result['mode'],'PLAN_ONLY')
        self.assertEqual(result['duration_seconds'],86400);self.assertFalse(result['http_enabled'])
    def test_activacion_accidental_rechazada(self):
        with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):b.main(['--execute'])
    def test_destinos_no_aprobados_rechazados(self):
        for url in ['http://staging.dimabug.casol.cl/health','https://meta.example/health','https://staging.dimabug.casol.cl/health?token=secreto','https://usuario:secreto@staging.dimabug.casol.cl/health','https://staging.dimabug.casol.cl:8081/health','https://staging.dimabug.casol.cl/__PENDIENTE__']:
            with self.subTest(url=url),self.assertRaises(ValueError):b.parse_targets({'targets':[{'name':'staging','url':url}]})
    def test_destino_valido_solo_se_valida_no_se_visita(self):
        targets=b.parse_targets({'targets':[{'name':'staging','url':'https://staging.dimabug.casol.cl/health'}]})
        self.assertEqual(len(targets),1)
    def test_cpu_no_duplica_guest_y_detecta_reset(self):
        before=b.cpu_counters('cpu 100 0 20 200 10 0 0 5 50 0\n')
        after=b.cpu_counters('cpu 150 0 40 300 20 0 0 10 60 0\n')
        self.assertEqual(before['total'],335)
        self.assertIsNotNone(b.cpu_delta(before,after));self.assertIsNone(b.cpu_delta(after,before))
    def test_memoria_e_io(self):
        m=b.meminfo('MemTotal: 4000000 kB\nMemAvailable: 2200000 kB\nSwapTotal: 2000 kB\nSwapFree: 1500 kB\n')
        self.assertEqual(m['available_kib'],2200000);self.assertEqual(m['swap_used_kib'],500)
        before={'reads':10,'writes':10,'read_ms':20,'write_ms':20,'busy_ms':100}
        after={'reads':20,'writes':20,'read_ms':40,'write_ms':60,'busy_ms':200}
        self.assertEqual(b.io_delta(before,after,10)['operations_s'],2)
        self.assertIsNone(b.io_delta(after,before,10))
    def test_errores_http_no_exponen_excepciones(self):
        def failed(*args,**kwargs):raise OSError('URL_CON_TOKEN_PRIVADO')
        r=b.probe({'name':'staging','url':'https://staging.dimabug.casol.cl/health'},failed)
        self.assertEqual(r['error_type'],'OSError');self.assertNotIn('PRIVADO',json.dumps(r))
    def test_http_sin_contenido_credenciales_o_redirects(self):
        calls=[]
        class Response:
            status=302
            def read(self):raise AssertionError('No leer cuerpo')
            def close(self):calls.append('response_closed')
        class Connection:
            def __init__(self,host,port,timeout,context):
                self.asserted=context.check_hostname
                calls.append((host,port,timeout,self.asserted))
            def request(self,method,path,headers):
                calls.append((method,path,sorted(headers)))
            def getresponse(self):return Response()
            def close(self):calls.append('connection_closed')
        r=b.probe({'name':'staging','url':'https://staging.dimabug.casol.cl/health'},Connection)
        self.assertEqual(r['status'],302);self.assertEqual(sum(isinstance(c,tuple) and c[0]=='GET' for c in calls),1)
        self.assertTrue(calls[0][3]);self.assertNotIn('url',r);self.assertNotIn('body',r)
    def test_resumen_incompleto_no_aprueba_y_no_suma_disco(self):
        summary=b.Summary()
        row={'memory':{'available_kib':2200000,'swap_used_kib':0},'disks':[{'device':'8:1','paths':['/var/lib','/var/log'],'available_bytes':10**10}],'probes':[{'name':'staging','status':200,'elapsed_ms':100},{'name':'staging','status':503,'elapsed_ms':5}]}
        summary.add(row);summary.add({**row,'interval_seconds':120})
        r=summary.result(86400,'completed')
        self.assertFalse(r['coverage_24h']);self.assertFalse(r['deployment_approved'])
        self.assertEqual(len(r['disk_minima']),1)
        self.assertEqual(r['http']['staging']['p95_ms'],100)
        self.assertIn('503',r['http']['staging']['outcomes'])
    def test_sin_http_latencias_siguen_pendientes(self):
        r=b.Summary().result(0,'interrupted')
        self.assertTrue(r['latencies_pending']);self.assertFalse(r['coverage_24h'])

if __name__=='__main__':unittest.main()
