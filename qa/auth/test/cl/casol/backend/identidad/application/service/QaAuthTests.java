package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import dimabug.qa.otp.*;
import org.springframework.core.env.*;
import org.springframework.transaction.support.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.lang.reflect.Proxy;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Real authentication service, in-memory repositories/transactions. NO Spring context or MySQL. */
public class QaAuthTests {
    static int checks;static void check(boolean ok){if(!ok)throw new AssertionError("QA_AUTH_ASSERT_"+checks);checks++;}
    interface Action{void run()throws Exception;}
    static void rejects(Action a)throws Exception{try{a.run();}catch(CodigoAccesoInvalidoException|IllegalStateException e){checks++;return;}throw new AssertionError("QA_AUTH_EXPECTED_REJECTION");}
    static class Time extends Clock {
        Instant now=Instant.parse("2026-11-13T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
    }
    static void begin(){TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);}
    static void finish(boolean commit){
        var callbacks=TransactionSynchronizationManager.getSynchronizations();
        try{if(commit)for(var s:callbacks)s.afterCommit();for(var s:callbacks)s.afterCompletion(commit?0:1);}
        finally{TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    static class Fixture {
        final Time time=new Time();final Map<Long,CodigoAcceso> codes=new LinkedHashMap<>();final byte[] key=new byte[32];
        final String run="a".repeat(32);final Path folder;long sequence;boolean active=true,roleActive=true;int logins;
        final QaOtpAdapter adapter;final AutenticarUsuarioService service;
        Fixture(Path base,String name)throws Exception{
            folder=base.resolve(name);Files.createDirectories(folder);new java.security.SecureRandom().nextBytes(key);
            adapter=new QaOtpAdapter(folder,key,run,time);
            var repo=(CodigoAccesoRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CodigoAccesoRepository.class},(o,m,a)->switch(m.getName()){
                case "bloquearUsuario" -> null;
                case "guardar" -> {var c=(CodigoAcceso)a[0];if(c.id()==null)c=new CodigoAcceso(++sequence,c.usuarioId(),c.codigoHash(),c.creadoEn(),c.expiraEn(),c.intentos(),c.usado());codes.put(c.id(),c);yield c;}
                case "buscarUltimo" -> codes.values().stream().reduce((x,y)->y);
                case "buscarPendiente" -> codes.values().stream().filter(c->!c.usado()).reduce((x,y)->y);
                case "invalidarPendientes" -> {codes.replaceAll((id,c)->c.marcarUsado());yield null;}
                case "contarDesde" -> codes.values().stream().filter(c->!c.creadoEn().isBefore((LocalDateTime)a[1])).count();
                default -> throw new UnsupportedOperationException();
            });
            var users=(UsuarioRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{UsuarioRepository.class},(o,m,a)->switch(m.getName()){
                case "buscarPorEmail" -> "usuario@qa.dimabug.test".equals(a[0])?Optional.of(new Usuario(1004,new Rol(1004,"USUARIO",null,roleActive,null,null),"QA","usuario@qa.dimabug.test","unused",active,null,null)):Optional.empty();
                case "registrarIngreso" -> {logins++;yield null;}
                default -> throw new UnsupportedOperationException();
            });
            var bcrypt=new BCryptPasswordEncoder(4);
            PasswordEncoderPort encoder=new PasswordEncoderPort(){public String codificar(String v){return bcrypt.encode(v);}public boolean coincide(String p,String h){return bcrypt.matches(p,h);}};
            service=new AutenticarUsuarioService(users,repo,adapter,encoder,time,5,60);
        }
        String delivered()throws Exception{
            Path p=folder.resolve(sequence+".otp");byte[] file=Files.readAllBytes(p);
            Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(file,6,18)));
            aes.updateAAD(("dimabug-qa-otp-v1|"+run+"|"+sequence).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new String(aes.doFinal(Arrays.copyOfRange(file,18,file.length)),java.nio.charset.StandardCharsets.UTF_8).split("\n")[3];
        }
        void request(){begin();try{service.solicitarCodigo("usuario@qa.dimabug.test");}finally{finish(true);}}
    }
    public static void main(String[] args)throws Exception{
        Path base=Path.of(args[0]);Files.createDirectories(base);
        Fixture f=new Fixture(base,"commit");begin();f.service.solicitarCodigo("usuario@qa.dimabug.test");
        check(f.codes.size()==1);check(Files.list(f.folder).count()==0);finish(true);
        check(Files.exists(f.folder.resolve("1.otp")));String code=f.delivered();check(code.matches("[0-9]{6}"));
        check(!Files.readString(f.folder.resolve("1.otp"),java.nio.charset.StandardCharsets.ISO_8859_1).contains(code));
        check(f.service.verificarCodigo("usuario@qa.dimabug.test",code).getId()==1004);check(f.logins==1);rejects(()->f.service.verificarCodigo("usuario@qa.dimabug.test",code));
        Fixture r=new Fixture(base,"rollback");begin();r.service.solicitarCodigo("usuario@qa.dimabug.test");finish(false);check(Files.list(r.folder).count()==0);
        Fixture failed=new Fixture(base,"failed-delivery");Files.createDirectory(failed.folder.resolve("1.otp"));failed.request();
        check(Files.exists(failed.folder.resolve("1.failed")));check(failed.codes.size()==1);
        Fixture e=new Fixture(base,"expiry");e.request();String expired=e.delivered();e.time.now=e.time.now.plusSeconds(300);rejects(()->e.service.verificarCodigo("usuario@qa.dimabug.test",expired));
        Fixture attempts=new Fixture(base,"attempts");attempts.request();String good=attempts.delivered();String wrong=String.format(java.util.Locale.ROOT,"%06d",(Integer.parseInt(good)+1)%1_000_000);
        for(int i=0;i<5;i++)rejects(()->attempts.service.verificarCodigo("usuario@qa.dimabug.test",wrong));
        check(attempts.codes.get(1L).intentos()==5);rejects(()->attempts.service.verificarCodigo("usuario@qa.dimabug.test",good));
        Fixture limits=new Fixture(base,"limits");limits.request();limits.request();check(limits.codes.size()==1);
        for(int i=0;i<5;i++){limits.time.now=limits.time.now.plusSeconds(60);limits.request();}
        check(limits.codes.size()==5);check(limits.codes.get(1L).usado());
        check(Files.list(limits.folder).filter(p->p.toString().endsWith(".otp")).count()==1);
        Fixture daily=new Fixture(base,"daily");
        for(int i=0;i<21;i++){daily.time.now=daily.time.now.plusSeconds(901);daily.request();}
        check(daily.codes.size()==20);
        Fixture unknown=new Fixture(base,"unknown");begin();unknown.service.solicitarCodigo("not-registered@qa.dimabug.test");finish(true);check(unknown.codes.isEmpty());
        unknown.active=false;unknown.request();check(unknown.codes.isEmpty());unknown.active=true;unknown.roleActive=false;unknown.request();check(unknown.codes.isEmpty());
        rejects(()->f.adapter.encolar(f.codes.get(1L),"usuario@qa.dimabug.test","QA",code,5));
        begin();rejects(()->f.adapter.encolar(f.codes.get(1L),"outside@example.invalid","QA",code,5));finish(false);
        StandardEnvironment env=new StandardEnvironment();for(var source:new ArrayList<PropertySource<?>>(env.getPropertySources().stream().toList()))env.getPropertySources().remove(source.getName());
        Map<String,Object> props=new HashMap<>();props.put("dimabug.qa.otp.enabled","true");props.put("server.address","127.0.0.1");props.put("server.port","19090");props.put("spring.datasource.username","dimabug_qa_app");props.put("spring.jpa.hibernate.ddl-auto","validate");props.put("spring.sql.init.mode","never");
        for(String k:List.of("dimabug.gmail.oauth-habilitado","dimabug.gmail.envio-real-habilitado","dimabug.auth.correo.habilitado","dimabug.automatizacion.seguimiento-habilitado"))props.put(k,"false");
        props.put("dimabug.automatizacion.modo","DESHABILITADO");props.put("spring.datasource.url","jdbc:mysql://127.0.0.1:13318/dima_bug_qa_mvp?serverTimezone=UTC&connectTimeout=3000&socketTimeout=8000&sslMode=VERIFY_CA&fallbackToSystemTrustStore=false&allowPublicKeyRetrieval=false&tlsVersions=TLSv1.2,TLSv1.3&socketFactory=dimabug.qa.PinnedSocketFactory");props.put("spring.autoconfigure.exclude","org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration");
        env.getPropertySources().addFirst(new MapPropertySource("qa-unit",props));env.setActiveProfiles("qa-local");QaOtpConfiguration.validate(env);checks++;
        var factory=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        factory.registerBeanDefinition("envioCodigoAccesoCorreoAdapter",new org.springframework.beans.factory.support.RootBeanDefinition(cl.casol.backend.identidad.infrastructure.correo.EnvioCodigoAccesoCorreoAdapter.class));
        QaOtpConfiguration.qaOtpRemoveRealAdapter(env).postProcessBeanFactory(factory);
        check(!factory.containsBeanDefinition("envioCodigoAccesoCorreoAdapter"));
        env.setActiveProfiles("staging");rejects(()->QaOtpConfiguration.validate(env));env.setActiveProfiles("qa-local");
        props.put("dimabug.gmail.envio-real-habilitado","true");rejects(()->QaOtpConfiguration.validate(env));props.put("dimabug.gmail.envio-real-habilitado","false");
        props.put("logging.level.root","DEBUG");rejects(()->QaOtpConfiguration.validate(env));props.remove("logging.level.root");
        props.put("spring.mvc.log-request-details","true");rejects(()->QaOtpConfiguration.validate(env));props.remove("spring.mvc.log-request-details");
        props.put("server.error.include-binding-errors","always");rejects(()->QaOtpConfiguration.validate(env));props.remove("server.error.include-binding-errors");
        props.put("spring.datasource.url","jdbc:mysql://remote/db");rejects(()->QaOtpConfiguration.validate(env));
        System.out.println("QA_AUTH_UNIT_CHECKS_PASSED="+checks); // Never print codes, hashes or JWT.
    }
}
