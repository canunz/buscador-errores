package cl.casol.backend.ejecucion.infrastructure.persistence;

import cl.casol.backend.ejecucion.infrastructure.automatizacion.CorreoSmtpAdapter;
import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
import cl.casol.backend.ejecucion.domain.exception.ReglaEjecucionException;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorreoSmtpAdapterTest {
    JavaMailSender smtp=mock(JavaMailSender.class);
    CorreoSmtpAdapter adaptador;
    @BeforeEach void preparar(){
        var factory=new DefaultListableBeanFactory();factory.registerSingleton("smtp",smtp);
        adaptador=new CorreoSmtpAdapter(factory.getBeanProvider(JavaMailSender.class),"qa@dimarsa.cl");
        when(smtp.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));
    }
    SolicitudEvidencia solicitud(){
        var d=List.of(new DestinatarioPaso("qa-destino@dimarsa.cl",null));
        var cfg=ConfiguracionPaso.resolver(new ConfiguracionPaso(d,false,"Mensaje",List.of(),false,List.of(),120,ConfiguracionPaso.Condicion.ENVIO_REGISTRADO,null,"Asunto"),ModoEjecucionPaso.ENVIAR_CORREO,false,"Mensaje");
        return new SolicitudEvidencia(UUID.randomUUID().toString(),1,1,1,new SolicitudEvidencia.Contexto(null,null,null,null,"qa-destino@dimarsa.cl",1,1,null,d),new SolicitudEvidencia.Politica("Mensaje",24,72,120,cfg),SolicitudEvidencia.Estado.PENDIENTE,null,null,0,null,null,null,0L);
    }
    @Test void autenticacionFallaConMensajeSeguro(){
        doThrow(new MailAuthenticationException("PASSWORD_SINTETICA_NO_MOSTRAR")).when(smtp).send(any(MimeMessage.class));
        var error=assertThrows(ReglaEjecucionException.class,()->adaptador.enviar(solicitud(),0));
        assertTrue(error.getMessage().contains("autenticación"));assertFalse(error.getMessage().contains("PASSWORD"));assertNull(error.getCause());
    }
    @Test void conexionFallaSinAfirmarEntregaNiExponerRespuesta(){
        doThrow(new MailSendException("TOKEN_SINTETICO_NO_MOSTRAR")).when(smtp).send(any(MimeMessage.class));
        var error=assertThrows(ReglaEjecucionException.class,()->adaptador.enviar(solicitud(),0));assertTrue(error.getMessage().contains("no confirmó"));assertFalse(error.getMessage().contains("TOKEN"));
    }
    @Test void configuracionAusenteNoSimulaUnEnvioReal(){
        var vacia=new DefaultListableBeanFactory();var sinConfig=new CorreoSmtpAdapter(vacia.getBeanProvider(JavaMailSender.class),"");
        assertFalse(sinConfig.disponible());assertFalse(sinConfig.simulado());assertThrows(ReglaEjecucionException.class,()->sinConfig.enviar(solicitud(),0));verifyNoInteractions(smtp);
    }
    @Test void remitenteInvalidoDeshabilitaElEnvio(){
        var factory=new DefaultListableBeanFactory();factory.registerSingleton("smtp",smtp);assertFalse(new CorreoSmtpAdapter(factory.getBeanProvider(JavaMailSender.class),"no-es-correo").disponible());
    }
}
