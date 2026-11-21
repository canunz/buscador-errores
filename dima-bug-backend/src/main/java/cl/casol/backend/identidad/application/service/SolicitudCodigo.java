package cl.casol.backend.identidad.application.service;

/** Datos que el frontend necesita para mostrar el contador, el reenvío o el ingreso con contraseña. */
public record SolicitudCodigo(int segundosVigencia, int segundosReenvio, String modo) {
    public static final String CODIGO = "CODIGO";
    public static final String PASSWORD = "PASSWORD";

    public SolicitudCodigo(int segundosVigencia, int segundosReenvio) {
        this(segundosVigencia, segundosReenvio, CODIGO);
    }

    public static SolicitudCodigo password() {
        return new SolicitudCodigo(0, 0, PASSWORD);
    }
}
