"""Preparado, no ejecutado: crea solo un esquema y cuenta QA nuevos. Requiere root local."""
import argparse,base64,hashlib,json,os,re,secrets,subprocess
from pathlib import Path

HERE=Path(__file__).resolve().parent
DBUSER='wa_qa_pilot'
def mysql(sql):
    r=subprocess.run(['mysql','--protocol=socket','--batch','--skip-column-names','--default-character-set=utf8mb4'],input=sql,text=True,encoding='utf-8',capture_output=True)
    if r.returncode:raise RuntimeError('MYSQL_FAILED: no se continua; revisar localmente sin mostrar secretos')
    return r.stdout.strip()
def main():
    p=argparse.ArgumentParser();p.add_argument('--execute',action='store_true');p.add_argument('--approval');p.add_argument('--mysql-ca',type=Path)
    args=p.parse_args()
    if not args.execute:print('PLAN_ONLY');return
    if args.approval!='CREAR_SOLO_ESQUEMA_Y_USUARIO_QA':p.error('Autorizacion explicita requerida')
    if os.name!='posix' or os.geteuid()!=0:raise RuntimeError('ROOT_LOCAL_REQUIRED')
    manifest=json.loads((HERE/'manifest.json').read_text());db=manifest['database']
    if not re.fullmatch(r'wa_durable_[a-f0-9]{8,32}',db):raise RuntimeError('DB_NOT_ALLOWED')
    for name,digest in manifest['sha256'].items():
        path=(HERE/name).resolve()
        if not path.is_relative_to(HERE) or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise RuntimeError('BUNDLE_CHANGED')
    if args.mysql_ca is None or not args.mysql_ca.is_file():raise RuntimeError('MYSQL_CA_PUBLIC_REQUIRED')
    if mysql(f"SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{db}'")!='0':raise RuntimeError('DB_ALREADY_EXISTS')
    if mysql(f"SELECT COUNT(*) FROM mysql.user WHERE User='{DBUSER}'")!='0':raise RuntimeError('USER_ALREADY_EXISTS')
    config=Path('/etc/dimabug-wa-pilot')
    if config.exists():raise RuntimeError('QA_CONFIG_ALREADY_EXISTS')
    config.mkdir(mode=0o700);sec=config/'secrets';sec.mkdir(mode=0o700)
    values={'spring.datasource.password':secrets.token_urlsafe(36),'verify-token':secrets.token_hex(24),'app-secret':secrets.token_hex(24),
            'clave-cifrado':base64.b64encode(secrets.token_bytes(32)).decode(),'clave-hmac':base64.b64encode(secrets.token_bytes(32)).decode(),
            'jwt-secret':secrets.token_hex(48),'security-password':secrets.token_hex(24)}
    for key,value in values.items():
        f=sec/key
        with f.open('x',encoding='utf-8') as out:out.write(value)
        f.chmod(0o600)
    # mysql batch sin --force: el primer error aborta. DDL solo sobre una base NUEVA.
    mysql((HERE/'schema.sql').read_text(encoding='utf-8'))
    password=values['spring.datasource.password'] # token_urlsafe, sin comillas ni barras.
    mysql(f"CREATE USER '{DBUSER}'@'127.0.0.1' IDENTIFIED BY '{password}' REQUIRE SSL WITH MAX_USER_CONNECTIONS 3; GRANT SELECT,INSERT,UPDATE,DELETE ON `{db}`.* TO '{DBUSER}'@'127.0.0.1';")
    # No degradar TLS ni modificar el certificado de MySQL compartido.
    ca=config/'mysql-ca.pem';ca.write_bytes(args.mysql_ca.read_bytes());ca.chmod(0o600)
    client=sec/'verify-client.cnf'
    client.write_text(f'[client]\nuser={DBUSER}\npassword={password}\nhost=127.0.0.1\nprotocol=TCP\nssl-mode=VERIFY_IDENTITY\nssl-ca={ca}\n',encoding='utf-8');client.chmod(0o600)
    test=subprocess.run(['mysql','--defaults-extra-file='+str(client),'--batch','--skip-column-names','-e','SELECT 1'],capture_output=True)
    if test.returncode:
        mysql(f"ALTER USER '{DBUSER}'@'127.0.0.1' ACCOUNT LOCK;")
        raise RuntimeError('MYSQL_TLS_IDENTITY_FAILED: cuenta QA bloqueada; no cambiar TLS existente')
    store=config/'mysql-truststore.p12'
    r=subprocess.run(['keytool','-importcert','-noprompt','-alias','mysql-qa-public-ca','-file',str(ca),'-keystore',str(store),'-storetype','PKCS12','-storepass','qa-public-ca-integrity'],capture_output=True)
    if r.returncode:raise RuntimeError('PUBLIC_TRUSTSTORE_FAILED')
    store.chmod(0o644) # Solo certificado publico, sin claves privadas.
    text=(HERE/'application.properties').read_text(encoding='utf-8')
    (config/'application.properties').write_text(text,encoding='utf-8');(config/'application.properties').chmod(0o600)
    rows=mysql(f"USE `{db}`; SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='{db}' AND TABLE_NAME IN ('wa_conversacion','wa_evento','wa_respuesta'); SELECT COUNT(*) FROM co_conocimiento c JOIN co_solucion s ON s.conocimiento_id=c.conocimiento_id WHERE c.conocimiento_estado='PUBLICADO' AND s.solucion_estado=1 AND s.solucion_autoservicio=1;")
    if rows.splitlines()!=['3','1']:raise RuntimeError('QA_SEED_INVALID')
    print('QA_SCHEMA_USER_TLS_READY; servicios NO iniciados')

if __name__=='__main__':main()
