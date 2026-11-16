package cl.casol.backend.tickets.application.port.out;

/** Dónde quedan guardados físicamente los archivos de los tickets. */
public interface AlmacenAdjuntosPort {
    /** Guarda el contenido y devuelve la referencia para leerlo después. */
    String guardar(byte[] contenido);
    byte[] leer(String referencia);
    void eliminar(String referencia);
}
