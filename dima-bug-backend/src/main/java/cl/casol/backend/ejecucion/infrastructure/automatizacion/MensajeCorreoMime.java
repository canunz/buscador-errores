package cl.casol.backend.ejecucion.infrastructure.automatizacion;
import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import org.springframework.mail.javamail.MimeMessageHelper;
import java.util.Properties;
import java.nio.charset.StandardCharsets;

public final class MensajeCorreoMime {
    private MensajeCorreoMime(){}
    static MimeMessage crear(SolicitudEvidencia s,int regla,String remitente)throws Exception{
        if(s.politica().configuracion().asunto()==null)throw new IllegalArgumentException();
        return crear(remitente,s.contexto().destinatarios(),(regla>0?"Recordatorio: ":"")+s.politica().configuracion().asunto(),s.politica().mensaje(),"DIMABUG-"+s.id()+"-"+regla);
    }
    public static MimeMessage crear(String remitente,java.util.List<DestinatarioPaso> destinatarios,String asunto,String mensaje,String referencia)throws Exception{
        var mime=new MimeMessage(Session.getInstance(new Properties()));var helper=new MimeMessageHelper(mime,StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(remitente,true));
        for(var d:destinatarios){if(d.tipo()==DestinatarioPaso.Tipo.COPIA)helper.addCc(new InternetAddress(d.correo(),true));else helper.addTo(new InternetAddress(d.correo(),true));}
        if(asunto==null || asunto.isBlank() || asunto.contains("\r") || asunto.contains("\n"))throw new IllegalArgumentException();
        helper.setSubject(asunto);helper.setText(mensaje,false);
        mime.setHeader("X-DimaBug-Referencia",referencia);mime.saveChanges();return mime;
    }
}
