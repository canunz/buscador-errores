package cl.casol.backend.whatsapp.application.port.out;
import cl.casol.backend.whatsapp.domain.RespuestaCanal.Candidato;
import java.util.List;
public interface ReconocerImagenPort {
    List<Candidato> reconocer(String referenciaMedio);
    class FalloOcr extends RuntimeException {
        public FalloOcr() { super("No fue posible reconocer la imagen de prueba."); }
    }
    class PlazoOcr extends FalloOcr { }
}
