package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.CausaRepository;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.domain.exception.CausaNoEncontradaException;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Servicio de mantener causa (módulo de conocimiento).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
@Transactional(readOnly = true)
public class MantenerCausaService {
    private final ConocimientoRepository conocimientos;
    private final CausaRepository causas;
    private final IndexarConocimientoService indexador;

    public MantenerCausaService(ConocimientoRepository conocimientos, CausaRepository causas,
            IndexarConocimientoService indexador) {
        this.conocimientos = conocimientos;
        this.causas = causas;
        this.indexador = indexador;
    }

    // Lista el conocimiento indicado.
    public List<Causa> listar(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return causas.buscarPorConocimientoOrdenadas(conocimientoId);
    }

    // Crea el conocimiento indicado.
    @Transactional
    public Causa crear(Integer conocimientoId, String descripcion, Integer orden) {
        verificarConocimiento(conocimientoId);
        Causa guardada = causas.guardar(new Causa(null, conocimientoId, descripcion, ordenOValorInicial(orden)));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    // Modifica el conocimiento indicado.
    @Transactional
    public Causa modificar(Integer conocimientoId, Integer causaId, String descripcion, Integer orden) {
        verificarConocimiento(conocimientoId);
        Causa actual = causas.buscarPorId(causaId)
                .filter(causa -> conocimientoId.equals(causa.conocimientoId()))
                .orElseThrow(() -> new CausaNoEncontradaException(causaId));
        Causa guardada = causas.guardar(new Causa(actual.id(), conocimientoId, descripcion, ordenOValorInicial(orden)));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    // Verifica conocimiento.
    private void verificarConocimiento(Integer conocimientoId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
    }

    // Devuelve orden o valor inicial.
    private int ordenOValorInicial(Integer orden) {
        return orden == null ? 1 : orden;
    }
}
