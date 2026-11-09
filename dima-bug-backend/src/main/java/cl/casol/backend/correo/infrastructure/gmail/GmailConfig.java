package cl.casol.backend.correo.infrastructure.gmail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class GmailConfig implements cl.casol.backend.correo.application.port.out.GmailSettings {
    public static final String SCOPE="https://www.googleapis.com/auth/gmail.send";
    public final boolean oauthHabilitado,envioHabilitado;
    public final String clientId,clientSecret,callback,remitente,claveCifrado,frontend;
    public GmailConfig(@Value("${dimabug.gmail.oauth-habilitado:false}") boolean oauth,
        @Value("${dimabug.gmail.envio-real-habilitado:false}") boolean envio,
        @Value("${dimabug.gmail.client-id:}") String id,@Value("${dimabug.gmail.client-secret:}") String secreto,
        @Value("${dimabug.gmail.callback:http://localhost:9090/login/oauth2/code/google}") String callback,
        @Value("${dimabug.gmail.remitente:}") String remitente,@Value("${dimabug.gmail.clave-cifrado:}") String clave,
        @Value("${dimabug.gmail.frontend:http://localhost:4200/admin/gmail}") String frontend){
        oauthHabilitado=oauth;envioHabilitado=envio;clientId=id;clientSecret=secreto;this.callback=callback;this.remitente=remitente;claveCifrado=clave;this.frontend=frontend;
    }
    public boolean configurado(){
        try{return oauthHabilitado && !clientId.isBlank() && !clientSecret.isBlank() &&
            remitenteValido() && uriSegura(callback,"/login/oauth2/code/google") && uriSegura(frontend,"/admin/gmail");}
        catch(RuntimeException ex){return false;}
    }
    /** Remitente de la API: Gmail personal o @dimarsa.cl. No usa DestinatarioPaso (solo sucursales). */
    boolean remitenteValido(){
        String correo=remitente==null?"":remitente.strip();
        return correo.length()<=120 && correo.matches("(?i)[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,24}");
    }
    public boolean oauthHabilitado(){return oauthHabilitado;}
    public boolean envioHabilitado(){return envioHabilitado;}
    public String remitente(){return remitente;}
    private boolean uriSegura(String texto,String ruta){
        var u=URI.create(texto);return u.getHost()!=null && u.getUserInfo()==null && u.getQuery()==null && u.getFragment()==null && ruta.equals(u.getPath()) &&
            ("https".equals(u.getScheme()) || ("http".equals(u.getScheme()) && "localhost".equals(u.getHost())));
    }
}
