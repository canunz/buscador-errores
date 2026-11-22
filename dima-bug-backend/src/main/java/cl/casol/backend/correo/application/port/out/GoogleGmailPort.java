package cl.casol.backend.correo.application.port.out;

import java.time.Instant;
import java.util.Set;

/**
 * Puerto de salida de google gmail.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface GoogleGmailPort {
    final class Tokens {
        public final String acceso,refresco;public final Instant expira;public final Set<String> scopes;
        // Devuelve tokens.
        public Tokens(String acceso,String refresco,Instant expira,Set<String> scopes){this.acceso=acceso;this.refresco=refresco;this.expira=expira;this.scopes=Set.copyOf(scopes);}
        @Override public String toString(){return "TokensOAuth[redactado]";}
    }
    // Devuelve autorización.
    String autorizacion(String estado,String verificador);
    // Intercambia el código.
    Tokens intercambiar(String codigo,String estado,String verificador);
    // Renueva el refresco.
    Tokens renovar(String refresco);
    // Revoca el token.
    void revocar(String token);
    // Envía el acceso.
    String enviar(String acceso,String mensajeBase64Url);
}
