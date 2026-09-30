package cl.casol.backend.seguimiento.application.port.out;

import cl.casol.backend.seguimiento.domain.*;
import java.util.List;

public interface ResultadoSolucionRepository {
    ResultadoSolucion guardar(ResultadoSolucion resultado);
    List<ResultadoSolucion> buscarPorSolucionOrdenados(Integer solucionId);
    EfectividadSolucion calcularEfectividad(Integer solucionId);
}
