"""Comprobaciones estáticas locales; no equivalen a nginx -t ni a pruebas HTTPS."""
import ast,json,unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parent
class ContratosPlantillas(unittest.TestCase):
    def test_presupuesto_coherente_con_candidato_actual(self):
        presupuesto=json.loads((ROOT/'candidato-qa-local.json').read_text())['recursos_recepcion_sola']
        for nombre,clave in [('dimabug-wa-qa.service.template','spring'),('dimabug-wa-qa-mysql.service.template','mysql_exclusivo')]:
            s=(ROOT/nombre).read_text();p=presupuesto[clave]
            for valor in [f"MemoryMax={p['memory_max_mib']}M",f"MemoryHigh={p['memory_high_mib']}M",f"CPUQuota={p['cpu_quota_porcentaje_de_un_cpu']}%",f"TasksMax={p['tasks_max']}",f"CPUWeight={p['cpu_weight']}",f"IOWeight={p['io_weight']}"]:
                self.assertIn(valor,s)
        nginx=(ROOT/'nginx-wa-qa.conf.template').read_text();p=presupuesto['nginx_piloto']
        for valor in [f"rate={p['rate_solicitudes_por_segundo']}r/s",f"burst={p['burst']}",f"wa_qa_conns {p['max_conexiones_qa']};"]:self.assertIn(valor,nginx)
        mysql=(ROOT/'mysqld-qa.cnf.template').read_text();p=presupuesto['mysql_exclusivo']
        self.assertIn(f"max-connections={p['max_connections']}",mysql)
        self.assertIn(f"innodb-buffer-pool-size={p['innodb_buffer_pool_mib']}M",mysql)
        app=(ROOT/'application.properties.template').read_text();p=presupuesto['spring']
        self.assertIn(f"maximum-pool-size={p['jdbc_pool_max']}",app)
        self.assertIn(f"server.tomcat.threads.max={p['tomcat_threads_max']}",app)
    def test_puerto_canonico_y_mysql_ajustado(self):
        candidato=json.loads((ROOT/'candidato-qa-local.json').read_text())
        app=(ROOT/'application.properties.template').read_text();nginx=(ROOT/'nginx-wa-qa.conf.template').read_text()
        self.assertIn(f"server.port={candidato['red']['spring_port']}\n",app)
        self.assertIn(f"proxy_pass http://127.0.0.1:{candidato['red']['spring_port']};",nginx)
        self.assertNotIn('18132',app+nginx)
        self.assertIn('socketTimeout=5000',app)
        mysql=(ROOT/'mysqld-qa.cnf.template').read_text()
        for valor in ['performance-schema=OFF','table-open-cache=128','table-definition-cache=400','thread-cache-size=4','tmp-table-size=16M','max-heap-table-size=16M','temptable-max-ram=32M','innodb-flush-log-at-trx-commit=1','innodb-doublewrite=ON','sync-binlog=1','skip-name-resolve']:
            self.assertIn(valor,mysql)
        spring=(ROOT/'dimabug-wa-qa.service.template').read_text()
        for valor in ['-Xms64m -Xmx192m','MaxDirectMemorySize=48m','MaxMetaspaceSize=96m','ReservedCodeCacheSize=48m']:
            self.assertIn(valor,spring)
    def test_nginx_cierra_rutas_y_origen(self):
        s=(ROOT/'nginx-wa-qa.conf.template').read_text()
        for v in ['listen 127.0.0.1:18443 ssl','server_name wa-qa.dimabug.casol.cl','location = /webhooks/whatsapp','location / { return 403; }','proxy_next_upstream off','proxy_set_header Forwarded ""','client_max_body_size 3m']:
            self.assertIn(v,s)
        self.assertNotIn('listen 443',s);self.assertNotIn('301',s)
    def test_logs_no_usan_campos_sensibles(self):
        s=(ROOT/'nginx-wa-qa.conf.template').read_text();fmt=s.split('log_format wa_qa_safe',1)[1].split(';',1)[0]
        for v in ['$request_uri','$args','$request_body','$request"','$remote_addr','$http_authorization']:self.assertNotIn(v,fmt)
        self.assertIn('error_log /dev/null crit',s)
    def test_servicio_separado_y_limitado(self):
        s=(ROOT/'dimabug-wa-qa.service.template').read_text()
        for v in ['User=dimabug-wa-qa','CPUQuota=50%','MemoryMax=512M','TasksMax=96','LoadCredential=','IPAddressDeny=any','IPAddressAllow=localhost','/APPROVED','whatsapp-qa.jar']:self.assertIn(v,s)
        self.assertNotIn('/opt/dimabug/staging',s);self.assertNotIn('EnvironmentFile=',s)
    def test_mysql_no_comparte_datos(self):
        s=(ROOT/'mysqld-qa.cnf.template').read_text()
        self.assertIn('datadir=/var/lib/dimabug-wa-qa-mysql',s);self.assertIn('port=13328',s);self.assertIn('require-secure-transport=ON',s)
        self.assertNotIn('datadir=/var/lib/mysql\n',s)
    def test_interruptores_y_ddl_seguros(self):
        s=(ROOT/'application.properties.template').read_text()
        for c in ['recepcion','procesamiento','medios','envio','envio-real','conexion-meta']:self.assertIn('dimabug.whatsapp.meta-qa.'+c+'=false',s)
        self.assertIn('spring.jpa.hibernate.ddl-auto=validate',s);self.assertIn('sslMode=VERIFY_IDENTITY',s)
        self.assertIn('logging.level.org.hibernate.orm.connections.pooling=WARN',s)
    def test_scripts_preparados_sin_ejecutarlos(self):
        for p in ROOT.glob('*.py'):ast.parse(p.read_text(encoding='utf-8'))

if __name__=='__main__':unittest.main()
