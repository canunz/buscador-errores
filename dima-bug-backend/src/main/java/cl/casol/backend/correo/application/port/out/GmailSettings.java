package cl.casol.backend.correo.application.port.out;
/**
 * Contrato de gmail settings (módulo de correo).
 */
public interface GmailSettings {
    // Indica si está configurado.
    boolean configurado();boolean oauthHabilitado();boolean envioHabilitado();String remitente();
    // Indica si configurado para envío.
    default boolean configuradoParaEnvio() { return configurado(); }
}
