package cl.casol.backend.ejecucion.infrastructure.automatizacion;

import cl.casol.backend.ejecucion.application.port.out.CorreoEvidenciaPort;
import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
import cl.casol.backend.ejecucion.domain.exception.ReglaEjecucionException;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/** Usa exclusivamente spring.mail.*. Aceptación SMTP no implica entrega al destinatario. */
@Component
@ConditionalOnProperty(name="dimabug.automatizacion.modo",havingValue="SMTP")
public class CorreoSmtpAdapter implements CorreoEvidenciaPort {
    private final JavaMailSender sender;
    private final String remitente;
    public CorreoSmtpAdapter(ObjectProvider<JavaMailSender> sender,
            @Value("${dimabug.correo.remitente:${spring.mail.username:}}") String remitente){
        this.sender=sender.getIfAvailable();this.remitente=remitente;
    }
    public boolean simulado(){return false;}
    public boolean disponible(){
        if(sender==null || remitente==null || remitente.isBlank())return false;
        try{new DestinatarioPaso(remitente,DestinatarioPaso.Tipo.PRINCIPAL);return true;}catch(cl.casol.backend.shared.domain.DestinatarioInvalidoException ex){return false;}
    }
    public String enviar(SolicitudEvidencia s,int regla){
        if(!disponible())throw new ReglaEjecucionException("SMTP no está configurado. Solicita al administrador revisar la configuración de correo");
        try{
            var mime=MensajeCorreoMime.crear(s,regla,remitente);
            String referencia="DIMABUG-"+s.id()+"-"+regla;
            sender.send(mime);
            return referencia;
        }catch(org.springframework.mail.MailAuthenticationException ex){
            throw new ReglaEjecucionException("SMTP rechazó la autenticación. El administrador debe revisar la autorización de Google Workspace");
        }catch(Exception ex){
            // No propagar respuestas SMTP ni excepciones con cuentas, hosts o credenciales.
            throw new ReglaEjecucionException("El servidor SMTP no confirmó el envío. Revisa el historial antes de reintentar; la entrega podría ser incierta");
        }
    }
}
