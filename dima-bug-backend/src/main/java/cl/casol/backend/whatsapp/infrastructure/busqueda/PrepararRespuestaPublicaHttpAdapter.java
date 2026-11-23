package cl.casol.backend.whatsapp.infrastructure.busqueda;

import cl.casol.backend.whatsapp.application.port.out.PrepararRespuestaPublicaPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import java.util.*;

public final class PrepararRespuestaPublicaHttpAdapter implements PrepararRespuestaPublicaPort {
    private final CatalogoPublicoHttpClient cliente;
    public PrepararRespuestaPublicaHttpAdapter(CatalogoPublicoHttpClient cliente){this.cliente=cliente;}
    public RespuestaCanal preparar(RespuestaCanal r){
        if(r.estado()!=RespuestaCanal.Estado.RESULTADOS)return r;
        if(r.conocimientos().isEmpty() || r.conocimientos().stream().anyMatch(s -> !cliente.catalogo().equals(s.catalogo())))
            return ProteccionCatalogo.consultaNueva(r);
        var publicas=new ArrayList<RespuestaCanal.Solucion>();
        for(var s:r.conocimientos())cliente.publica(s.id()).ifPresent(publicas::add);
        return new RespuestaCanal(publicas.isEmpty()?RespuestaCanal.Estado.SIN_RESULTADOS:RespuestaCanal.Estado.RESULTADOS,
                publicas.isEmpty()?"No hay soluciones de autoservicio disponibles. Contacta a Soporte.":r.mensaje(),
                null,publicas,List.of(),List.of(),r.destinatario());
    }
}
