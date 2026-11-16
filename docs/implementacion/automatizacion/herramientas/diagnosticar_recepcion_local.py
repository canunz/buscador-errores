"""Inspección de solo lectura de la ejecución local 34; nunca imprime secretos."""
import json, subprocess, tempfile
from pathlib import Path
from urllib.parse import urlsplit
import psutil
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--pid',required=True,type=int)
args=parser.parse_args()
p=psutil.Process(args.pid);env=p.environ();cmd=p.cmdline()
u=urlsplit(env.get('DB_URL','').removeprefix('jdbc:'))
if 'cl.casol.backend.BackendApplication' not in cmd or (u.hostname,u.port,u.path)!=('127.0.0.1',3307,'/dima_bug_staging'):
    raise SystemExit('Instancia local no validada.')
if any(v.startswith(('--spring.datasource.','-Dspring.datasource.')) for v in cmd):
    raise SystemExit('Overrides de datasource: inspección cancelada.')
root=Path(__file__).resolve().parents[4]
classpath=str(root/'dima-bug-backend/target/classes')+';'+(root/'dima-bug-backend/target/runtime-classpath.txt').read_text().strip()
source=r'''import java.sql.*;
class InspectReception {
 public static void main(String[] args) throws Exception {
  try(var c=DriverManager.getConnection(System.getenv("DB_URL"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"))){
   c.setReadOnly(true);
   String[] queries={
    "select ejecucion_id,ejecucion_estado from se_ejecucion where ejecucion_id=34",
    "select s.id,s.ejecucion_id,s.ejecucion_paso_id,s.ronda,s.estado,count(f.id) as fotos from se_solicitud_evidencia s left join se_evidencia f on f.solicitud_id=s.id where s.ejecucion_id=34 group by s.id,s.ejecucion_id,s.ejecucion_paso_id,s.ronda,s.estado",
    "select count(*) as solicitudes_paso_ajeno from se_solicitud_evidencia s left join se_ejecucion_paso p on p.ejecucion_paso_id=s.ejecucion_paso_id where s.ejecucion_id=34 and (p.ejecucion_id<>34 or p.ejecucion_paso_id is null)",
    "select count(*) as envios_pendientes from se_solicitud_evidencia where estado='PENDIENTE'",
    "select count(*) as seguimientos_reales_vencidos from se_solicitud_evidencia where estado in ('ESPERANDO_EVIDENCIA','ESPERANDO_CONFIRMACION') and mensaje_enviado_id not like 'SIMULADO-%' and proximo_seguimiento<=UTC_TIMESTAMP()"
   };
   for(int i=0;i<queries.length;i++)try(var st=c.createStatement()){st.setQueryTimeout(8);try(var r=st.executeQuery(queries[i])){while(r.next()){var row=new StringBuilder("QA_"+i);for(int j=1;j<=r.getMetaData().getColumnCount();j++)row.append(" ").append(r.getMetaData().getColumnLabel(j)).append("=").append(r.getString(j));System.out.println(row);}}}
  } catch(SQLException ex){System.out.println("QA_SQL_ERROR state="+ex.getSQLState());System.exit(2);}
 }
}'''
with tempfile.TemporaryDirectory(prefix='dimabug-recepcion-') as folder:
    file=Path(folder)/'InspectReception.java';file.write_text(source,encoding='utf-8')
    result=subprocess.run([p.exe(),'--class-path',classpath,str(file)],env=env,capture_output=True,text=True,timeout=45)
    for line in result.stdout.splitlines():
        if line.startswith('QA_'):print(line)
    print(json.dumps({'inspection_exit':result.returncode,'backend_pid':p.pid}))
    raise SystemExit(result.returncode)
