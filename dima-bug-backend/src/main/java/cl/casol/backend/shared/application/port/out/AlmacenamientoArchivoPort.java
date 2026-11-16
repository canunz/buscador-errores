package cl.casol.backend.shared.application.port.out;

public interface AlmacenamientoArchivoPort {
    String guardar(byte[] contenido);
    byte[] leer(String referencia);
    /** Elimina un archivo administrado; si ya no existe, la operación es idempotente. */
    void eliminar(String referencia);
    /** Exclusivamente compensación de una creación fallida. */
    void compensar(String referencia);
}
