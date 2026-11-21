package cl.casol.backend.identidad.api;
import cl.casol.backend.identidad.application.port.out.SucursalRepository;
import cl.casol.backend.identidad.application.port.out.DepartamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Minimal public module projection. No staff, contact, email or remote-access data. */
@Service
@Transactional(readOnly=true)
public class CatalogosSolicitudApi {
    // Devuelve opcion.
    public record Opcion(Integer id, String nombre) {}
    private final SucursalRepository sucursales;
    private final DepartamentoRepository departamentos;
    public CatalogosSolicitudApi(SucursalRepository sucursales, DepartamentoRepository departamentos) {
        this.sucursales=sucursales; this.departamentos=departamentos;
    }
    public List<Opcion> sucursales() {
        return sucursales.listar().stream().filter(s->s.activa())
            .map(s->new Opcion(s.id(),s.nombre())).sorted(Comparator.comparing(Opcion::nombre,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public List<Opcion> departamentos() {
        return departamentos.buscarActivosOrdenadosPorNombre().stream().filter(d->d.activo())
            .map(d->new Opcion(d.id(),d.nombre())).toList();
    }
    // Devuelve sucursal activa.
    public Optional<Opcion> sucursalActiva(Integer id) {
        return id==null ? Optional.empty() : sucursales.buscar(id).filter(s->s.activa()).map(s->new Opcion(s.id(),s.nombre()));
    }
    // Devuelve departamento activo.
    public Optional<Opcion> departamentoActivo(Integer id) {
        return id==null ? Optional.empty() : departamentos.buscarActivoPorId(id).filter(d->d.activo()).map(d->new Opcion(d.id(),d.nombre()));
    }
}
