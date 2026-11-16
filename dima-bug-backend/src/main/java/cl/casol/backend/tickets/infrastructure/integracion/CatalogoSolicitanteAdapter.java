package cl.casol.backend.tickets.infrastructure.integracion;
import cl.casol.backend.identidad.api.CatalogosSolicitudApi;
import cl.casol.backend.tickets.application.port.out.CatalogoSolicitantePort;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class CatalogoSolicitanteAdapter implements CatalogoSolicitantePort {
    private final CatalogosSolicitudApi api;
    public CatalogoSolicitanteAdapter(CatalogosSolicitudApi api) { this.api=api; }
    private static Opcion opcion(CatalogosSolicitudApi.Opcion v) {return new Opcion(v.id(),v.nombre());}
    public List<Opcion> sucursales() {return api.sucursales().stream().map(CatalogoSolicitanteAdapter::opcion).toList();}
    public List<Opcion> departamentos() {return api.departamentos().stream().map(CatalogoSolicitanteAdapter::opcion).toList();}
    public Optional<Opcion> sucursalActiva(Integer id) {return api.sucursalActiva(id).map(CatalogoSolicitanteAdapter::opcion);}
    public Optional<Opcion> departamentoActivo(Integer id) {return api.departamentoActivo(id).map(CatalogoSolicitanteAdapter::opcion);}
}
