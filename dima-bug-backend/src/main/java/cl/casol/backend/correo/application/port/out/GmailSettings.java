package cl.casol.backend.correo.application.port.out;
public interface GmailSettings {
    boolean configurado();boolean oauthHabilitado();boolean envioHabilitado();String remitente();
}
