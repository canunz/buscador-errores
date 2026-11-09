package cl.casol.backend.correo.application.service;

import cl.casol.backend.correo.application.port.out.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cl.casol.backend.correo.application.service.GmailException.Tipo.*;

@Service
public class GmailOAuthService {
    public static final String SCOPE="https://www.googleapis.com/auth/gmail.send";
    private final GmailSettings config;private final GmailTokenStore store;private final GoogleGmailPort google;private final CifradoGmailPort cifrado;private final UsuarioRepository usuarios;private final Clock reloj;
    private final SecureRandom random=new SecureRandom();
    public GmailOAuthService(GmailSettings config,GmailTokenStore store,GoogleGmailPort google,CifradoGmailPort cifrado,UsuarioRepository usuarios,Clock reloj){this.config=config;this.store=store;this.google=google;this.cifrado=cifrado;this.usuarios=usuarios;this.reloj=reloj;}
    public record Estado(boolean configurado,boolean conectado,boolean envioRealHabilitado,String estado,Instant expira,String remitente,boolean revocacionRemotaPendiente){}
    public record Inicio(String url,String navegador) { @Override public String toString(){return "InicioOAuth[redactado]";} }
    private Integer admin(String email){return usuarios.buscarPorEmail(email).filter(u->u.isActivo() && u.getRol()!=null && "ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre())).orElseThrow(()->new AccessDeniedException("Solo un administrador activo puede autorizar Gmail")).getId();}
    private void admin(Integer id){usuarios.buscarPorId(id).filter(u->u.isActivo() && u.getRol()!=null && "ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre())).orElseThrow(()->new AccessDeniedException("El administrador que inició OAuth ya no tiene autorización"));}
    private boolean configurado(){return config.configurado() && cifrado.disponible();}
    private void exigir(){if(!configurado())throw new GmailException(CONFIGURACION);}
    public Estado estado(String email){admin(email);if(!configurado())return new Estado(false,false,false,"NO_CONFIGURADO",null,config.remitente(),false);var c=store.cuenta().orElse(null);boolean activa=c!=null && "ACTIVA".equals(c.estado());return new Estado(true,activa,config.envioHabilitado(),c==null?"DESCONECTADA":c.estado(),c==null?null:c.expira(),config.remitente(),c!=null && c.revocacionCifrada()!=null);}
    private String secreto(){byte[] valor=new byte[32];random.nextBytes(valor);return Base64.getUrlEncoder().withoutPadding().encodeToString(valor);}
    private String hash(String valor){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new GmailException(CONFIGURACION);}}
    public Inicio iniciar(String email){
        Integer actor=admin(email);exigir();String estado=secreto(),navegador=secreto(),pkce=secreto();String h=hash(estado);
        store.preparar(new GmailTokenStore.Intento(h,hash(navegador),cifrado.cifrar(pkce,"gmail:pkce:"+h),actor,reloj.instant().plusSeconds(300),store.versionActual()),reloj.instant());
        return new Inicio(google.autorizacion(estado,pkce),navegador);
    }
    public void completar(String estado,String navegador,String codigo,String error){
        exigir();if(estado==null || !estado.matches("[A-Za-z0-9_-]{43}") || navegador==null || !navegador.matches("[A-Za-z0-9_-]{43}"))throw new GmailException(ESTADO);
        var intento=store.consumir(hash(estado),hash(navegador),reloj.instant());admin(intento.actor());
        var actual=store.cuenta().orElseThrow(()->new GmailException(CONFLICTO));
        if(actual.version()!=intento.versionCuenta() || actual.revocacionCifrada()!=null || "ACTIVA".equals(actual.estado()))throw new GmailException(CONFLICTO);
        if(error!=null || codigo==null || codigo.isBlank() || codigo.length()>4096)throw new GmailException(AUTORIZACION);
        var tokens=google.intercambiar(codigo,estado,cifrado.descifrar(intento.verificadorCifrado(),"gmail:pkce:"+intento.hashEstado()));
        validar(tokens,false);if(tokens.refresco==null || tokens.refresco.isBlank())throw new GmailException(AUTORIZACION);
        store.autorizar(intento.versionCuenta(),cifrado.cifrar(tokens.acceso,"gmail:access"),cifrado.cifrar(tokens.refresco,"gmail:refresh"),tokens.expira,intento.actor());
    }
    private void validar(GoogleGmailPort.Tokens t,boolean renovar){if(t.acceso==null || t.acceso.isBlank() || t.expira==null || !t.expira.isAfter(reloj.instant().plusSeconds(30)) || !(t.scopes.equals(Set.of(SCOPE)) || renovar && t.scopes.isEmpty()))throw new GmailException(AUTORIZACION);}
    public boolean disponible(){
        try{return configurado() && config.envioHabilitado() && store.cuenta().filter(c->"ACTIVA".equals(c.estado())).isPresent();}
        catch(RuntimeException ex){return false;}
    }
    private static final class Acceso {final String token;final long version;Acceso(String token,long version){this.token=token;this.version=version;}}
    private synchronized Acceso acceso(){
        exigir();var c=store.cuenta().filter(v->"ACTIVA".equals(v.estado())).orElseThrow(()->new GmailException(AUTORIZACION));
        if(c.expira()!=null && c.expira().isAfter(reloj.instant().plusSeconds(60)))return new Acceso(cifrado.descifrar(c.accesoCifrado(),"gmail:access"),c.version());
        try{var tokens=google.renovar(cifrado.descifrar(c.refrescoCifrado(),"gmail:refresh"));validar(tokens,true);
            var actualizada=store.renovar(c.version(),cifrado.cifrar(tokens.acceso,"gmail:access"),tokens.refresco==null || tokens.refresco.isBlank()?c.refrescoCifrado():cifrado.cifrar(tokens.refresco,"gmail:refresh"),tokens.expira);return new Acceso(tokens.acceso,actualizada.version());
        }catch(GmailException ex){if(ex.tipo==AUTORIZACION)store.invalidar(c.version());throw ex;}
    }
    public String enviar(String raw){
        if(!disponible())throw new GmailException(CONFIGURACION);
        var acceso=acceso();try{return google.enviar(acceso.token,raw);}catch(GmailException ex){if(ex.tipo==AUTORIZACION)store.invalidar(acceso.version);throw ex;}
    }
    public Estado revocar(String email){
        Integer actor=admin(email);exigir();var c=store.revocarLocal(actor);
        if(c.revocacionCifrada()!=null){google.revocar(cifrado.descifrar(c.revocacionCifrada(),"gmail:refresh"));store.confirmarRevocacion(c.version(),actor);}
        return estado(email);
    }
}
