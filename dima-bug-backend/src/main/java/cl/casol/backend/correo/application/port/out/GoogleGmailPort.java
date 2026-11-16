package cl.casol.backend.correo.application.port.out;

import java.time.Instant;
import java.util.Set;

public interface GoogleGmailPort {
    final class Tokens {
        public final String acceso,refresco;public final Instant expira;public final Set<String> scopes;
        public Tokens(String acceso,String refresco,Instant expira,Set<String> scopes){this.acceso=acceso;this.refresco=refresco;this.expira=expira;this.scopes=Set.copyOf(scopes);}
        @Override public String toString(){return "TokensOAuth[redactado]";}
    }
    String autorizacion(String estado,String verificador);
    Tokens intercambiar(String codigo,String estado,String verificador);
    Tokens renovar(String refresco);
    void revocar(String token);
    String enviar(String acceso,String mensajeBase64Url);
}
