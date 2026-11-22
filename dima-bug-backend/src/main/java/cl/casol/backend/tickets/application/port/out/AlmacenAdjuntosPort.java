package cl.casol.backend.tickets.application.port.out;

/** Dónde quedan guardados físicamente los archivos de los tickets. */
public interface AlmacenAdjuntosPort {
    /** Guarda el contenido y devuelve la referencia para leerlo después. */
    String guardar(byte[] contenido);
    // Lee la referencia.
    byte[] leer(String referencia);
    // Elimina la referencia.
    void eliminar(String referencia);
}
