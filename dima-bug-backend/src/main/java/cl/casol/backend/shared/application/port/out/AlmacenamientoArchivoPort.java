package cl.casol.backend.shared.application.port.out;

public interface AlmacenamientoArchivoPort {
    String guardar(byte[] contenido);
    byte[] leer(String referencia);
    /** Exclusivamente compensación de una creación fallida. */
    void compensar(String referencia);
}
