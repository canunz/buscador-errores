"""Pruebas PREPARADAS. Solo loopback, SNI QA, CA explícita y secretos ficticios.
No se ejecutan automáticamente ni instalan/inician Nginx.
"""
import argparse,concurrent.futures,hashlib,hmac,http.client,json,os,socket,ssl,time,uuid
from pathlib import Path

HOST='wa-qa.dimabug.casol.cl'

class LocalHttps(http.client.HTTPSConnection):
    def connect(self):
        plain=socket.create_connection(('127.0.0.1',self.port),timeout=self.timeout)
        self.sock=self._context.wrap_socket(plain,server_hostname=HOST)

def main():
    p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=18443);p.add_argument('--ca-file',type=Path,required=True)
    p.add_argument('--phase',choices=['paused','receive-only'],default='paused');a=p.parse_args()
    if os.getenv('WA_QA_NGINX_TEST_APPROVED')!='SI_SINTETICO_LOCAL' or not 1024<=a.port<=65535:raise SystemExit('Falta aprobación local sintética o puerto QA permitido')
    token=os.environ.get('WA_QA_TEST_VERIFY_TOKEN');secret=os.environ.get('WA_QA_TEST_APP_SECRET')
    if not token or not secret:raise SystemExit('Faltan secretos ficticios en el entorno; no pasarlos por CLI')
    tls=ssl.create_default_context(cafile=str(a.ca_file));tls.minimum_version=ssl.TLSVersion.TLSv1_2
    def request(method,path,body=None,signature=None,declared=None):
        c=LocalHttps(HOST,port=a.port,context=tls,timeout=30)
        try:
            h={'Host':HOST}
            if body is not None:h['Content-Type']='application/json'
            if signature is not None:h['X-Hub-Signature-256']=signature
            if declared is not None:h['Content-Length']=str(declared)
            c.request(method,path,body=body,headers=h);r=c.getresponse();b=r.read()
            assert r.getheader('X-WA-QA-Gateway')=='nginx'
            return r.status,b
        finally:c.close()
    from urllib.parse import urlencode
    query='/webhooks/whatsapp?'+urlencode({'hub.mode':'subscribe','hub.verify_token':token,'hub.challenge':'000321'})
    status,b=request('GET',query);assert status==200 and b==b'000321' # Preflight, antes de POST.
    results=[{'caso':'GET exacto con TLS validado','estado':'APROBADO'}]
    incorrecto='/webhooks/whatsapp?'+urlencode({'hub.mode':'subscribe','hub.verify_token':token+'-incorrecto','hub.challenge':'x'})
    status,_=request('GET',incorrecto);assert status==403
    results.append({'caso':'token incorrecto','estado':'APROBADO'})
    for path in ['/','/api','/api/publico/chatbot/consultas','/actuator','/actuator/health','/swagger-ui/index.html','/v3/api-docs','/webhooks/whatsapp/','/webhooks/%77hatsapp']:
        assert request('GET',path)[0] in [400,403]
    for method in ['PUT','PATCH','DELETE','HEAD','OPTIONS']:assert request(method,'/webhooks/whatsapp')[0]==403
    results.append({'caso':'rutas y métodos cerrados','estado':'APROBADO'})
    body=json.dumps({'object':'whatsapp_business_account','entry':[{'id':'qa-waba-ubuntu','changes':[{'field':'messages','value':{
        'messaging_product':'whatsapp','metadata':{'phone_number_id':'qa-numero-ubuntu'},'messages':[{
        'id':'wamid.qa-'+uuid.uuid4().hex,'from':'100000000000001','timestamp':str(int(time.time())),'type':'text','text':{'body':'Error sintético á'}}]}}]}]},ensure_ascii=False).encode()
    signed='sha256='+hmac.new(secret.encode(),body,hashlib.sha256).hexdigest()
    assert request('POST','/webhooks/whatsapp',body,'sha256='+'0'*64)[0]==403
    assert request('POST','/webhooks/whatsapp',b'',signed,3*1024*1024+1)[0]==413
    results.append({'caso':'firma y tamaño','estado':'APROBADO'})
    if a.phase=='paused':assert request('POST','/webhooks/whatsapp',body,signed)[0]==503
    else:
        # Configurar previamente recepción SOLA y los activos ficticios de este script.
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            replies=list(pool.map(lambda _:request('POST','/webhooks/whatsapp',body,signed),range(4)))
        assert all(status==200 for status,_ in replies)
        values=[json.loads(b) for _,b in replies]
        assert sum(v['aceptados'] for v in values)==1 and sum(v['duplicados'] for v in values)==3
    results.append({'caso':'fase '+a.phase,'estado':'APROBADO'})
    print(json.dumps({'pruebas':results,'conexion':'solo 127.0.0.1 con SNI QA','nginx_en_recorrido':True,
        'commit_db':'REQUIERE contraste agregado del auditor SQL','meta':False,'logs_reales':'REQUIEREN revisión con marcadores'},indent=2))

if __name__=='__main__':
    try:main()
    except Exception as e:raise SystemExit('Prueba NO aprobada: '+type(e).__name__+'; detalles sensibles omitidos')
