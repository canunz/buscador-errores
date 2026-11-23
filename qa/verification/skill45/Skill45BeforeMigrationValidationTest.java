package cl.casol.backend;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Skill45BeforeMigrationValidationTest {
 static DriverManagerDataSource dataSource() {
  String url=System.getenv("SKILL45_URL");
  assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"+System.getenv("SKILL45_PORT")+"/s45_ticket_"));
  var ds=new DriverManagerDataSource(url,"qa_runtime",System.getenv("SKILL45_PASSWORD"));
  var props=new Properties();props.setProperty("trustCertificateKeyStoreUrl",System.getenv("SKILL45_STORE_URI"));
  props.setProperty("trustCertificateKeyStoreType","JKS");props.setProperty("trustCertificateKeyStorePassword","qa-public-ca-integrity");
  ds.setConnectionProperties(props);return ds;
 }
 @Test void validateRealRechazaElEsquemaAnteriorPorLasColumnasNuevas() {
  var ds=dataSource();var jdbc=new JdbcTemplate(ds);
  assertEquals(System.getenv("SKILL45_SERVER_UUID"),jdbc.queryForObject("SELECT @@server_uuid",String.class));
  assertEquals(System.getenv("SKILL45_DATABASE"),jdbc.queryForObject("SELECT DATABASE()",String.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ticket' AND column_name='sol_nombre_completo'",Integer.class));
  var bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(ds);
  bean.setPackagesToScan("cl.casol.backend.tickets.infrastructure.persistence.entity");
  bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));
  try {
   Exception ex=assertThrows(Exception.class,bean::afterPropertiesSet);
   StringBuilder chain=new StringBuilder();for(Throwable t=ex;t!=null;t=t.getCause())chain.append(t.getMessage());
   assertTrue(chain.toString().contains("missing column") && chain.toString().contains("sol_"),"EXPECTED_MISSING_SNAPSHOT_COLUMN");
  } finally { if(bean.getObject()!=null)bean.destroy(); }
 }
}
