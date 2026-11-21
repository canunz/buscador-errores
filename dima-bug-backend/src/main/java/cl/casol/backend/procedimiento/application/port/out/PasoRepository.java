package cl.casol.backend.procedimiento.application.port.out;

import cl.casol.backend.procedimiento.domain.Paso;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de paso.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface PasoRepository {
    // Busca por procedimiento ordenados.
    List<Paso> buscarPorProcedimientoOrdenados(Integer procedimientoId);
    // Busca por id.
    Optional<Paso> buscarPorId(Integer id);
    // Indica si existe orden.
    boolean existeOrden(Integer procedimientoId, Integer orden);
    // Indica si existe orden excluyendo paso.
    boolean existeOrdenExcluyendoPaso(Integer procedimientoId, Integer orden, Integer pasoId);
    // Guarda el paso.
    Paso guardar(Paso paso);
    // Elimina por id.
    default void eliminar(Integer id){throw new UnsupportedOperationException();}
    // Reordena los pasos.
    default void reordenar(List<Paso> pasos){throw new UnsupportedOperationException();}
    // Configura el paso.
    default Paso configurar(Paso paso,Integer actorId){return guardar(paso);}
}
