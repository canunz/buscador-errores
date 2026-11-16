package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.application.port.out.SolucionRepository;
import cl.casol.backend.conocimiento.domain.Solucion;
import cl.casol.backend.conocimiento.domain.TipoSolucion;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.domain.exception.SolucionNoEncontradaException;
import cl.casol.backend.conocimiento.domain.exception.SolucionInactivaException;
import cl.casol.backend.conocimiento.domain.exception.TituloSolucionInvalidoException;
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
        return soluciones.buscarActivasPorConocimiento(conocimientoId);
    }

    @Transactional
    public Solucion crear(Integer conocimientoId, String descripcion, TipoSolucion tipo, Integer orden, String titulo) {
        conocimientos.bloquear(conocimientoId);
        verificarConocimiento(conocimientoId);
        validarTitulo(titulo);
        var actuales = soluciones.bloquearPorConocimiento(conocimientoId);
        cl.casol.backend.conocimiento.domain.IdentidadSolucion.exigirNueva(actuales,
                titulo, descripcion, tipo == null ? TipoSolucion.PASOS : tipo);
        Solucion guardada = soluciones.guardar(new Solucion(null, conocimientoId, descripcion,
                tipo == null ? TipoSolucion.PASOS : tipo, ordenDisponible(actuales, orden), titulo.strip(), true, false));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    @Transactional
    public Solucion modificar(Integer conocimientoId, Integer solucionId, String descripcion,
            TipoSolucion tipo, Integer orden, String titulo) {
        conocimientos.bloquear(conocimientoId);
        verificarConocimiento(conocimientoId);
        soluciones.bloquearPorConocimiento(conocimientoId);
        Solucion actual = buscarPerteneciente(conocimientoId, solucionId);
        if (!actual.activo()) throw new SolucionInactivaException();
        validarTitulo(titulo);
        Solucion guardada = soluciones.guardar(new Solucion(actual.id(), conocimientoId, descripcion,
                tipo == null ? TipoSolucion.PASOS : tipo, orden == null ? 1 : orden, titulo.strip(), actual.activo(), actual.autoservicio()));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    private Integer ordenDisponible(List<Solucion> actuales, Integer solicitado) {
        int propuesto = solicitado == null ? 1 : solicitado;
        if (actuales.stream().noneMatch(s -> s.orden() == propuesto)) return propuesto;
        int maximo = actuales.stream().mapToInt(Solucion::orden).max().orElse(0);
        if (maximo == Integer.MAX_VALUE) throw new cl.casol.backend.conocimiento.domain.exception.CopiaSolucionInvalidaException("No hay un orden disponible para otra solución");
        return maximo + 1;
    }

    private void validarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank() || titulo.length() > 150) throw new TituloSolucionInvalidoException();
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
