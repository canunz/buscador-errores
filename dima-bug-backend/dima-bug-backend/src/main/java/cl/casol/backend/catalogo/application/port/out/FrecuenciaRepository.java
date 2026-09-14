package cl.casol.backend.catalogo.application.port.out;

import cl.casol.backend.catalogo.domain.Frecuencia;

import java.util.Optional;
import java.util.List;

public interface FrecuenciaRepository {
    Optional<Frecuencia> buscarActivaPorId(Integer id);
    List<Frecuencia> buscarActivasOrdenadas();
}
