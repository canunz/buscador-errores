package cl.casol.backend.whatsapp.infrastructure.persistence;

public final class PerfilesWhatsApp {
    public static final String DURABLE="((qa-whatsapp-durable & qa-whatsapp-http-local) | qa-whatsapp-meta) & !staging & !prod & !production";
    private PerfilesWhatsApp(){ }
}
