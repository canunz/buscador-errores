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

/**
 * Servicio de gestionar pruebas conocimiento (módulo de conocimiento).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
@Transactional(readOnly = true)
public class GestionarPruebasConocimientoService {
    private final ConocimientoRepository conocimientos;
    private final PruebaRepository pruebas;
    private final ConocimientoPruebaRepository asociaciones;
    private final IndexarConocimientoService indexador;

    public GestionarPruebasConocimientoService(ConocimientoRepository conocimientos, PruebaRepository pruebas,
            ConocimientoPruebaRepository asociaciones, IndexarConocimientoService indexador) {
        this.conocimientos = conocimientos;
        this.pruebas = pruebas;
        this.asociaciones = asociaciones;
        this.indexador = indexador;
    }

    // Lista catálogo.
    public List<Prueba> listarCatalogo() {
        return pruebas.buscarActivasOrdenadasPorDescripcion();
    }

    // Lista por conocimiento.
    public List<ConocimientoPrueba> listarPorConocimiento(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return asociaciones.buscarPorConocimientoOrdenadas(conocimientoId);
    }

    // Asocia el conocimiento indicado.
    @Transactional
    public ConocimientoPrueba asociar(Integer conocimientoId, Integer pruebaId, Integer orden) {
        verificarConocimiento(conocimientoId);
        pruebas.bloquear(pruebaId);
        Prueba prueba = pruebas.buscarActivaPorId(pruebaId)
                .orElseThrow(() -> new PruebaNoEncontradaException(pruebaId));
        if (asociaciones.existe(conocimientoId, pruebaId)) {
            throw new PruebaYaAsociadaException(conocimientoId, pruebaId);
        }
        ConocimientoPrueba guardada = asociaciones.guardar(
                new ConocimientoPrueba(conocimientoId, prueba, ordenOValorInicial(orden)));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    // Actualiza orden.
    @Transactional
    public ConocimientoPrueba actualizarOrden(Integer conocimientoId, Integer pruebaId, Integer orden) {
        verificarConocimiento(conocimientoId);
        ConocimientoPrueba actual = asociaciones.buscarPorIds(conocimientoId, pruebaId)
                .orElseThrow(() -> new AsociacionPruebaNoEncontradaException(conocimientoId, pruebaId));
        ConocimientoPrueba guardada = asociaciones.guardar(
                new ConocimientoPrueba(conocimientoId, actual.prueba(), orden));
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
