"""Reparación preparada SOLO del cliente QA existente. Nunca desbloquea ni inicia servicios."""
import argparse,hashlib,json,os,re,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
PIN='e2d75fd5a17a616992f3e15ec162202e0c0b09154d67fc01ef9960bc5552f06e'
CONF=Path('/etc/dimabug-wa-pilot')
def mysql(q):
    r=subprocess.run(['mysql','--protocol=socket','--batch','--skip-column-names'],input=q,text=True,capture_output=True)
    if r.returncode:raise RuntimeError('ADMIN_READ_FAILED')
    return r.stdout.strip()
def run(command):
    r=subprocess.run(command,capture_output=True)
    if r.returncode:
        safe=re.findall(rb'QA_[A-Z_]+(?:=\d+)?',r.stderr)
        raise RuntimeError('QA_TLS_PREPARATION_FAILED'+(': '+','.join(x.decode('ascii') for x in safe) if safe else ''))
    return r.stdout
def jdbc_store_uri(path):return 'file:'+path.as_posix()
def main():
    p=argparse.ArgumentParser();p.add_argument('--execute',action='store_true');p.add_argument('--approval');a=p.parse_args()
    if not a.execute:print('PLAN_ONLY');return
    if a.approval!='AJUSTAR_SOLO_TLS_CLIENTE_QA_SIN_DESBLOQUEAR' or os.name!='posix' or os.geteuid()!=0:p.error('Autorizacion y root requeridos')
    m=json.loads((HERE/'manifest.json').read_text());db=m['database']
    if not re.fullmatch(r'wa_durable_[a-f0-9]{8,32}',db):raise RuntimeError('QA_DB_REQUIRED')
    for name,digest in m['sha256'].items():
        f=(HERE/name).resolve()
        if not f.is_relative_to(HERE) or hashlib.sha256(f.read_bytes()).hexdigest()!=digest:raise RuntimeError('BUNDLE_CHANGED')
    if mysql("SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1'")!='Y':raise RuntimeError('QA_ACCOUNT_MUST_REMAIN_LOCKED')
    if mysql(f"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='{db}'")!='47':raise RuntimeError('QA_SCHEMA_NOT_READY')
    cert=Path('/var/lib/mysql/server-cert.pem');ca=CONF/'mysql-ca.pem'
    der=run(['openssl','x509','-in',str(cert),'-outform','DER'])
    if hashlib.sha256(der).hexdigest()!=PIN:raise RuntimeError('SERVER_PIN_CHANGED')
    run(['openssl','verify','-CAfile',str(ca),str(cert)]) # CA y vigencia; identidad verificada por pin exacto.
    store=CONF/'mysql-truststore.p12'
    if not store.exists():run(['keytool','-importcert','-noprompt','-alias','mysql-qa-public-ca','-file',str(ca),'-keystore',str(store),'-storetype','PKCS12','-storepass','qa-public-ca-integrity'])
    store.chmod(0o600);digest=hashlib.sha256(store.read_bytes()).hexdigest()
    properties=(HERE/'application.properties').read_text(encoding='utf-8').replace('sslMode=VERIFY_IDENTITY','sslMode=VERIFY_CA')
    if 'socketFactory=dimabug.qa.SharedMysqlPinnedSocketFactory' not in properties:
        properties=properties.replace('&sslMode=VERIFY_CA','&sslMode=VERIFY_CA&socketFactory=dimabug.qa.SharedMysqlPinnedSocketFactory&tlsVersions=TLSv1.2,TLSv1.3')
    if properties.count('socketFactory=dimabug.qa.SharedMysqlPinnedSocketFactory')!=1:raise RuntimeError('PINNED_FACTORY_REQUIRED')
    target=CONF/'application.properties'
    if target.exists() and not (CONF/'application.before-pinning.properties').exists():
        backup=CONF/'application.before-pinning.properties';backup.write_bytes(target.read_bytes());backup.chmod(0o600)
    target.write_text(properties,encoding='utf-8');target.chmod(0o600)
    unit=(HERE/'dimabug-wa-pilot.service').read_text(encoding='utf-8').replace('/usr/bin/java ',f'/usr/bin/java -Dqa.tls.storeUri=file:%d/mysql-truststore.p12 -Dqa.tls.storeSha256={digest} ',1)
    (CONF/'dimabug-wa-pilot.service').write_text(unit,encoding='utf-8');(CONF/'dimabug-wa-pilot.service').chmod(0o600)
    (CONF/'tls-public.json').write_text(json.dumps({'store_sha256':digest,'server_pin':PIN,'bundle':str(HERE)}),encoding='utf-8');(CONF/'tls-public.json').chmod(0o600)
    result=run(['java','-Dqa.tls.storeUri='+jdbc_store_uri(store),'-Dqa.tls.storeSha256='+digest,'-cp',str(HERE/'tls')+':'+str(HERE/'lib/mysql-connector-j.jar'),'dimabug.qa.SharedMysqlTlsProbe','--expect-locked'])
    if result.strip()!=b'TLS_CA_PIN_VERIFIED_ACCOUNT_STILL_LOCKED':raise RuntimeError('QA_PINNED_CHECK_FAILED')
    if mysql("SELECT account_locked FROM mysql.user WHERE User='wa_qa_pilot' AND Host='127.0.0.1'")!='Y':raise RuntimeError('QA_ACCOUNT_CHANGED')
    print('TLS_CA_PIN_VERIFIED_ACCOUNT_STILL_LOCKED; sin servicios iniciados')

if __name__=='__main__':main()
