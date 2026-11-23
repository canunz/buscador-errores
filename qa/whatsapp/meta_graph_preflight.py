"""Lectura privada y comprobaciones GET de Graph. Nunca envía mensajes ni activa QA."""
from pathlib import Path
import argparse,http.client,json,subprocess

def read_token(path):
    p=Path(path).resolve()
    if not p.is_file():raise ValueError('TOKEN_FILE_MISSING')
    repo=subprocess.run(['git','-C',str(p.parent),'rev-parse','--show-toplevel'],capture_output=True,text=True)
    if repo.returncode==0:raise ValueError('TOKEN_FILE_INSIDE_GIT')
    token=p.read_text(encoding='utf-8-sig').strip()
    # Token opaco: no se interpreta su contenido. El cliente Java actual limita a 1024.
    if not token or len(token)>1024 or any(ord(c)<33 or ord(c)>126 for c in token):raise ValueError('TOKEN_FILE_INVALID')
    return token

def spring_properties(path):
    """Solo en memoria. El futuro lanzador decide el montaje privado; no activa envío."""
    return {'dimabug.whatsapp.meta-qa.graph-token':read_token(path)}

def graph_get(path,token=None):
    connection=http.client.HTTPSConnection('graph.facebook.com',timeout=12)
    try:
        headers={'Authorization':'Bearer '+token} if token else {}
        connection.request('GET',path,headers=headers)
        response=connection.getresponse();body=response.read(65537)
        if len(body)>65536:raise ValueError('GRAPH_RESPONSE_LIMIT')
        try:data=json.loads(body)
        except Exception:data={}
        return response.status,data
    finally:connection.close()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--token-file',required=True,type=Path)
    parser.add_argument('--qa-config',type=Path);parser.add_argument('--check-assets',action='store_true')
    args=parser.parse_args()
    try:
        token=read_token(args.token_file)
        result={'token_file_available':True,'outside_git':True,'token_loaded_in_memory':True,'real_send_enabled':False}
        if args.check_assets:
            if args.qa_config is None:raise ValueError('QA_CONFIG_REQUIRED')
            c=json.loads(args.qa_config.read_text(encoding='utf-8-sig'))
            for key in ['waba_id','phone_number_id']:
                if not isinstance(c.get(key),str) or not c[key].isdigit():raise ValueError('QA_IDENTIFIER_INVALID')
            status,data=graph_get('/v27.0/'+c['phone_number_id']+'?fields=id',token)
            result['phone_number']={'http':status,'matches':status==200 and data.get('id')==c['phone_number_id'],'error_code':data.get('error',{}).get('code')}
            status,data=graph_get('/v27.0/'+c['waba_id']+'/phone_numbers?fields=id&limit=100',token)
            result['waba']={'http':status,'contains_authorized_phone':status==200 and any(p.get('id')==c['phone_number_id'] for p in data.get('data',[])),'error_code':data.get('error',{}).get('code')}
            status,data=graph_get('/v27.0/me/permissions',token)
            result['messaging_permission']={'http':status,'granted':any(p.get('permission')=='whatsapp_business_messaging' and p.get('status')=='granted' for p in data.get('data',[])) if status==200 else None,'error_code':data.get('error',{}).get('code')}
            result['https_host_reachable']=True
            result['application_identity_verified']=False # Requiere App ID esperado e introspección autorizada.
        print(json.dumps(result))
    except Exception as error:
        # Nunca imprimir texto de excepción de red, ruta, URL ni respuestas de Meta.
        known={'TOKEN_FILE_MISSING','TOKEN_FILE_INSIDE_GIT','TOKEN_FILE_INVALID','QA_CONFIG_REQUIRED','QA_IDENTIFIER_INVALID','GRAPH_RESPONSE_LIMIT'}
        code=str(error) if isinstance(error,ValueError) and str(error) in known else type(error).__name__
        print(json.dumps({'prepared':False,'error_code':code,'real_send_enabled':False}));return 1
    return 0

if __name__=='__main__':raise SystemExit(main())
