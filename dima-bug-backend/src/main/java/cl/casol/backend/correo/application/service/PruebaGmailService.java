package cl.casol.backend.correo.application.service;
import cl.casol.backend.correo.application.port.out.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.shared.domain.DestinatarioPaso;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;import java.util.*;
import static cl.casol.backend.correo.application.port.out.PruebaGmailStore.Estado.*;
@Service
public class PruebaGmailService {
    private final GmailOAuthService oauth;private final GmailSettings config;private final PruebaGmailStore store;private final PruebaGmailTransport transporte;private final UsuarioRepository usuarios;private final Clock reloj;private final String modo;
    public PruebaGmailService(GmailOAuthService oauth,GmailSettings config,PruebaGmailStore store,PruebaGmailTransport transporte,UsuarioRepository usuarios,Clock reloj,@Value("${dimabug.automatizacion.modo:DESHABILITADO}") String modo){this.oauth=oauth;this.config=config;this.store=store;this.transporte=transporte;this.usuarios=usuarios;this.reloj=reloj;this.modo=modo;}
    public record EstadoPrueba(String remitente,String destinatario,boolean conectado,String transporte,boolean habilitado,PruebaGmailStore.Prueba ultima){}
    private Integer admin(String email){return usuarios.buscarPorEmail(email).filter(u->u.isActivo() && "ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre())).orElseThrow(()->new AccessDeniedException("Solo administradores pueden probar Gmail")).getId();}
    private PruebaGmailStore.Prueba visible(PruebaGmailStore.Prueba p){if(p.estado()==ENVIANDO && p.intento()!=null && p.intento().isBefore(reloj.instant().minusSeconds(60)))return new PruebaGmailStore.Prueba(p.id(),p.actor(),p.remitente(),p.destinatario(),INCIERTO,p.creada(),p.intento(),p.finalizada(),p.mensajeId(),"CONFIRMACION_PENDIENTE");return p;}
    public EstadoPrueba estado(String email){admin(email);var o=oauth.estado(email);String destino=config.remitente();return new EstadoPrueba(destino,destino,o.conectado(),modo,o.conectado() && config.envioHabilitado() && "GMAIL_API".equalsIgnoreCase(modo),store.ultima(destino).map(this::visible).orElse(null));}
    public PruebaGmailStore.Prueba enviar(String email,String clave,String anterior,boolean confirmado,String destinatario){
        Integer actor=admin(email);if(!confirmado)throw new GmailException(GmailException.Tipo.CONFLICTO);
        String destino=new DestinatarioPaso(destinatario,null).correo();String remitente=new DestinatarioPaso(config.remitente(),null).correo();if(!destino.equals(remitente))throw new GmailException(GmailException.Tipo.CONFLICTO);
        var repetida=store.buscar(clave);if(repetida.isPresent()){if(!repetida.get().destinatario().equals(destino))throw new GmailException(GmailException.Tipo.CONFLICTO);return visible(repetida.get());}
        if(!"GMAIL_API".equalsIgnoreCase(modo) || !oauth.disponible())throw new GmailException(GmailException.Tipo.CONFIGURACION);
        var p=store.preparar(clave,anterior,actor,remitente,destino);if(!store.reservar(p.id()))return visible(store.buscar(p.id()).orElseThrow());
        try{String id=transporte.enviar(destino,p.id());return store.finalizar(p.id(),ENVIADO,id,"GMAIL_ACEPTO_ENTREGA_NO_CONFIRMADA");}
        catch(GmailException ex){return store.finalizar(p.id(),ex.tipo==GmailException.Tipo.ENVIO?INCIERTO:FALLIDO,null,ex.tipo.name());}
        catch(RuntimeException ex){return store.finalizar(p.id(),INCIERTO,null,"RESULTADO_NO_CONFIRMADO");}
    }
}
