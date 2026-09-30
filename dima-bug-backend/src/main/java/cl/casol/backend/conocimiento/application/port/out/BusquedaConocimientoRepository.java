package cl.casol.backend.conocimiento.application.port.out;

import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;
import java.util.List;

public interface BusquedaConocimientoRepository {
    List<ResultadoBusquedaConocimiento> buscar(String texto, Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId);
}
