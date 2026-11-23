import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import cl.casol.backend.identidad.infrastructure.security.JwtTokenServiceAdapter;
import cl.casol.backend.identidad.domain.Usuario;

/** Deployment-only probe. No network/SQL, no persistent credentials or tokens. */
public class DeploymentProbe {
 public static void main(String[] args) throws Exception {
  var logger=org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  if(logger instanceof ch.qos.logback.classic.Logger l)l.setLevel(ch.qos.logback.classic.Level.OFF);
  if(args[0].equals("token")) {
   var in=new DataInputStream(System.in);
   int n=in.readInt();if(n<32||n>4096)throw new IllegalArgumentException("INVALID_KEY_LENGTH");
   byte[] raw=in.readNBytes(n);if(raw.length!=n)throw new EOFException();
   int e=in.readInt();if(e<3||e>120)throw new IllegalArgumentException("INVALID_EMAIL_LENGTH");
   String email=new String(in.readNBytes(e),StandardCharsets.UTF_8);
   String token=new JwtTokenServiceAdapter(new String(raw,StandardCharsets.UTF_8),120000)
     .generarToken(new Usuario(0,null,"Deployment probe",email,"",true,null,null));
   Arrays.fill(raw,(byte)0);System.out.println(token);return;
  }
  if(!args[0].equals("bindings"))throw new IllegalArgumentException("INVALID_MODE");
  StandardEnvironment env=new StandardEnvironment();
  env.getPropertySources().addLast(new ResourcePropertySource("external-staging","file:"+args[1]));
  try(var ctx=new AnnotationConfigApplicationContext()) {
   ctx.setEnvironment(env);
   DataSource ds=new AbstractDataSource(){
    public java.sql.Connection getConnection() throws java.sql.SQLException {throw new java.sql.SQLException("PROBE_CONNECTION_FORBIDDEN");}
    public java.sql.Connection getConnection(String u,String p) throws java.sql.SQLException {return getConnection();}
   };
   ctx.registerBean(JdbcTemplate.class,()->new JdbcTemplate(ds));
   Class<?> type=Class.forName("cl.casol.backend.identidad.infrastructure.persistence.adapter.RegistroUniversalAdapter");
   ctx.register(type);ctx.refresh();Object bean=ctx.getBean(type);
   Map<String,Object> result=new LinkedHashMap<>();
   for(String field:List.of("habilitado","hora","dia","pendientes")) {
    var f=type.getDeclaredField(field);f.setAccessible(true);result.put(field,f.get(bean));
   }
   result.put("auth",env.getProperty("dimabug.auth.correo.habilitado",Boolean.class,true));
   result.put("gmail",env.getProperty("dimabug.gmail.envio-real-habilitado",Boolean.class,false));
   result.put("welcome",env.getProperty("dimabug.correo.bienvenida.habilitado",Boolean.class,false));
   result.put("critical",env.getProperty("dimabug.correo.ticket-critico.habilitado",Boolean.class,false));
   System.out.println(new tools.jackson.databind.ObjectMapper().writeValueAsString(result));
  }
 }
}
