"""Offline checks with an allowlisted child environment. Never connects to MySQL or Google."""
import json,os,re,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parents[1]/'qa/artifacts/registro-universal-20261115'
task=sys.argv[1]
allowed=['SystemRoot','WINDIR','SystemDrive','USERPROFILE','LOCALAPPDATA','APPDATA','TEMP','TMP','ProgramFiles','ProgramFiles(x86)']
env={k:os.environ[k] for k in allowed if k in os.environ}
env['PATH']=os.pathsep.join([r'C:\Program Files\nodejs',r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin',str(Path(env['SystemRoot'])/'System32'),env['SystemRoot']])
env.update(CI='true',NG_CLI_ANALYTICS='false',GMAIL_ENVIO_REAL_HABILITADO='false',GMAIL_OAUTH_HABILITADO='false',DIMABUG_AUTH_CORREO_HABILITADO='false',DIMABUG_AUTOMATIZACION_MODO='DESHABILITADO',DIMABUG_AUTOMATIZACION_SEGUIMIENTO_HABILITADO='false',DIMABUG_CORREO_BIENVENIDA_HABILITADO='false',DIMABUG_CORREO_TICKET_CRITICO_HABILITADO='false')
if task.startswith('backend'):
 cwd=R/'sources/dima-bug-backend'
 m=Path(env['USERPROFILE'])/'.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn.cmd'
 agent=Path(env['USERPROFILE'])/'.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
 suites='RegistroUniversalIntegrationTest,RegistroUniversalSeguridadTest,RegistroUniversalMigracionTest'
 if task=='backend-ticket-regressions':
  suites='AdministrarUsuariosServiceTest,TicketSecurityIntegrationTest,CrearTicketServiceTest,ConsultarTicketsServiceTest,ComentarTicketServiceTest,AsignarTicketServiceTest,CambiarEstadoTicketServiceTest,CambiarPrioridadTicketServiceTest,ResolverTicketServiceTest,EtiquetarTicketServiceTest,AdjuntarArchivoTicketServiceTest,TicketTest,AdjuntoTicketTest,TicketsArquitecturaTest'
 cmd=[str(m),'-o','-B','-ntp','-f','pom-release-isolated.xml','test','-DargLine=-javaagent:'+str(agent),'-Dtest='+suites,'-Dlogging.level.root=OFF','-Dspring.main.banner-mode=off']
elif task.startswith('frontend'):
 cwd=R/'sources/frontend-dimabug'
 cli=cwd/'node_modules/@angular/cli/bin/ng.js'
 cmd=[r'C:\Program Files\nodejs\node.exe',str(cli)]
 if task=='frontend-build':cmd+=['build','--configuration=production','--output-path='+str(R/'artifacts/frontend')]
 else:
  env['CHROME_BIN']=r'C:\Program Files\Google\Chrome\Application\chrome.exe'
  specs=['src/app/features/tickets/solicitudes-basicas.component.spec.ts','src/app/core/guards/auth.guard.spec.ts','src/app/features/auth/login/login.component.spec.ts']
  cmd+=['test','--watch=false','--browsers=ChromeHeadless','--progress=false']+['--include='+s for s in specs]
else:raise SystemExit('UNKNOWN_TASK')
p=subprocess.run(cmd,cwd=cwd,env=env,capture_output=True,encoding='utf-8',errors='replace',timeout=480)
s=re.sub(r'\x1b\[[0-9;]*m','',p.stdout+'\n'+p.stderr)
report={'task':task,'exit_code':p.returncode,'inherited_credentials':False,'real_mail':False,'mysql_access':False}
report['summary']=[x[:450] for x in s.splitlines() if re.search(r'Tests run:|BUILD SUCCESS|BUILD FAILURE|TOTAL:|Application bundle generation|error TS\d+|\[ERROR\].*\.java\[',x)][:60]
if p.returncode:
 safe=re.sub(r'\beyJ[A-Za-z0-9_.-]+','[JWT]',s)
 safe=re.sub(r'\b\d{6}\b','[CODE]',safe)
 report['diagnostic']=[x[:500] for x in safe.splitlines() if re.search(r'Caused by:|Error:|Expected|but was|FAILED|error NG\d+|does not exist|not a known|BadSqlGrammarException|IntegrityConstraintViolation',x) and 'WebMergedContextConfiguration' not in x][:20]
 if task.startswith('frontend') and not report['diagnostic']: report['diagnostic']=safe.splitlines()[-15:]
if task.startswith('backend'):
 reports=[]
 for f in (cwd/'target/surefire-reports').glob('TEST-*.xml'):
  node=ET.parse(f).getroot();reports.append({'suite':node.attrib['name'],**{k:int(node.attrib[k]) for k in ('tests','failures','errors','skipped')}})
 reports=[r for r in reports if r['suite'].rsplit('.',1)[-1] in suites.split(',')]
 report['suites']=reports;report['totals']={k:sum(t[k] for t in reports) for k in ('tests','failures','errors','skipped')}
report['verified']=p.returncode==0
(R/'evidence'/f'{task}.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report));raise SystemExit(p.returncode)
