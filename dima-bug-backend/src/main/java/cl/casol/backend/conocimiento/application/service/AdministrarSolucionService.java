package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Servicio de administrar solución (módulo de conocimiento).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
@Transactional(readOnly = true)
public class AdministrarSolucionService {
    private final ConocimientoRepository conocimientos;
    private final SolucionRepository soluciones;
    private final IndexarConocimientoService indexador;
    public AdministrarSolucionService(ConocimientoRepository conocimientos, SolucionRepository soluciones,
            IndexarConocimientoService indexador) {
        this.conocimientos = conocimientos; this.soluciones = soluciones; this.indexador = indexador;
    }
    // Lista el conocimiento indicado.
    public List<Solucion> listar(Integer conocimientoId) {
        padre(conocimientoId);
        return soluciones.buscarPorConocimientoOrdenadas(conocimientoId);
    }
    // Cambia estado.
    @Transactional
    public Solucion cambiarEstado(Integer conocimientoId, Integer solucionId, boolean activo) {
        conocimientos.bloquear(conocimientoId);
        Conocimiento c = padre(conocimientoId);
        List<Solucion> actuales = soluciones.bloquearPorConocimiento(conocimientoId);
        Solucion s = perteneciente(actuales, solucionId);
        if (s.activo() == activo) return s;
        if (!activo && c.getEstado() == EstadoConocimiento.PUBLICADO
                && actuales.stream().filter(Solucion::activo).count() <= 1)
            throw new UltimaSolucionActivaException();
        return guardar(s, activo, s.autoservicio());
    }
    // Cambia autoservicio.
    @Transactional
    public Solucion cambiarAutoservicio(Integer conocimientoId, Integer solucionId, boolean autoservicio) {
        conocimientos.bloquear(conocimientoId);
        padre(conocimientoId);
        Solucion s = perteneciente(soluciones.bloquearPorConocimiento(conocimientoId), solucionId);
        if(autoservicio && !s.activo())throw new SolucionInactivaException();
        return s.autoservicio() == autoservicio ? s : guardar(s, s.activo(), autoservicio);
    }
    private Solucion guardar(Solucion s, boolean activo, boolean autoservicio) {
        Solucion guardada = soluciones.guardar(new Solucion(s.id(), s.conocimientoId(), s.descripcion(),
                s.tipo(), s.orden(), s.titulo(), activo, autoservicio));
        indexador.indexar(s.conocimientoId());
        return guardada;
    }
    // Devuelve padre.
    private Conocimiento padre(Integer id) {
        return conocimientos.buscarPorId(id).orElseThrow(() -> new ConocimientoNoEncontradoException(id));
    }
    // Devuelve perteneciente.
    private Solucion perteneciente(List<Solucion> lista, Integer id) {
        return lista.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new SolucionNoEncontradaException(id));
    }
}
