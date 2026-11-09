package cl.casol.backend.correo.infrastructure.gmail;
import cl.casol.backend.correo.application.port.out.PruebaGmailTransport;
import cl.casol.backend.correo.application.service.*;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import cl.casol.backend.ejecucion.infrastructure.automatizacion.MensajeCorreoMime;
import org.springframework.stereotype.Component;
import java.util.*;
import java.io.ByteArrayOutputStream;
@Component
public class PruebaGmailApiTransport implements PruebaGmailTransport {
    private final GmailOAuthService gmail;private final GmailConfig config;
    public PruebaGmailApiTransport(GmailOAuthService gmail,GmailConfig config){this.gmail=gmail;this.config=config;}
    public String enviar(String destinatario,String referencia){
        try{var mime=MensajeCorreoMime.crear(config.remitente,List.of(new DestinatarioPaso(destinatario,null)),"[DimaBug] Prueba controlada Gmail API","Mensaje de prueba controlada de DimaBug. Confirma manualmente su recepción. Este envío no modifica procedimientos ni acredita recepción de evidencias.","DIMABUG-PRUEBA-"+referencia);var bytes=new ByteArrayOutputStream();mime.writeTo(bytes);return gmail.enviar(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()));}
        catch(GmailException ex){throw ex;}catch(Exception ex){throw new GmailException(GmailException.Tipo.CONFIGURACION);}
    }
}
