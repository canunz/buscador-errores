import unittest,subprocess,sys
from pathlib import Path
from unittest.mock import patch
import meta_config as m
import temporary_tunnel as t

class PreparacionMeta(unittest.TestCase):
    def complete(self):
        d=m.initial();d.update(app_secret='1'*32,waba_id='12345678901',phone_number_id='12345678902')
        d['authorized_participants']=[{'alias':'qa-persona','tel':'56911111111','bsuid':'US.qa-prueba'}]
        return d
    def test_archivo_inicial_incompleto_y_cerrado(self):
        d=m.initial();status,_=m.check(d)
        self.assertEqual(status['state'],'INCOMPLETE');self.assertFalse(status['public_reception'])
        with self.assertRaises(ValueError):m.property_values(d)
    def test_config_completa_no_autoriza_publicacion_o_envio(self):
        d=self.complete();values=m.property_values(d)
        for flag in ['recepcion','procesamiento','medios','envio','envio-real','conexion-meta']:
            self.assertEqual(values['dimabug.whatsapp.meta-qa.'+flag],'false')
        status,_=m.check(d);self.assertFalse(status['meta_connected'])
        self.assertNotIn(d['app_secret'],str(status));self.assertNotIn('56911111111',str(status))
    def test_no_se_amplia_allowlist_ni_se_normaliza_identidad(self):
        d=self.complete();d['authorized_participants'][0]['tel']='+56911111111'
        self.assertEqual(m.check(d)[0]['state'],'INCOMPLETE')
        d=self.complete();d['authorized_participants'].append(dict(d['authorized_participants'][0]))
        self.assertEqual(m.check(d)[0]['state'],'INCOMPLETE')
    def test_bandera_en_archivo_no_es_autorizacion(self):
        d=self.complete();d['public_reception_authorized']=True
        with self.assertRaises(ValueError):m.property_values(d)
    def test_modo_preparacion_prohibe_tunel_antes_de_crear_recursos(self):
        p=subprocess.run([sys.executable,str(Path(__file__).with_name('resources_linux.py')),'build-inexistente','--variant','optimized','--https','--prepare-reception','--temporary-tunnel','--cloudflared-exe','inexistente','--existing-qa-tls','inexistente'],capture_output=True,text=True)
        self.assertEqual(p.returncode,2)
        self.assertIn('prohibe crear un tunel',p.stderr)
    def test_publicacion_real_rechazada_sin_autorizacion(self):
        with self.assertRaises(ValueError):t.Session(harness=None,props={},exe='inexistente',port=12345,minutes=20,real_meta=True,public_authorized=False)
        p=subprocess.run([sys.executable,str(Path(__file__).with_name('resources_linux.py')),'build-inexistente','--variant','optimized','--https','--temporary-tunnel','--cloudflared-exe','inexistente','--existing-qa-tls','inexistente','--real-meta-config','inexistente'],capture_output=True,text=True)
        self.assertEqual(p.returncode,2);self.assertIn('autorizacion publica expresa',p.stderr)
    def test_autorizacion_de_20_no_abre_ventana_de_30(self):
        p=subprocess.run([sys.executable,str(Path(__file__).with_name('resources_linux.py')),'build-inexistente','--variant','optimized','--https','--temporary-tunnel','--window-minutes','30','--cloudflared-exe','inexistente','--existing-qa-tls','inexistente','--real-meta-config','inexistente','--public-approval','AUTORIZO_RECEPCION_REAL_META_20_MINUTOS'],capture_output=True,text=True)
        self.assertEqual(p.returncode,2)
        self.assertIn('duracion seleccionada',p.stderr)
    def test_ventana_30_conserva_validacion_binario_y_autorizacion(self):
        with self.assertRaises(ValueError):
            t.Session(harness=None,props={},exe='inexistente',port=12345,minutes=30,real_meta=True,public_authorized=False)
        with patch.object(Path,'read_bytes',return_value=b'binario-no-verificado'):
            with self.assertRaisesRegex(ValueError,'Binario cloudflared no verificado'):
                t.Session(harness=None,props={},exe='inexistente',port=12345,minutes=60,real_meta=True,public_authorized=True)
    def test_chequeos_meta_no_insertan_evento_valido_sintetico(self):
        calls=[]
        def fake(config,method,path,body=None,**kwargs):
            calls.append((method,body,kwargs))
            return (200,b'000123',1) if 'hub.challenge=' in path else (403,b'{}',1)
        with patch('tunnel_client.send',fake),patch.object(t.time,'sleep'):
            result=t.Session.real_meta_checks(object(),{'verify_token':'token-sintetico'})
        self.assertTrue(result['no_test_message_inserted']);self.assertTrue(result['real_message_not_yet_verified'])
        for method,body,options in calls:
            self.assertFalse(options['auth'])
            if method=='POST':self.assertTrue(options.get('mutate') or options.get('signed') is False)
    def test_activacion_espera_configuracion_manual_sin_pruebas_post(self):
        session=object.__new__(t.Session);session.real_meta=True
        with patch('tunnel_client.send',side_effect=AssertionError('No enviar pruebas')),patch.object(t,'self_test',side_effect=AssertionError('No pruebas sinteticas')):
            result=session.activation_checks({})
        self.assertTrue(result['awaiting_manual_meta_setup']);self.assertTrue(result['no_post_sent'])

if __name__=='__main__':unittest.main()
