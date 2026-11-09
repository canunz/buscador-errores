package cl.casol.backend.identidad.infrastructure.web.dto;

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
