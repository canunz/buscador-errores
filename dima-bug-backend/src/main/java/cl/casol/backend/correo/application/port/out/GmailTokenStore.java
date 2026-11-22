package cl.casol.backend.correo.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Contrato de gmail token store (módulo de correo).
 */
public interface GmailTokenStore {
    // Devuelve cuenta.
    record Cuenta(long version,String estado,String accesoCifrado,String refrescoCifrado,String revocacionCifrada,Instant expira,Integer actor) {
        @Override public String toString(){return "CuentaGmail["+estado+"]";}
    }
    // Devuelve intento.
    record Intento(String hashEstado,String hashNavegador,String verificadorCifrado,Integer actor,Instant expira,long versionCuenta) {
        @Override public String toString(){return "IntentoOAuth[redactado]";}
    }
    Optional<Cuenta> cuenta();
    // Devuelve versión actual.
    long versionActual();
    // Prepara el intento.
    void preparar(Intento intento,Instant ahora);
    // Consume el hash estado.
    Intento consumir(String hashEstado,String hashNavegador,Instant ahora);
    // Autoriza la esperada.
    Cuenta autorizar(long esperada,String acceso,String refresco,Instant expira,Integer actor);
    // Renueva la esperada.
    Cuenta renovar(long esperada,String acceso,String refresco,Instant expira);
    // Invalida la esperada.
    void invalidar(long esperada);
    // Revoca local.
    Cuenta revocarLocal(Integer actor);
    // Confirma revocación.
    void confirmarRevocacion(long esperada,Integer actor);
}
