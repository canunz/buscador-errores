package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.EvidenciaFotografica.Categoria;
/**
 * Puerto de salida de fotos evidencia.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface FotosEvidenciaPort {
    // Normaliza con los datos recibidos.
    byte[] normalizar(byte[] entrada);
    // Devuelve muestra.
    byte[] muestra(Categoria categoria);
}
