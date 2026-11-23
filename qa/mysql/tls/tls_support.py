"""Bounded QA helpers. No SQL or credential access at import time."""
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import sys

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
NAME='dimabug-qa-mvp-13318-mysql'
ACCOUNT="'dimabug_qa_app'@'172.21.0.3'"
PIN='afee3b9f5f0b8ad162198bfe96b3b5f5be45499afa7f69acb1d89a9a812d7b9e'
sys.path.insert(0,str(ROOT/'qa/runtime/tls-client/site'))
import pymysql


def require(ok,code):
    if not ok:raise RuntimeError(code)


def clean_env():
    env={k:os.environ[k] for k in ['SystemRoot','WINDIR','SystemDrive','USERPROFILE','LOCALAPPDATA','APPDATA','TEMP','TMP','ProgramFiles','ProgramFiles(x86)'] if k in os.environ}
    docker_path=shutil.which('docker')
    require(bool(docker_path),'DOCKER_NOT_FOUND')
    env['PATH']=str(Path(docker_path).parent)+os.pathsep+str(Path(sys.executable).parent)+os.pathsep+str(Path(os.environ['SystemRoot'])/'System32')
    env['PYTHONDONTWRITEBYTECODE']='1'
    return env


def docker(args,stdin=None):
    result=subprocess.run([shutil.which('docker'),'--context','desktop-linux',*args],input=stdin,capture_output=True,text=True,encoding='utf-8',timeout=15,env=clean_env())
    if result.returncode:
        match=re.search(r'ERROR (\d+)',result.stderr)
        raise RuntimeError('SQL_ERROR_'+match[1] if match else 'DOCKER_OPERATION_FAILED')
    return result.stdout.strip()


def sql(statement):
    command='MYSQL_HISTFILE=/dev/null MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql --default-character-set=utf8mb4 --protocol=socket --user=root --batch --raw --skip-column-names --connect-timeout=5'
    return docker(['exec','-i',NAME,'sh','-c',command],statement+'\n')


def public_cert(name):
    require(name in {'ca.pem','server-cert.pem'},'PUBLIC_CERT_ONLY')
    text=docker(['exec',NAME,'cat','/var/lib/mysql/'+name])
    certs=re.findall(r'-----BEGIN CERTIFICATE-----[\s\S]+?-----END CERTIFICATE-----',text)
    require(len(certs)==1 and 'PRIVATE KEY' not in text,'CERT_FORMAT_INVALID')
    return certs[0]+'\n'


class PinnedContext(ssl.SSLContext):
    def __new__(cls,ca,pin):
        return super().__new__(cls,ssl.PROTOCOL_TLS_CLIENT)

    def __init__(self,ca,pin):
        self.check_hostname=False  # Explicit QA identity pin replaces unavailable SAN verification.
        self.verify_mode=ssl.CERT_REQUIRED
        self.minimum_version=ssl.TLSVersion.TLSv1_2
        if ca:self.load_verify_locations(cadata=ca)
        self.expected_pin=pin
        self.pin_verified=False
        self.negotiated_protocol=None
        self.negotiated_cipher=None

    def wrap_socket(self,*args,**kwargs):
        sock=super().wrap_socket(*args,**kwargs)  # Chain/validity must pass first.
        actual=hashlib.sha256(sock.getpeercert(binary_form=True)).hexdigest()
        if actual!=self.expected_pin:
            sock.close();raise ssl.SSLCertVerificationError('QA_CERT_PIN_MISMATCH')
        self.pin_verified=True
        self.negotiated_protocol=sock.version();self.negotiated_cipher=sock.cipher()[0]
        return sock  # PyMySQL sends authentication only after this returns successfully.


def connection(context,password,*,defer=False):
    require(context.verify_mode==ssl.CERT_REQUIRED,'CERT_REQUIRED')
    conn=pymysql.Connection(host='127.0.0.1',port=13318,user='dimabug_qa_app',password=password,
        database='dima_bug_qa_mvp',ssl=context,connect_timeout=5,read_timeout=5,write_timeout=5,
        autocommit=False,local_infile=False,defer_connect=defer)
    require(conn._ssl_required,'TLS_MUST_BE_REQUIRED')
    return conn


def app_password():
    # DPAPI CurrentUser, using OS API; plaintext never crosses a console or file.
    class Blob(ctypes.Structure):
        _fields_=[('size',wintypes.DWORD),('data',ctypes.POINTER(ctypes.c_ubyte))]
    raw=(Path(os.environ['LOCALAPPDATA'])/'DimaBug-QA-MySQL-13318/credentials.dpapi').read_bytes()
    buf=ctypes.create_string_buffer(raw)
    incoming=Blob(len(raw),ctypes.cast(buf,ctypes.POINTER(ctypes.c_ubyte)));out=Blob()
    crypt=ctypes.WinDLL('crypt32',use_last_error=True)
    crypt.CryptUnprotectData.argtypes=[ctypes.POINTER(Blob),ctypes.c_void_p,ctypes.c_void_p,ctypes.c_void_p,ctypes.c_void_p,wintypes.DWORD,ctypes.POINTER(Blob)]
    crypt.CryptUnprotectData.restype=wintypes.BOOL
    require(bool(crypt.CryptUnprotectData(ctypes.byref(incoming),None,None,None,None,0,ctypes.byref(out))),'DPAPI_FAILED')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.LocalFree.argtypes=[ctypes.c_void_p]
    try:
        data=json.loads(ctypes.string_at(out.data,out.size).decode('utf-8'))
    finally:
        ctypes.memset(out.data,0,out.size);kernel.LocalFree(out.data)
    require(data['format']=='dimabug-qa-mysql-credentials-v1' and data['port']==13318 and data['database']=='dima_bug_qa_mvp' and data['app_user']=='dimabug_qa_app','CUSTODY_IDENTITY_MISMATCH')
    password=data['app_password']
    require(bool(re.fullmatch(r'[A-Za-z0-9+/]{43}=',password)),'CREDENTIAL_FORMAT_MISMATCH')
    data.clear()
    return password


def close_window(query=sql):
    query('ALTER USER '+ACCOUNT+' ACCOUNT LOCK;')
    sessions=query("SELECT ID,USER FROM information_schema.PROCESSLIST WHERE USER='dimabug_qa_app';")
    for line in sessions.splitlines():
        identifier,user=line.split('\t')
        require(identifier.isdecimal() and user=='dimabug_qa_app','SESSION_IDENTITY_INVALID')
        try:query('KILL CONNECTION '+str(int(identifier))+';')
        except RuntimeError as exc:
            if str(exc)!='SQL_ERROR_1094':raise  # Already closed: recheck below.
    require(query("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER='dimabug_qa_app';")=='0','APP_SESSIONS_REMAIN')
    require(query("SELECT COUNT(*) FROM mysql.user WHERE User IN ('dimabug_qa_schema','dimabug_qa_app') AND account_locked='Y';")=='2','ACCOUNTS_NOT_LOCKED')
    return True


def emit(path,data):
    # Reports contain only explicit sanitized fields, never exceptions/SQL payloads.
    temp=path.with_suffix('.tmp')
    temp.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8');temp.replace(path)
