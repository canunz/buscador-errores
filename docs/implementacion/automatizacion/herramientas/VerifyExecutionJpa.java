import cl.casol.backend.ejecucion.infrastructure.persistence.entity.EjecucionPasoEntity;
import cl.casol.backend.ejecucion.infrastructure.persistence.mapper.EjecucionMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import java.util.Map;

/** Solo lectura: consulta Hibernate real, sin iniciar Boot, HTTP, jobs o migraciones. */
public class VerifyExecutionJpa {
    public static void main(String[] args) {
        var ds=new DriverManagerDataSource(System.getenv("REPAIR_DB_URL"),
                System.getenv("REPAIR_DB_USER"),System.getenv("REPAIR_DB_PASSWORD"));
        var factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("cl.casol.backend");
        factory.setPersistenceUnitName("verificacion_esquema_solo_lectura");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false"));
        try {
            factory.afterPropertiesSet();
            try(var em=factory.getObject().createEntityManager()) {
                var rows=em.createQuery("select p from EjecucionPasoEntity p",EjecucionPasoEntity.class)
                        .setHint("org.hibernate.readOnly",true).getResultList();
                for(var row:rows) EjecucionMapper.toDomain(row);
                System.out.println("hibernate_query_and_mapping=OK");
                System.out.println("rows_checked="+rows.size());
                if(args.length==1 && args[0].equals("automation")) {
                    for(String entity:java.util.List.of("ProcedimientoEntity","AuditoriaProcedimientoEntity","PasoEntity","SucursalEntity","SolicitudEvidenciaEntity","EvidenciaFotograficaEntity","EventoAutomatizacionEntity")) {
                        em.createQuery("select e from "+entity+" e").setMaxResults(1).setHint("org.hibernate.readOnly",true).getResultList();
                        System.out.println("automation_mapping_"+entity+"=OK");
                    }
                }
            }
        } catch(Exception e) {
            // Las causas de conexión podrían contener información de configuración; no mostrarlas.
            System.err.println("hibernate_verification_failed="+e.getClass().getSimpleName());
            System.exit(2);
        } finally { factory.destroy(); }
    }
}
