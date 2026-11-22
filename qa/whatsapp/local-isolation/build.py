"""Construye offline un JAR QA por lista permitida; no cambia el backend."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,uuid,zipfile

ROOT=Path(__file__).resolve().parents[3]
SOURCE=ROOT/'dima-bug-backend/src/main'
RUN=ROOT/'qa/runtime'/('wa-local-build-'+uuid.uuid4().hex[:10])
RUN.mkdir(parents=True)
java=RUN/'src/main/java'; resources=RUN/'src/main/resources'
resources.mkdir(parents=True)
base=SOURCE/'java/cl/casol/backend/whatsapp'
selected=[]
for folder in ['domain','application/port/out','infrastructure/meta','infrastructure/persistence']:
    selected.extend((base/folder).glob('*.java'))
excluded={'MetaQaConfig.java','WhatsAppDurableConfig.java','TrabajadoresWhatsApp.java'}
selected=[p for p in selected if p.name not in excluded]
selected.append(base/'infrastructure/http/ValidacionWebhookLocal.java')
manifest={}
for p in selected:
    relative=p.relative_to(SOURCE/'java'); dest=java/relative
    dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
    manifest[str(relative)]=hashlib.sha256(p.read_bytes()).hexdigest()
dest=java/'cl/casol/qa/RecepcionQa.java';dest.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(Path(__file__).with_name('RecepcionQa.java'),dest)
manifest['cl/casol/qa/RecepcionQa.java']=hashlib.sha256(dest.read_bytes()).hexdigest()
(resources/'META-INF').mkdir()
shutil.copy2(SOURCE/'resources/META-INF/whatsapp.orm.xml',resources/'META-INF/whatsapp.orm.xml')
shutil.copy2(SOURCE/'resources/application-qa-whatsapp-meta.properties',resources/'application-qa-whatsapp-meta.properties')
dependencies=['spring-boot-starter-data-jpa','spring-boot-starter-security','spring-boot-starter-webmvc']
deps=''.join('<dependency><groupId>org.springframework.boot</groupId><artifactId>'+d+'</artifactId></dependency>' for d in dependencies)
deps+='<dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>'
(RUN/'pom.xml').write_text('<project><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.1.1</version><relativePath/></parent><groupId>cl.casol.qa</groupId><artifactId>whatsapp-qa</artifactId><version>local</version><properties><java.version>21</java.version></properties><dependencies>'+deps+'</dependencies><build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId><configuration><mainClass>cl.casol.qa.RecepcionQa</mainClass></configuration></plugin></plugins></build></project>')
home=Path.home()/'.m2/wrapper/dists'
boots=sorted(home.glob('apache-maven-3.9.16*/**/plexus-classworlds*.jar')) or sorted(home.glob('**/plexus-classworlds*.jar'))
boot=boots[0];maven=boot.parent.parent
command=[shutil.which('java'),'-Dmaven.home='+str(maven),'-Dmaven.multiModuleProjectDirectory='+str(RUN),'-Dclassworlds.conf='+str(maven/'bin/m2.conf'),'-classpath',str(boot),'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-Dstyle.color=never','package']
with (RUN/'build.log').open('wb') as log:
    result=subprocess.run(command,cwd=RUN,stdout=log,stderr=subprocess.STDOUT,timeout=240)
report={'exit_code':result.returncode,'fuentes_sha256':manifest,'backend_modificado':False,'offline':True}
if result.returncode==0:
    jar=RUN/'target/whatsapp-qa-local.jar'
    with zipfile.ZipFile(jar) as z:
        classes=[n for n in z.namelist() if n.startswith('BOOT-INF/classes/') and n.endswith('.class')]
        assert all(n.startswith(('BOOT-INF/classes/cl/casol/backend/whatsapp/','BOOT-INF/classes/cl/casol/qa/')) for n in classes)
        assert not any('/busqueda/' in n or '/ocr/' in n or 'Trabajadores' in n for n in classes)
        report['clases_aplicacion']=len(classes)
    report.update(jar=str(jar),jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest())
(RUN/'informe.json').write_text(json.dumps(report,indent=2))
print('EVIDENCIAS',RUN,flush=True)
if result.returncode:
    print('\n'.join((RUN/'build.log').read_text(errors='replace').splitlines()[-25:]))
raise SystemExit(result.returncode)
