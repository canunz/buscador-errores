package cl.casol.backend.shared.application.archivo;

public class ArchivoException extends RuntimeException {
    public enum Motivo { INVALIDO, DEMASIADO_GRANDE, NO_ENCONTRADO, ALMACENAMIENTO }
    private final Motivo motivo;

    public ArchivoException(Motivo motivo, String mensaje) { super(mensaje); this.motivo = motivo; }
    public ArchivoException(Motivo motivo, String mensaje, Throwable causa) {
        super(mensaje, causa); this.motivo = motivo;
    }
    public Motivo motivo() { return motivo; }
}
