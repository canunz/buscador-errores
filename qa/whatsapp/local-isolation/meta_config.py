"""Preparacion privada de datos Meta. Nunca publica, conecta o imprime secretos."""
import argparse,json,os,re,secrets,subprocess,uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]

def initial():
    return {'schema_version':1,'scope':'QA_TEST_ASSETS_ONLY','app_secret':'','waba_id':'','phone_number_id':'',
        'verify_token':secrets.token_urlsafe(32),
        'authorized_participants':[{'alias':'carolina-qa','tel':'','bsuid':''}],
        'public_reception_authorized':False,'note':'Completar localmente. No pegar secretos en el chat. No cambiar autorizacion.'}

def check(data):
    missing=[];invalid=[];parts=[];seen=set()
    if data.get('scope')!='QA_TEST_ASSETS_ONLY':invalid.append('scope')
    if data.get('public_reception_authorized') is not False:invalid.append('public_reception_authorized_must_remain_false')
    for name in ['app_secret','verify_token']:
        value=data.get(name)
        if not value:missing.append(name)
        elif not isinstance(value,str) or not 16<=len(value)<=256 or not value.strip() or any(ord(c)<32 or ord(c)==127 for c in value) or value.startswith('__'):invalid.append(name)
    for name in ['waba_id','phone_number_id']:
        value=data.get(name)
        if not value:missing.append(name)
        elif not isinstance(value,str) or not re.fullmatch(r'[0-9]{1,128}',value):invalid.append(name)
    participants=data.get('authorized_participants')
    if not isinstance(participants,list) or not participants:
        missing.append('authorized_participants');participants=[]
    for i,p in enumerate(participants):
        if not isinstance(p,dict):invalid.append(f'participant_{i}');continue
        alias=p.get('alias','');tel=p.get('tel','');bsuid=p.get('bsuid','')
        if not isinstance(alias,str) or not re.fullmatch(r'[A-Za-z0-9_.:-]{1,128}',alias):invalid.append(f'participant_{i}_alias')
        if not tel and not bsuid:missing.append(f'participant_{i}_tel_or_bsuid')
        for kind,value,pattern in [('tel',tel,r'[0-9]{5,20}'),('bsuid',bsuid,r'[A-Za-z0-9_.:/+-]{1,128}')]:
            if not value:continue
            if not isinstance(value,str) or not re.fullmatch(pattern,value):invalid.append(f'participant_{i}_{kind}');continue
            key=kind+':'+value
            if key in seen:invalid.append(f'participant_{i}_duplicate_identifier')
            seen.add(key);parts.append(key+'='+alias)
    return {'state':'COMPLETE_UNVERIFIED_META' if not missing and not invalid else 'INCOMPLETE',
        'missing_fields':missing,'invalid_fields':invalid,'participants_count':len(participants),
        'public_reception':False,'meta_connected':False},parts

def property_values(data):
    status,parts=check(data)
    if status['state']!='COMPLETE_UNVERIFIED_META':raise ValueError('Configuracion Meta incompleta o invalida; no mostrar valores')
    return {'dimabug.whatsapp.meta-qa.app-secret':data['app_secret'],'dimabug.whatsapp.meta-qa.verify-token':data['verify_token'],
        'dimabug.whatsapp.meta-qa.waba':data['waba_id'],'dimabug.whatsapp.meta-qa.numero':data['phone_number_id'],
        'dimabug.whatsapp.meta-qa.participantes':','.join(parts),
        **{'dimabug.whatsapp.meta-qa.'+key:'false' for key in ['recepcion','procesamiento','medios','envio','envio-real','conexion-meta']}}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    group=p.add_mutually_exclusive_group(required=True);group.add_argument('--init',action='store_true');group.add_argument('--check',type=Path)
    a=p.parse_args()
    if a.init:
        folder=ROOT/'qa/runtime'/('meta-preparation-20261118-'+uuid.uuid4().hex[:10]);folder.mkdir()
        if os.name=='nt':
            subprocess.run(['icacls',str(folder),'/inheritance:r','/grant:r',os.environ['USERDOMAIN']+'\\'+os.environ['USERNAME']+':(OI)(CI)F','SYSTEM:(OI)(CI)F'],capture_output=True,check=True)
        else:folder.chmod(0o700)
        private=folder/'private';private.mkdir(mode=0o700)
        dest=private/'meta-config.json'
        with os.fdopen(os.open(dest,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w',encoding='utf-8') as f:json.dump(initial(),f,indent=2)
        print(json.dumps({'config_file':str(dest),'public_reception':False,'meta_connected':False}))
    else:
        data=json.loads(a.check.read_text(encoding='utf-8-sig'));print(json.dumps(check(data)[0]))

if __name__=='__main__':main()
