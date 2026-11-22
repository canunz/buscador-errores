"""Cliente sintetico. Secretos solo en archivo privado; nunca en argumentos/salida."""
import argparse,base64,concurrent.futures,hashlib,hmac,http.client,json,sys,time,uuid
from pathlib import Path
from urllib.parse import urlsplit

ROOT=Path(__file__).resolve().parents[3]

def load_run(path):
    run=Path(path).resolve()
    if not run.is_relative_to(ROOT/'qa/runtime') or not run.name.startswith('wa-tunnel-'):raise ValueError('Ruta de QA no valida')
    return run

def send(config,method,path,body=None,*,auth=True,signed=True,mutate=False,timeout=20,require_gateway=False):
    u=urlsplit(config['url'])
    if u.scheme!='https' or not (u.hostname or '').endswith('.trycloudflare.com') or u.username or u.password or u.port not in (None,443):raise ValueError('URL publica de QA no valida')
    headers={}
    if auth:headers['Authorization']='Basic '+base64.b64encode((config['username']+':'+config['password']).encode()).decode()
    if body is not None:
        headers['Content-Type']='application/json'
        if signed:headers['X-Hub-Signature-256']='sha256='+hmac.new(config['app_secret'].encode(),body,hashlib.sha256).hexdigest()
        if mutate:body=b' '+body
    c=http.client.HTTPSConnection(u.hostname,timeout=timeout)
    start=time.monotonic()
    try:
        c.request(method,path,body,headers);r=c.getresponse();payload=r.read(16384)
        if require_gateway and r.getheader('X-WA-QA-Gateway')!='nginx':raise RuntimeError('Respuesta no confirmada por gateway QA')
        return r.status,payload,round((time.monotonic()-start)*1000)
    finally:c.close()

def message(config,event_id):
    return json.dumps({'object':'whatsapp_business_account','entry':[{'id':'qa-waba-local','changes':[{'field':'messages','value':{'messaging_product':'whatsapp','metadata':{'phone_number_id':'qa-numero-local'},'messages':[{'id':event_id,'from':'100000000000001','timestamp':str(int(time.time())),'type':'text','text':{'body':'SOLO_EVENTO_SINTETICO_TUNEL'}}]}}]}]},ensure_ascii=False).encode()

def self_test(config):
    results={}
    status,_,_=send(config,'GET','/webhooks/whatsapp',auth=False)
    assert status==401, 'Webhook sin autenticacion no cerrado'
    results['without_auth']=status;time.sleep(.7)
    for route in ['/api/publico/chatbot/consultas','/actuator/health','/swagger-ui/index.html','/webhooks/%77hatsapp']:
        status,_,_=send(config,'GET',route)
        assert status==403,'Ruta interna accesible'
        time.sleep(.7)
    results['internal_routes']=403
    query='/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token='+config['verify_token']+'&hub.challenge=000123'
    status,payload,_=send(config,'GET',query);assert status==200 and payload==b'000123','Challenge incorrecto'
    results['challenge_exact']=True;time.sleep(.7)
    body=message(config,'qa-tunnel-'+uuid.uuid4().hex)
    status,_,_=send(config,'POST','/webhooks/whatsapp',body,mutate=True)
    assert status==403,'Cuerpo alterado aceptado';results['tampered_body']=status;time.sleep(.7)
    status,_,_=send(config,'POST','/webhooks/whatsapp',body,signed=False)
    assert status==403,'Firma ausente aceptada';results['missing_signature']=status
    time.sleep(2)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        values=list(pool.map(lambda _:send(config,'POST','/webhooks/whatsapp',body),range(4)))
    assert all(status==200 for status,_,_ in values),'Rechazo de duplicados'
    receipts=[json.loads(payload) for _,payload,_ in values]
    assert sum(r['aceptados'] for r in receipts)==1 and sum(r['duplicados'] for r in receipts)==3,'Deduplicacion incorrecta'
    results.update(accepted=1,duplicates=3,post_elapsed_ms=[ms for _,_,ms in values])
    return results

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--stop',action='store_true');p.add_argument('--self-test',action='store_true');a=p.parse_args()
    run=load_run(a.run)
    if a.stop:
        (run/'stop.request').touch(exist_ok=True);print('CIERRE SOLICITADO');return
    state=json.loads((run/'tunnel-status.json').read_text())
    if state.get('state')!='ACTIVE' or time.time()>=state['expires_unix']:raise SystemExit('Tunel no activo o expirado')
    if state.get('real_reception'):raise SystemExit('Ventana Meta real: este cliente no envia mensajes sinteticos; --stop sigue disponible')
    config=json.loads((run/'private/client.json').read_text())
    if a.self_test:print(json.dumps(self_test(config)))
    else:
        body=message(config,'qa-tunnel-'+uuid.uuid4().hex)
        status,payload,ms=send(config,'POST','/webhooks/whatsapp',body)
        result={'status':status,'elapsed_ms':ms}
        if status==200:result['receipt']=json.loads(payload)
        print(json.dumps(result))

if __name__=='__main__':
    try:main()
    except Exception as e:
        print(json.dumps({'error_type':type(e).__name__,'message':'No se completo la prueba; no se muestran secretos o respuestas crudas'}))
        raise SystemExit(1)
