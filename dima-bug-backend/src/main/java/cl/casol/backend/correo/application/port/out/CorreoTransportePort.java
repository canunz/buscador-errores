package cl.casol.backend.correo.application.port.out;

/** Transporte de MIME codificado en base64url. No construye mensajes ni aplica reglas de negocio. */
public interface CorreoTransportePort {
    String remitente();
    boolean disponible();
    String enviar(String raw);
    /** Códigos/notificaciones: un rechazo no borra la autorización compartida de evidencias. */
    String enviarAutenticacion(String raw);
}
