package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

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
    public List<Solucion> listar(Integer conocimientoId) {
        padre(conocimientoId);
        return soluciones.buscarPorConocimientoOrdenadas(conocimientoId);
    }
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
    @Transactional
    public Solucion cambiarAutoservicio(Integer conocimientoId, Integer solucionId, boolean autoservicio) {
        conocimientos.bloquear(conocimientoId);
        padre(conocimientoId);
        Solucion s = perteneciente(soluciones.bloquearPorConocimiento(conocimientoId), solucionId);
        return s.autoservicio() == autoservicio ? s : guardar(s, s.activo(), autoservicio);
    }
    private Solucion guardar(Solucion s, boolean activo, boolean autoservicio) {
        Solucion guardada = soluciones.guardar(new Solucion(s.id(), s.conocimientoId(), s.descripcion(),
                s.tipo(), s.orden(), s.titulo(), activo, autoservicio));
        indexador.indexar(s.conocimientoId());
        return guardada;
    }
    private Conocimiento padre(Integer id) {
        return conocimientos.buscarPorId(id).orElseThrow(() -> new ConocimientoNoEncontradoException(id));
    }
    private Solucion perteneciente(List<Solucion> lista, Integer id) {
        return lista.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new SolucionNoEncontradaException(id));
    }
}
