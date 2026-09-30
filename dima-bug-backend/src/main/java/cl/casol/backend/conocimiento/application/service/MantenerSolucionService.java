package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.application.port.out.SolucionRepository;
import cl.casol.backend.conocimiento.domain.Solucion;
import cl.casol.backend.conocimiento.domain.TipoSolucion;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.domain.exception.SolucionNoEncontradaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerSolucionService {
    private final ConocimientoRepository conocimientos;
    private final SolucionRepository soluciones;
    private final IndexarConocimientoService indexador;

    public MantenerSolucionService(ConocimientoRepository conocimientos, SolucionRepository soluciones,
            IndexarConocimientoService indexador) {
        this.conocimientos = conocimientos;
        this.soluciones = soluciones;
        this.indexador = indexador;
    }

    public List<Solucion> listar(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return soluciones.buscarPorConocimientoOrdenadas(conocimientoId);
    }

    @Transactional
    public Solucion crear(Integer conocimientoId, String descripcion, TipoSolucion tipo, Integer orden) {
        verificarConocimiento(conocimientoId);
        Solucion guardada = soluciones.guardar(new Solucion(null, conocimientoId, descripcion,
                tipo == null ? TipoSolucion.PASOS : tipo, orden == null ? 1 : orden));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    @Transactional
    public Solucion modificar(Integer conocimientoId, Integer solucionId, String descripcion,
            TipoSolucion tipo, Integer orden) {
        verificarConocimiento(conocimientoId);
        Solucion actual = buscarPerteneciente(conocimientoId, solucionId);
        Solucion guardada = soluciones.guardar(new Solucion(actual.id(), conocimientoId, descripcion,
                tipo == null ? TipoSolucion.PASOS : tipo, orden == null ? 1 : orden));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    public Solucion buscarPerteneciente(Integer conocimientoId, Integer solucionId) {
        return soluciones.buscarPorId(solucionId)
                .filter(solucion -> conocimientoId.equals(solucion.conocimientoId()))
                .orElseThrow(() -> new SolucionNoEncontradaException(solucionId));
    }

    private void verificarConocimiento(Integer conocimientoId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
    }
}
