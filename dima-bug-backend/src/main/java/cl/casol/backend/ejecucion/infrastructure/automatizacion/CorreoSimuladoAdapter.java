package cl.casol.backend.ejecucion.infrastructure.automatizacion;
import cl.casol.backend.ejecucion.application.port.out.CorreoEvidenciaPort;
import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
import cl.casol.backend.ejecucion.domain.exception.ReglaEjecucionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("!'SMTP'.equalsIgnoreCase('${dimabug.automatizacion.modo:DESHABILITADO}') && !'GMAIL_API'.equalsIgnoreCase('${dimabug.automatizacion.modo:DESHABILITADO}')")
public class CorreoSimuladoAdapter implements CorreoEvidenciaPort {
    private final boolean simulado;
    public CorreoSimuladoAdapter(@Value("${dimabug.automatizacion.modo:DESHABILITADO}") String modo,Environment entorno) {
        boolean produccion=Arrays.stream(entorno.getActiveProfiles()).anyMatch(p->p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production") || p.equalsIgnoreCase("produccion"));
        if(produccion && modo.equalsIgnoreCase("SIMULADO"))throw new IllegalStateException("La simulación de evidencias está prohibida en producción");
        String url=entorno.getProperty("spring.datasource.url","");
        if(modo.equalsIgnoreCase("SIMULADO") && !url.isBlank() && !url.startsWith("jdbc:h2:mem:") && !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost|\\[::1\\])(:[0-9]+)?/.*"))
            throw new IllegalStateException("La simulación solo admite una base local de desarrollo o H2 efímero");
        simulado=modo.equalsIgnoreCase("SIMULADO");
    }
    public boolean simulado(){return simulado;}
    public boolean disponible(){return simulado;}
    public String enviar(SolicitudEvidencia s,int regla) {
        if(!simulado)throw new ReglaEjecucionException("Gmail corporativo no está conectado. Configura SIMULADO solo en desarrollo para probar el flujo sin correos reales");
        // Sin efectos externos. ID estable: el envío lógico y su auditoría se confirman con la transacción SQL.
        return "SIMULADO-"+s.id()+"-"+regla;
    }
}
