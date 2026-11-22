"""Nginx real para el lanzador Linux. Solo certificados y credenciales sintéticos."""
from pathlib import Path
import json,re,shutil,subprocess,time

IMAGE='nginx@sha256:f6daac2445b0ce70e64d77442ccf62839f3f1b4c24bf6746a857eff014e798c8'

class Harness:
    def __init__(self,*,docker,owned,created,name,run,sec,classes,java,label,tls_existing=None,tls_min_seconds=1800):
        self.docker,self.owned,self.created=docker,owned,created
        self.name,self.run,self.sec,self.classes,self.java,self.label=name,run,sec,classes,java,label
        openssl=next((p for p in [Path('C:/Program Files/Git/mingw64/bin/openssl.exe'),Path('C:/Program Files/Git/usr/bin/openssl.exe')] if p.is_file()),None)
        if openssl is None:raise RuntimeError('OpenSSL local no disponible')
        if tls_existing is not None:
            self.tls=Path(tls_existing).resolve()
            if not self.tls.is_relative_to(Path(__file__).resolve().parents[3]/'qa/runtime') or not (self.tls/'key.pem').is_file():raise ValueError('TLS QA existente fuera de ubicacion permitida')
            r=subprocess.run([str(openssl),'x509','-in',str(self.tls/'cert.pem'),'-noout','-checkend',str(tls_min_seconds)],capture_output=True,timeout=10)
            if r.returncode:raise RuntimeError('Certificado QA no vigente para la ventana; no se renueva automaticamente')
        else:
            self.tls=sec/'tls';self.tls.mkdir()
            r=subprocess.run([str(openssl),'req','-x509','-newkey','rsa:2048','-nodes','-days','2','-subj','/CN=wa-qa.dimabug.casol.cl','-addext','subjectAltName=DNS:wa-qa.dimabug.casol.cl,IP:127.0.0.1','-keyout',str(self.tls/'key.pem'),'-out',str(self.tls/'cert.pem')],capture_output=True,timeout=40)
            if r.returncode:raise RuntimeError('No se pudo crear certificado sintetico')
        root=Path(__file__).resolve().parents[3]
        template=root/'qa/whatsapp/ubuntu/nginx-wa-qa.conf.template'
        site=template.read_text().replace('__CERT_FULLCHAIN__','/qa/tls/cert.pem').replace('__CERT_PRIVATE_KEY__','/qa/tls/key.pem')
        site=site.replace('/var/log/nginx/wa-qa.access.log','/dev/stdout').replace('/var/lib/dimabug-wa-qa-nginx/body','/tmp/body')
        self.config=run/'nginx.conf'
        self.config.write_text('pid /tmp/nginx.pid;\nerror_log /dev/stderr crit;\nworker_processes 1;\nevents { worker_connections 64; }\nhttp {\nclient_body_temp_path /tmp/body 1 2;\nproxy_temp_path /tmp/proxy;\nfastcgi_temp_path /tmp/fastcgi;\nuwsgi_temp_path /tmp/uwsgi;\nscgi_temp_path /tmp/scgi;\n'+site+'\n}\n')
        javac=str(Path(shutil.which('java')).with_name('javac.exe'))
        subprocess.run([javac,'-d',str(classes),str(Path(__file__).with_name('HttpsProbe.java'))],check=True,capture_output=True)
    def common(self,name,network):
        return ['--pull','never','--name',name,'--label','dimabug.wa.c3='+self.label,'--network',network,'--memory','64m','--memory-swap','64m','--cpus','0.25','--pids-limit','32','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','101:101','--tmpfs','/tmp:rw,noexec,nosuid,size=16m','--mount',f'type=bind,source={self.config},target=/qa/nginx.conf,readonly','--mount',f'type=bind,source={self.tls},target=/qa/tls,readonly','--entrypoint','nginx',IMAGE]
    def start(self):
        name=self.name+'-nginx-test'
        p=self.docker('run',*self.common(name,'none'),'-t','-c','/qa/nginx.conf',check=False)
        if self.docker('inspect',name,check=False).returncode==0:self.created.append(name)
        (self.sec/'nginx-test.log').write_text(p.stdout+p.stderr)
        if p.returncode:raise RuntimeError('nginx -t fallo (detalle privado)')
        name=self.name+'-nginx'
        self.docker('run','-d',*self.common(name,'container:'+self.name+'-mysql'),'-c','/qa/nginx.conf','-g','daemon off;')
        self.created.append(name)
        time.sleep(1)
        if not self.owned(name)['State']['Running']:raise RuntimeError('Nginx se detuvo')
    def probe(self,mode):
        name=self.name+'-tls-'+mode
        self.created.append(name)
        p=self.docker('run','--pull','never','--name',name,'--label','dimabug.wa.c3='+self.label,'--network','container:'+self.name+'-nginx','--memory','256m','--memory-swap','256m','--cpus','0.5','--pids-limit','48','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--user','10001:10001','--mount',f'type=bind,source={self.classes},target=/probe,readonly','--mount',f'type=bind,source={self.sec}/application.properties,target=/qa/application.properties,readonly','--mount',f'type=bind,source={self.tls}/cert.pem,target=/qa/cert.pem,readonly','--entrypoint','java',self.java,'-Xmx64m','-Djava.net.preferIPv4Stack=true','-cp','/probe','HttpsProbe',mode,check=False,timeout=150)
        if p.returncode:
            (self.sec/('tls-'+mode+'.log')).write_text(p.stdout+p.stderr)
            raise RuntimeError('HTTPS '+mode+' fallo (detalle privado)')
        return json.loads(p.stdout)
    def scan_logs(self,secrets):
        forbidden=[*secrets,'PRIVADO_C4','100000000000001','hub.verify_token','qa-waba-local','qa-c4-cold','qa-c4-duplicate','qa-c4-sql-retry','jdbc:mysql://']
        findings=[];sizes={};startup=None
        for name in self.created:
            suffix=name[len(self.name):]
            p=self.docker('logs',name,check=False);text=p.stdout+p.stderr
            (self.sec/(name+'.log')).write_text(text)
            sizes[suffix]=len(text.encode())
            if suffix=='-spring':
                match=re.search(r'Started RecepcionQa in ([0-9.]+) seconds',text)
                if match:startup=float(match.group(1))
            if any(value and value in text for value in forbidden) or re.search(r'sha256=[0-9a-fA-F]{64}',text):findings.append(suffix)
        if findings:raise RuntimeError('Registro sensible detectado en '+','.join(findings))
        return {'sensitive_matches':0,'streams_scanned_bytes':sizes,'spring_startup_seconds':startup,'scope':'stdout y stderr de todos los componentes del ensayo; Nginx global y vhost configurados aqui'}
