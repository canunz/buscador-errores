package cl.casol.backend.correo.application.port.out;

/** Transporte de MIME codificado en base64url. No construye mensajes ni aplica reglas de negocio. */
public interface CorreoTransportePort {
    String remitente();
    // Indica si está disponible.
    boolean disponible();
    // Envía el raw.
    String enviar(String raw);
    /** Códigos/notificaciones: un rechazo no borra la autorización compartida de evidencias. */
    String enviarAutenticacion(String raw);
}
