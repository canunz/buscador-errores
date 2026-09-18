package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.ConocimientoPruebaRepository;
import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.application.port.out.PruebaRepository;
import cl.casol.backend.conocimiento.domain.ConocimientoPrueba;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.domain.exception.AsociacionPruebaNoEncontradaException;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.domain.exception.PruebaNoEncontradaException;
import cl.casol.backend.conocimiento.domain.exception.PruebaYaAsociadaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GestionarPruebasConocimientoService {
    private final ConocimientoRepository conocimientos;
    private final PruebaRepository pruebas;
    private final ConocimientoPruebaRepository asociaciones;

    public GestionarPruebasConocimientoService(ConocimientoRepository conocimientos, PruebaRepository pruebas,
            ConocimientoPruebaRepository asociaciones) {
        this.conocimientos = conocimientos;
        this.pruebas = pruebas;
        this.asociaciones = asociaciones;
    }

    public List<Prueba> listarCatalogo() {
        return pruebas.buscarActivasOrdenadasPorDescripcion();
    }

    public List<ConocimientoPrueba> listarPorConocimiento(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return asociaciones.buscarActivasPorConocimientoOrdenadas(conocimientoId);
    }

    @Transactional
    public ConocimientoPrueba asociar(Integer conocimientoId, Integer pruebaId, Integer orden) {
        verificarConocimiento(conocimientoId);
        Prueba prueba = pruebas.buscarActivaPorId(pruebaId)
                .orElseThrow(() -> new PruebaNoEncontradaException(pruebaId));
        if (asociaciones.existe(conocimientoId, pruebaId)) {
            throw new PruebaYaAsociadaException(conocimientoId, pruebaId);
        }
        return asociaciones.guardar(new ConocimientoPrueba(conocimientoId, prueba, ordenOValorInicial(orden)));
    }

    @Transactional
    public ConocimientoPrueba actualizarOrden(Integer conocimientoId, Integer pruebaId, Integer orden) {
        verificarConocimiento(conocimientoId);
        ConocimientoPrueba actual = asociaciones.buscarPorIds(conocimientoId, pruebaId)
                .orElseThrow(() -> new AsociacionPruebaNoEncontradaException(conocimientoId, pruebaId));
        return asociaciones.guardar(new ConocimientoPrueba(conocimientoId, actual.prueba(), orden));
    }

    private void verificarConocimiento(Integer conocimientoId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
    }

    private int ordenOValorInicial(Integer orden) {
        return orden == null ? 1 : orden;
    }
}
