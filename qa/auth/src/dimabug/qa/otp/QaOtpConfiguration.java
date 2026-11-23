package dimabug.qa.otp;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.Environment;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;

@Configuration(proxyBeanMethods=false)
@Profile("qa-local")
public class QaOtpConfiguration {
    public static void validate(Environment env){
        if(!java.time.ZoneId.systemDefault().normalized().equals(java.time.ZoneOffset.UTC))throw new IllegalStateException("QA_OTP_UTC_REQUIRED");
        if(!Set.of(env.getActiveProfiles()).equals(Set.of("qa-local")))throw new IllegalStateException("QA_OTP_PROFILE_REJECTED");
        for(String level:java.util.List.of("logging.level.root","logging.level.org.springframework.web","logging.level.cl.casol.backend.identidad.infrastructure.security")){
            if(java.util.Set.of("ALL","TRACE","DEBUG").contains(env.getProperty(level,"INFO").toUpperCase(java.util.Locale.ROOT)))throw new IllegalStateException("QA_OTP_LOGGING_REJECTED");
        }
        if(!env.getProperty("spring.mvc.log-request-details","false").equals("false"))throw new IllegalStateException("QA_OTP_LOGGING_REJECTED");
        for(String property:java.util.List.of("server.error.include-message","server.error.include-binding-errors","server.error.include-stacktrace")){
            if(!env.getProperty(property,"never").equals("never"))throw new IllegalStateException("QA_OTP_ERROR_DETAILS_REJECTED");
        }
        Map<String,String> required=Map.ofEntries(
            Map.entry("dimabug.qa.otp.enabled","true"),Map.entry("server.address","127.0.0.1"),Map.entry("server.port","19090"),
            Map.entry("spring.datasource.username","dimabug_qa_app"),Map.entry("spring.jpa.hibernate.ddl-auto","validate"),
            Map.entry("spring.sql.init.mode","never"),Map.entry("dimabug.gmail.oauth-habilitado","false"),
            Map.entry("dimabug.gmail.envio-real-habilitado","false"),Map.entry("dimabug.auth.correo.habilitado","false"),
            Map.entry("dimabug.automatizacion.seguimiento-habilitado","false"),Map.entry("dimabug.automatizacion.modo","DESHABILITADO"));
        if(required.entrySet().stream().anyMatch(e->!e.getValue().equals(env.getProperty(e.getKey()))))throw new IllegalStateException("QA_OTP_ISOLATION_REJECTED");
        String url=env.getProperty("spring.datasource.url","");
        String exact="jdbc:mysql://127.0.0.1:13318/dima_bug_qa_mvp?serverTimezone=UTC&connectTimeout=3000&socketTimeout=8000&sslMode=VERIFY_CA&fallbackToSystemTrustStore=false&allowPublicKeyRetrieval=false&tlsVersions=TLSv1.2,TLSv1.3&socketFactory=dimabug.qa.PinnedSocketFactory";
        if(!url.equals(exact) ||
            !env.getProperty("spring.autoconfigure.exclude","").contains("org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration"))throw new IllegalStateException("QA_OTP_ISOLATION_REJECTED");
    }
    @Bean public static BeanFactoryPostProcessor qaOtpRemoveRealAdapter(Environment env){
        validate(env);
        return factory->{
            if(!(factory instanceof BeanDefinitionRegistry registry))throw new IllegalStateException("QA_OTP_BEAN_REGISTRY");
            String name="envioCodigoAccesoCorreoAdapter";
            if(!registry.containsBeanDefinition(name) || !"cl.casol.backend.identidad.infrastructure.correo.EnvioCodigoAccesoCorreoAdapter".equals(registry.getBeanDefinition(name).getBeanClassName()))throw new IllegalStateException("QA_OTP_REAL_ADAPTER_NOT_IDENTIFIED");
            registry.removeBeanDefinition(name);
        };
    }
    @Bean public QaOtpAdapter qaOtpAdapter(Environment env){
        validate(env);
        try{
            Path root=Path.of(env.getRequiredProperty("QA_DATA_DIR")).toRealPath();
            Path directory=Path.of(env.getRequiredProperty("QA_OTP_DIRECTORY")).toRealPath();
            if(!directory.startsWith(root.resolve("otp-runs")) || Files.isSymbolicLink(Path.of(env.getRequiredProperty("QA_OTP_DIRECTORY"))))throw new IllegalStateException();
            byte[] key=Base64.getDecoder().decode(env.getRequiredProperty("QA_OTP_KEY"));
            try{return new QaOtpAdapter(directory,key,env.getRequiredProperty("QA_OTP_RUN_ID"),Clock.systemUTC());}
            finally{Arrays.fill(key,(byte)0);}
        }catch(Exception e){throw new IllegalStateException("QA_OTP_CUSTODY_OR_PATH_REJECTED");}
    }
}
