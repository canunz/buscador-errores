"""Supervisor del Quick Tunnel autorizado. No login, DNS, servicios o Meta."""
import base64,ctypes,datetime as dt,hashlib,http.client,json,os,re,secrets,ssl,subprocess,threading,time
from ctypes import wintypes
from pathlib import Path
from tunnel_client import self_test

EXPECTED='341deda3db5ec498a0482254d6c38c0b7cf01a30c37bb5dd95d2216d08955e35'

def cloudflared_command(exe,port,ca):
    return [str(exe),'tunnel','--no-autoupdate','--url',f'https://127.0.0.1:{port}','--origin-ca-pool',str(ca),'--origin-server-name','wa-qa.dimabug.casol.cl','--http-host-header','wa-qa.dimabug.casol.cl','--metrics','127.0.0.1:0','--management-diagnostics=false','--loglevel','info']

class KillOnCloseJob:
    """Cerrar el supervisor (incluso por fallo) cierra el proceso cloudflared."""
    def __init__(self,process):
        class Basic(ctypes.Structure):
            _fields_=[('ProcessTime',ctypes.c_longlong),('JobTime',ctypes.c_longlong),('Flags',wintypes.DWORD),('MinWorking',ctypes.c_size_t),('MaxWorking',ctypes.c_size_t),('ActiveProcesses',wintypes.DWORD),('Affinity',ctypes.c_size_t),('Priority',wintypes.DWORD),('Scheduling',wintypes.DWORD)]
        class Io(ctypes.Structure):
            _fields_=[(name,ctypes.c_ulonglong) for name in ['ReadOperations','WriteOperations','OtherOperations','ReadBytes','WriteBytes','OtherBytes']]
        class Extended(ctypes.Structure):
            _fields_=[('Basic',Basic),('Io',Io),('ProcessMemory',ctypes.c_size_t),('JobMemory',ctypes.c_size_t),('PeakProcess',ctypes.c_size_t),('PeakJob',ctypes.c_size_t)]
        kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.CreateJobObjectW.argtypes=[ctypes.c_void_p,wintypes.LPCWSTR];kernel.CreateJobObjectW.restype=wintypes.HANDLE
        kernel.SetInformationJobObject.argtypes=[wintypes.HANDLE,ctypes.c_int,ctypes.c_void_p,wintypes.DWORD]
        kernel.AssignProcessToJobObject.argtypes=[wintypes.HANDLE,wintypes.HANDLE]
        kernel.CloseHandle.argtypes=[wintypes.HANDLE]
        self.kernel=kernel;self.handle=kernel.CreateJobObjectW(None,None)
        limits=Extended();limits.Basic.Flags=0x2000
        if not self.handle or not kernel.SetInformationJobObject(self.handle,9,ctypes.byref(limits),ctypes.sizeof(limits)) or not kernel.AssignProcessToJobObject(self.handle,wintypes.HANDLE(int(process._handle))):
            process.terminate();raise RuntimeError('No se pudo asegurar el cierre del tunel')
    def close(self):
        if self.handle:self.kernel.CloseHandle(self.handle);self.handle=None

class Session:
    def __init__(self,*,harness,props,exe,port,minutes,real_meta=False,public_authorized=False,meta_outbound=False):
        if real_meta and not public_authorized:raise ValueError('Recepcion real publica requiere autorizacion expresa')
        self.real_meta=real_meta
        self.meta_outbound=meta_outbound
        self.h=harness;self.props=props;self.exe=Path(exe).resolve();self.port=port;self.minutes=minutes
        if minutes not in (20,30,60):raise ValueError('La ventana debe ser de 20, 30 o 60 minutos')
        if hashlib.sha256(self.exe.read_bytes()).hexdigest()!=EXPECTED:raise ValueError('Binario cloudflared no verificado')
        self.username='qa';self.password=secrets.token_urlsafe(32) if not real_meta else ''
        self.process=None;self.job=None;self.timer=None;self.expired=threading.Event();self.closed=False
        text=self.h.config.read_text().replace('listen 127.0.0.1:18443 ssl;','listen 0.0.0.0:18443 ssl;')
        if not real_meta:
            openssl=next(p for p in [Path('C:/Program Files/Git/mingw64/bin/openssl.exe'),Path('C:/Program Files/Git/usr/bin/openssl.exe')] if p.is_file())
            hashed=subprocess.run([str(openssl),'passwd','-6','-stdin'],input=(self.password+'\n').encode('ascii'),capture_output=True,check=True).stdout.decode('ascii').strip()
            self.auth=self.h.sec/'access';self.auth.mkdir();(self.auth/'htpasswd').write_bytes((self.username+':'+hashed+'\n').encode('ascii'))
            text=text.replace('location = /webhooks/whatsapp {','location = /webhooks/whatsapp {\n        auth_basic "DimaBug QA temporal";\n        auth_basic_user_file /qa/access/htpasswd;')
        text=text.replace('    if ($host !=','    if ($http_x_forwarded_proto != "https") { return 403; }\n    if ($host !=')
        self.h.config.write_text(text)
        original=self.h.common
        def common(name,network):
            args=original(name,network)
            i=args.index('--entrypoint')
            return args[:i]+['--mount',f'type=bind,source={self.auth},target=/qa/access,readonly']+args[i:]
        if not real_meta:self.h.common=common
    def preflight(self):
        mysql=self.h.owned(self.h.name+'-mysql')
        assert not mysql['HostConfig'].get('PortBindings'),'MySQL publicado'
        assert not self.h.owned(self.h.name+'-spring')['HostConfig'].get('PortBindings'),'Spring publicado'
        edge=self.h.owned(self.h.name+'-edge')
        assert edge['HostConfig']['PortBindings']=={'18443/tcp':[{'HostIp':'127.0.0.1','HostPort':str(self.port)}]},'Publicacion de gateway no permitida'
        network=json.loads(self.h.docker('network','inspect',self.h.name+'-net').stdout)[0]
        assert network['Internal'],'Red QA con salida no prevista'
        context=ssl.create_default_context(cafile=str(self.h.tls/'cert.pem'))
        auth='Basic '+base64.b64encode((self.username+':'+self.password).encode()).decode()
        def request(path,authenticated):
            c=http.client.HTTPSConnection('127.0.0.1',self.port,context=context,timeout=10)
            headers={'Host':'wa-qa.dimabug.casol.cl','X-Forwarded-Proto':'https'}
            if authenticated and not self.real_meta:headers['Authorization']=auth
            try:
                c.request('GET',path,headers=headers);r=c.getresponse();body=r.read(2048);return r.status,body
            finally:c.close()
        for attempt in range(20):
            try:
                if request('/webhooks/whatsapp',False)[0]==(403 if self.real_meta else 401):break
            except OSError:pass
            time.sleep(1)
        else:raise RuntimeError('Gateway local no disponible con autenticacion')
        assert request('/api/publico/chatbot/consultas',True)[0]==403,'Origen expone API interna'
        time.sleep(1)
        query='/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token='+self.props['dimabug.whatsapp.meta-qa.verify-token']+'&hub.challenge=000123'
        status,body=request(query,True)
        assert status==200 and body==b'000123','Origen TLS/autenticacion no disponible'
    def start_edge(self):
        # La red backend sigue interna y sin puertos. Solo este proxy tiene
        # un puerto publicado en loopback del host, para cloudflared Windows.
        self.edge_network=self.h.name+'-edge-net'
        self.h.docker('network','create','--label','dimabug.wa.c3='+self.h.label,self.edge_network)
        backend=self.h.owned(self.h.name+'-mysql')['NetworkSettings']['Networks'][self.h.name+'-net']['IPAddress']
        edge_config=self.h.run/'nginx-edge.conf'
        text=self.h.config.read_text().replace('proxy_pass http://127.0.0.1:8082;',f'proxy_pass https://{backend}:18443;\n        proxy_ssl_trusted_certificate /qa/tls/cert.pem;\n        proxy_ssl_verify on;\n        proxy_ssl_server_name on;\n        proxy_ssl_name wa-qa.dimabug.casol.cl;')
        text=text.replace('proxy_set_header Authorization "";','proxy_set_header Authorization $http_authorization;').replace('proxy_set_header X-Forwarded-Proto "";','proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;')
        edge_config.write_text(text)
        name=self.h.name+'-edge'
        args=self.h.common(name,self.edge_network)
        args=[a.replace(f'source={self.h.config},target=/qa/nginx.conf',f'source={edge_config},target=/qa/nginx.conf') for a in args]
        self.h.docker('run','-d','-p',f'127.0.0.1:{self.port}:18443',*args,'-c','/qa/nginx.conf','-g','daemon off;')
        self.h.created.append(name)
        self.h.docker('network','connect',self.h.name+'-net',name)
    def status(self,data):
        dest=self.h.run/'tunnel-status.json';temp=self.h.run/'tunnel-status.tmp'
        temp.write_text(json.dumps(data,indent=2));os.replace(temp,dest)
    def start_and_wait(self,verify_store):
        self.start_edge()
        self.preflight() # Antes de crear una URL publica.
        profile=self.h.sec/'cloudflared-profile';profile.mkdir()
        (profile/'AppData/Roaming').mkdir(parents=True);(profile/'AppData/Local').mkdir(parents=True)
        allowed={'SYSTEMROOT','WINDIR','SYSTEMDRIVE','COMSPEC','PATH','TEMP','TMP'}
        env={k:v for k,v in os.environ.items() if k.upper() in allowed}
        env.update(USERPROFILE=str(profile),APPDATA=str(profile/'AppData/Roaming'),LOCALAPPDATA=str(profile/'AppData/Local'))
        logpath=self.h.sec/'cloudflared.log';self.log=logpath.open('wb')
        args=cloudflared_command(self.exe,self.port,self.h.tls/'cert.pem')
        self.status({'state':'STARTING','minutes_max':self.minutes})
        started=time.time()
        self.process=subprocess.Popen(args,env=env,stdout=self.log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        self.job=KillOnCloseJob(self.process)
        for _ in range(120):
            if self.process.poll() is not None:raise RuntimeError('cloudflared termino; detalle privado')
            candidates=[url for url in re.findall(r'https://[a-z0-9-]+\.trycloudflare\.com',logpath.read_text(errors='replace')) if url not in ['https://api.trycloudflare.com','https://www.trycloudflare.com']]
            if candidates:break
            time.sleep(1)
        else:raise RuntimeError('No se obtuvo URL temporal')
        self.url=candidates[0];expires=started+self.minutes*60
        self.expires=expires
        def expire():
            self.expired.set()
            if self.process.poll() is None:self.process.terminate()
        self.timer=threading.Timer(max(0,expires-time.time()),expire);self.timer.daemon=True;self.timer.start()
        config={'url':self.url,'username':self.username,'password':self.password,'app_secret':self.props['dimabug.whatsapp.meta-qa.app-secret'],'verify_token':self.props['dimabug.whatsapp.meta-qa.verify-token']}
        (self.h.sec/'client.json').write_text(json.dumps(config,indent=2))
        # Esperar propagacion sin credenciales: no exponer datos al log/errores.
        from tunnel_client import send
        readiness={};ready_deadline=min(expires,time.time()+180)
        while time.time()<ready_deadline:
            try:
                code=send(config,'GET','/webhooks/whatsapp',auth=False,timeout=5,require_gateway=self.real_meta)[0]
                readiness[str(code)]=readiness.get(str(code),0)+1
                if code==(403 if self.real_meta else 401):break
            except Exception as e:
                key=type(e).__name__;readiness[key]=readiness.get(key,0)+1
            self.status({'state':'STARTING','url':self.url,'expires_unix':expires,'readiness':readiness})
            if self.expired.is_set():raise RuntimeError('Ventana temporal agotada')
            time.sleep(1)
        else:raise RuntimeError('Gateway publico no disponible con control de acceso')
        results=self.activation_checks(config)
        if not verify_store():raise RuntimeError('Persistencia publica inicial incorrecta')
        self.audit_log()
        (self.h.run/'tunnel-public-tests.json').write_text(json.dumps(results,indent=2))
        active={'state':'ACTIVE','url':self.url,'expires_unix':expires,'expires_utc':dt.datetime.fromtimestamp(expires,dt.timezone.utc).isoformat(),'basic_auth':not self.real_meta,'hmac':True,'synthetic_only':not self.real_meta,'real_reception':self.real_meta,'meta_outbound':self.meta_outbound,'public_tests':results}
        self.status(active)
        print('TUNNEL_ACTIVE',self.h.run,flush=True)
        while not self.expired.wait(1):
            if time.time()>=expires:self.expired.set();break
            if (self.h.run/'stop.request').exists():break
            if self.process.poll() is not None:break
        self.close()
        return {'public_tests':results,'closed':True,'url':self.url,'expires_utc':active['expires_utc']}
    def activation_checks(self,config):
        if self.real_meta:
            # Esperar configuracion manual de Meta: no POST ni prueba publica
            # de challenge automatica. Solo se hizo GET de conectividad sin token.
            return {'connectivity_only':True,'awaiting_manual_meta_setup':True,'no_post_sent':True,'real_message_not_yet_verified':True}
        return self_test(config)
    def real_meta_checks(self,config):
        from tunnel_client import send
        query='/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token='+config['verify_token']+'&hub.challenge=000123'
        status,body,_=send(config,'GET',query,auth=False)
        if status!=200 or body!=b'000123':raise RuntimeError('Verificacion GET Meta no disponible')
        for route in ['/api/publico/chatbot/consultas','/actuator/health','/swagger-ui/index.html']:
            time.sleep(.7)
            if send(config,'GET',route,auth=False)[0]!=403:raise RuntimeError('Ruta interna accesible')
        time.sleep(.7)
        if send(config,'POST','/webhooks/whatsapp',b'{}',auth=False,signed=False)[0]!=403:raise RuntimeError('POST sin firma aceptado')
        time.sleep(.7)
        if send(config,'POST','/webhooks/whatsapp',b'{}',auth=False,mutate=True)[0]!=403:raise RuntimeError('POST alterado aceptado')
        return {'challenge_exact':True,'internal_routes':403,'missing_signature':403,'tampered_body':403,'no_test_message_inserted':True,'real_message_not_yet_verified':True}
    def audit_log(self):
        if not (self.h.sec/'cloudflared.log').exists() and self.process is None:return
        text=(self.h.sec/'cloudflared.log').read_text(errors='replace')
        basic='Basic '+base64.b64encode((self.username+':'+self.password).encode()).decode()
        forbidden=[self.password,basic,self.props['dimabug.whatsapp.meta-qa.app-secret'],self.props['dimabug.whatsapp.meta-qa.verify-token'],'hub.verify_token=','SOLO_EVENTO_SINTETICO_TUNEL']
        if any(value and value in text for value in forbidden) or re.search(r'sha256=[0-9a-fA-F]{64}',text):raise RuntimeError('Log cloudflared contiene marcador sensible; acceso cerrado')
    def close(self):
        if self.closed:return
        self.closed=True
        if self.timer:self.timer.cancel()
        if self.process is not None and self.process.poll() is None:
            self.process.terminate()
            try:self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:self.process.kill();self.process.wait(timeout=5)
        if self.job:self.job.close()
        if hasattr(self,'log'):self.log.close()
        self.status({'state':'CLOSED','closed_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'reason':'expired' if self.expired.is_set() else 'stopped_or_failed'})
