package cl.casol.backend.correo.application.port.out;

import java.time.Instant;
import java.util.Optional;

public interface GmailTokenStore {
    record Cuenta(long version,String estado,String accesoCifrado,String refrescoCifrado,String revocacionCifrada,Instant expira,Integer actor) {
        @Override public String toString(){return "CuentaGmail["+estado+"]";}
    }
    record Intento(String hashEstado,String hashNavegador,String verificadorCifrado,Integer actor,Instant expira,long versionCuenta) {
        @Override public String toString(){return "IntentoOAuth[redactado]";}
    }
    Optional<Cuenta> cuenta();
    long versionActual();
    void preparar(Intento intento,Instant ahora);
    Intento consumir(String hashEstado,String hashNavegador,Instant ahora);
    Cuenta autorizar(long esperada,String acceso,String refresco,Instant expira,Integer actor);
    Cuenta renovar(long esperada,String acceso,String refresco,Instant expira);
    void invalidar(long esperada);
    Cuenta revocarLocal(Integer actor);
    void confirmarRevocacion(long esperada,Integer actor);
}
