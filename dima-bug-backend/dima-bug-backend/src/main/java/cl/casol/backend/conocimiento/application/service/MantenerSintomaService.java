package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.application.port.out.SintomaRepository;
import cl.casol.backend.conocimiento.domain.Sintoma;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.domain.exception.SintomaNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerSintomaService {
    private final ConocimientoRepository conocimientos;
    private final SintomaRepository sintomas;

    public MantenerSintomaService(ConocimientoRepository conocimientos, SintomaRepository sintomas) {
        this.conocimientos = conocimientos;
        this.sintomas = sintomas;
    }

    public List<Sintoma> listar(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return sintomas.buscarPorConocimientoOrdenados(conocimientoId);
    }

    @Transactional
    public Sintoma crear(Integer conocimientoId, String descripcion, Integer orden) {
        verificarConocimiento(conocimientoId);
        return sintomas.guardar(new Sintoma(null, conocimientoId, descripcion, ordenOValorInicial(orden)));
    }

    @Transactional
    public Sintoma modificar(Integer conocimientoId, Integer sintomaId, String descripcion, Integer orden) {
        verificarConocimiento(conocimientoId);
        Sintoma actual = sintomas.buscarPorId(sintomaId)
                .filter(sintoma -> conocimientoId.equals(sintoma.conocimientoId()))
                .orElseThrow(() -> new SintomaNoEncontradoException(sintomaId));
        return sintomas.guardar(new Sintoma(actual.id(), conocimientoId, descripcion, ordenOValorInicial(orden)));
    }

    private void verificarConocimiento(Integer conocimientoId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
    }

    private int ordenOValorInicial(Integer orden) {
        return orden == null ? 1 : orden;
    }
}
