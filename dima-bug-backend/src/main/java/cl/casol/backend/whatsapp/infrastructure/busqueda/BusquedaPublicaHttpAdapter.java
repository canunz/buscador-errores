package cl.casol.backend.whatsapp.infrastructure.busqueda;

import cl.casol.backend.whatsapp.application.port.out.BuscarSolucionesCanalPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal.Solucion;
import java.util.List;

public final class BusquedaPublicaHttpAdapter implements BuscarSolucionesCanalPort {
    private final CatalogoPublicoHttpClient cliente;
    public BusquedaPublicaHttpAdapter(CatalogoPublicoHttpClient cliente){this.cliente=cliente;}
    public List<Solucion> buscar(String consulta){return cliente.buscar(consulta);}
}
