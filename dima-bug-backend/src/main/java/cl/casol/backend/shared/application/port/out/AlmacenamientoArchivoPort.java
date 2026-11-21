package cl.casol.backend.shared.application.port.out;

/**
 * Puerto de salida de almacenamiento archivo.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface AlmacenamientoArchivoPort {
    // Guarda el contenido.
    String guardar(byte[] contenido);
    // Lee la referencia.
    byte[] leer(String referencia);
    /** Elimina un archivo administrado; si ya no existe, la operación es idempotente. */
    void eliminar(String referencia);
    /** Exclusivamente compensación de una creación fallida. */
    void compensar(String referencia);
}
