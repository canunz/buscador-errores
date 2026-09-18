package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.CausaRepository;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.domain.Causa;
import cl.casol.backend.conocimiento.domain.exception.CausaNoEncontradaException;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerCausaService {
    private final ConocimientoRepository conocimientos;
    private final CausaRepository causas;

    public MantenerCausaService(ConocimientoRepository conocimientos, CausaRepository causas) {
        this.conocimientos = conocimientos;
        this.causas = causas;
    }

    public List<Causa> listar(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return causas.buscarPorConocimientoOrdenadas(conocimientoId);
    }

    @Transactional
    public Causa crear(Integer conocimientoId, String descripcion, Integer orden) {
        verificarConocimiento(conocimientoId);
        return causas.guardar(new Causa(null, conocimientoId, descripcion, ordenOValorInicial(orden)));
    }

    @Transactional
    public Causa modificar(Integer conocimientoId, Integer causaId, String descripcion, Integer orden) {
        verificarConocimiento(conocimientoId);
        Causa actual = causas.buscarPorId(causaId)
                .filter(causa -> conocimientoId.equals(causa.conocimientoId()))
                .orElseThrow(() -> new CausaNoEncontradaException(causaId));
        return causas.guardar(new Causa(actual.id(), conocimientoId, descripcion, ordenOValorInicial(orden)));
    }

    private void verificarConocimiento(Integer conocimientoId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
    }

    private int ordenOValorInicial(Integer orden) {
        return orden == null ? 1 : orden;
    }
}
