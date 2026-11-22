package cl.casol.backend.seguimiento.application.port.out;

import cl.casol.backend.seguimiento.domain.*;
import java.util.List;

/**
 * Repositorio Spring Data de resultado solución.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface ResultadoSolucionRepository {
    // Guarda el resultado.
    ResultadoSolucion guardar(ResultadoSolucion resultado);
    // Busca por solución ordenados.
    List<ResultadoSolucion> buscarPorSolucionOrdenados(Integer solucionId);
    // Calcula efectividad.
    EfectividadSolucion calcularEfectividad(Integer solucionId);
}
