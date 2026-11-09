package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.EvidenciaFotografica.Categoria;
public interface FotosEvidenciaPort {
    byte[] normalizar(byte[] entrada);
    byte[] muestra(Categoria categoria);
}
