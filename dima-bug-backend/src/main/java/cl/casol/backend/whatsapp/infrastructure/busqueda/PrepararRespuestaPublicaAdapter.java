package cl.casol.backend.whatsapp.infrastructure.busqueda;

import cl.casol.backend.conocimiento.application.service.AutoservicioPublicoService;
import cl.casol.backend.conocimiento.domain.exception.SolucionNoEncontradaException;
import cl.casol.backend.whatsapp.application.port.out.PrepararRespuestaPublicaPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal;
import java.util.*;

/** Relee los campos públicos vigentes, sin títulos internos ni materiales. */
public final class PrepararRespuestaPublicaAdapter implements PrepararRespuestaPublicaPort {
    private final AutoservicioPublicoService servicio;
    public PrepararRespuestaPublicaAdapter(AutoservicioPublicoService s){servicio=s;}
    public RespuestaCanal preparar(RespuestaCanal r){
        if(r.estado()!=RespuestaCanal.Estado.RESULTADOS)return r;
        var publicas=new ArrayList<RespuestaCanal.Solucion>();
        for(var original:r.conocimientos())try{
            var s=servicio.publica(original.id());
            publicas.add(new RespuestaCanal.Solucion(s.id(),s.titulo(),s.descripcion()));
        }catch(SolucionNoEncontradaException e){ /* Revocada: se omite. Fallos técnicos se reintentan. */ }
        return new RespuestaCanal(publicas.isEmpty()?RespuestaCanal.Estado.SIN_RESULTADOS:RespuestaCanal.Estado.RESULTADOS,
            publicas.isEmpty()?"No hay soluciones de autoservicio disponibles. Contacta a Soporte.":r.mensaje(),
            null,publicas,List.of(),List.of());
    }
}
