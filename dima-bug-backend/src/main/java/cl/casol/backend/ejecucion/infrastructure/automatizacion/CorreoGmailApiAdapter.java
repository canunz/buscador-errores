package cl.casol.backend.ejecucion.infrastructure.automatizacion;
import cl.casol.backend.ejecucion.application.port.out.CorreoEvidenciaPort;
import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
import cl.casol.backend.ejecucion.domain.exception.ReglaEjecucionException;
import cl.casol.backend.correo.application.port.out.CorreoTransportePort;
import cl.casol.backend.correo.application.service.GmailException;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
@Component @ConditionalOnProperty(name="dimabug.automatizacion.modo",havingValue="GMAIL_API")
public class CorreoGmailApiAdapter implements CorreoEvidenciaPort {
    private final CorreoTransportePort gmail;
    public CorreoGmailApiAdapter(CorreoTransportePort gmail){this.gmail=gmail;}
    public boolean simulado(){return false;}
    public boolean disponible(){return gmail.disponible();}
    public String transporte(){return "GMAIL_API";}
    public String enviar(SolicitudEvidencia s,int regla){
        try{var mime=MensajeCorreoMime.crear(s,regla,gmail.remitente());var bytes=new ByteArrayOutputStream();mime.writeTo(bytes);return gmail.enviar(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()));}
        catch(GmailException ex){throw new ReglaEjecucionException(ex.getMessage());}
        catch(Exception ex){throw new ReglaEjecucionException("No fue posible preparar o confirmar el correo con Gmail API");}
    }
}
