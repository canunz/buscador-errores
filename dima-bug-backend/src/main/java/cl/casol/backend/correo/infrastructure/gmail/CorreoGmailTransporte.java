package cl.casol.backend.correo.infrastructure.gmail;

import cl.casol.backend.correo.application.port.out.CorreoTransportePort;
import cl.casol.backend.correo.application.service.GmailOAuthService;
import org.springframework.stereotype.Component;

/**
 * Correo gmail transporte (módulo de correo).
 */
@Component
public class CorreoGmailTransporte implements CorreoTransportePort {
    private final GmailOAuthService oauth;
    private final GmailConfig config;
    public CorreoGmailTransporte(GmailOAuthService oauth, GmailConfig config) {
        this.oauth = oauth;
        this.config = config;
    }
    public String remitente() { return config.remitente(); }
    // Indica si está disponible.
    public boolean disponible() { return oauth.disponible(); }
    // Envía el raw.
    public String enviar(String raw) { return oauth.enviar(raw); }
    // Envía autenticación.
    public String enviarAutenticacion(String raw) { return oauth.enviarAutenticacion(raw); }
}
