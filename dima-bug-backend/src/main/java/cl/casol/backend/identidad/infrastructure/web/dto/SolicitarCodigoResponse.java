package cl.casol.backend.identidad.infrastructure.web.dto;

/**
 * Datos que se devuelven al frontend con solicitar código.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record SolicitarCodigoResponse(
        String message,
        int segundosVigencia,
        int segundosReenvio,
        String modo
) {
    public SolicitarCodigoResponse(String message, int segundosVigencia, int segundosReenvio) {
        this(message, segundosVigencia, segundosReenvio, "CODIGO");
    }
}
