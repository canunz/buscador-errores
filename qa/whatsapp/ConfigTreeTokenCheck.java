import java.nio.file.*;
import java.util.Map;
import org.springframework.core.env.*;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;

/** Prueba del lector real de Spring Boot, sin contexto web ni servicios. Solo secreto sintético. */
public final class ConfigTreeTokenCheck {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("wa-configtree-test-");
        Path secret=dir.resolve("dimabug.whatsapp.meta-qa.graph-token");
        try{
            Files.writeString(secret,"TOKEN_SINTETICO_NO_REAL\n");
            var env=new StandardEnvironment();
            env.getPropertySources().addFirst(new MapPropertySource("qa-configtree-test",Map.of(
                "spring.config.location","optional:classpath:/no-qa-config-exists.properties",
                "spring.config.import","configtree:"+dir.toAbsolutePath().toString().replace('\\','/')+"/")));
            ConfigDataEnvironmentPostProcessor.applyTo(env);
            if(!"TOKEN_SINTETICO_NO_REAL".equals(env.getProperty("dimabug.whatsapp.meta-qa.graph-token")))throw new AssertionError("CONFIGTREE_VALUE_MISMATCH");
            if(env.getProperty("dimabug.whatsapp.meta-qa.envio-real")!=null)throw new AssertionError("UNEXPECTED_SEND_ACTIVATION");
            System.out.println("CONFIGTREE_PASS");
        }finally{Files.deleteIfExists(secret);Files.deleteIfExists(dir);}
    }
}
