import cl.casol.backend.BackendApplication;
import cl.casol.backend.ejecucion.application.service.*;
import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.application.service.AdministrarSucursalesService;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Prueba opt-in en staging local: solo fixtures nuevas, rollback obligatorio, ningún correo real. */
public class VerifyAutomationRollback {
    public static void main(String[] args) {
        String url=System.getenv("DB_URL");
        if(url==null || !url.startsWith("jdbc:mysql://127.0.0.1:3307/dima_bug_staging"))throw new IllegalStateException("Destino local no autorizado");
        try(var context=SpringApplication.run(BackendApplication.class,
                "--server.address=127.0.0.1","--server.port=0","--spring.jpa.hibernate.ddl-auto=none","--spring.sql.init.mode=never",
                "--dimabug.automatizacion.modo=SIMULADO","--dimabug.automatizacion.intervalo-ms=3600000","--logging.level.root=OFF","--spring.main.banner-mode=off")) {
            var jdbc=context.getBean(JdbcTemplate.class);var tm=context.getBean(PlatformTransactionManager.class);
            String clave=UUID.randomUUID().toString();String email="qa-rollback-"+clave+"@example.invalid";
            new TransactionTemplate(tm).executeWithoutResult(status->{
                var roles=context.getBean(RolRepository.class).buscarActivos();
                var rol=roles.stream().filter(r->r.getNombre().strip().equalsIgnoreCase("TECNICO")).findFirst().orElseThrow();
                var usuario=context.getBean(UsuarioRepository.class).guardar(new Usuario(null,rol,"QA rollback",email,"NO_LOGIN_TEST",true,LocalDateTime.now(),LocalDateTime.now()));
                var sucursal=context.getBean(AdministrarSucursalesService.class).crear("QA_"+clave.substring(0,8),"Sucursal de prueba con rollback","Responsable sintético","qa-sintetico-no-enviar@dimarsa.cl");
                var procRepo=context.getBean(ProcedimientoRepository.class);var pasoRepo=context.getBean(PasoRepository.class);
                var proc=procRepo.guardar(new Procedimiento(null,"Preparar POS · QA rollback",null,EstadoProcedimiento.PUBLICADO,usuario,LocalDateTime.now(),null,null));
                pasoRepo.guardar(new Paso(null,proc.id(),1,"Verificar mueble, red y electricidad",true,ModoEjecucionPaso.VALIDACION_EXTERNA,null));
                pasoRepo.guardar(new Paso(null,proc.id(),2,"Trabajo físico manual",true));
                var ejecutar=context.getBean(GestionarEjecucionService.class);var auto=context.getBean(AutomatizarEvidenciaService.class);
                var e=ejecutar.iniciar(proc.id(),sucursal.id(),clave,email);var s=auto.listar(e.id(),email).getFirst();
                auto.simularTiempo(e.id(),s.id(),24,email);
                var recibida=auto.recibirSimulada(e.id(),s.id(),s.contexto().responsableEmail(),UUID.randomUUID().toString(),auto.muestras(),email);
                if(ejecutar.listarPasos(e.id(),email).getFirst().ejecucionPaso().cumplido())throw new IllegalStateException("La recepción completó el paso sin aprobación");
                auto.decidir(e.id(),s.id(),recibida.version(),true,false,null,email);
                var checklist=ejecutar.listarPasos(e.id(),email);
                if(!checklist.getFirst().ejecucionPaso().cumplido() || checklist.get(1).ejecucionPaso().cumplido())throw new IllegalStateException("Progreso inválido");
                if(auto.evidencias(e.id(),s.id(),email).size()!=3)throw new IllegalStateException("Fotografías no persistidas");
                status.setRollbackOnly();System.out.println("verification_mysql_full_workflow=OK");
            });
            if(jdbc.queryForObject("select count(*) from u_usuario where usuario_email=?",Integer.class,email)!=0)throw new IllegalStateException("La fixture no fue revertida");
            System.out.println("verification_fixtures_rolled_back=OK");
            System.out.println("verification_real_emails_sent=0");
        } catch(Exception e) {
            for(Throwable cause=e;cause!=null;cause=cause.getCause()) {
                if(cause instanceof java.sql.SQLException sql)System.err.println("verification_sql_state="+sql.getSQLState()+";code="+sql.getErrorCode());
            }
            System.err.println("verification_failed="+e.getClass().getSimpleName());System.exit(2);
        }
    }
}
