package cl.casol.backend.ejecucion.infrastructure.web.dto;
import cl.casol.backend.ejecucion.domain.*;
import java.time.LocalDateTime;
public record EjecucionPasoResponse(Integer ejecucionPasoId,Integer pasoId,Integer orden,String instruccion,
        boolean esCritico,boolean cumplido,String observacion,LocalDateTime fecha,
        EstadoEjecucionPaso estado, long version, Integer contratoVersion,
        cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modoEjecucion,cl.casol.backend.procedimiento.domain.ConfiguracionPaso configuracion) {
    public static EjecucionPasoResponse from(DetalleEjecucionPaso d){EjecucionPaso p=d.ejecucionPaso();
        return new EjecucionPasoResponse(p.id(),p.pasoId(),d.orden(),d.instruccion(),d.esCritico(),
                p.cumplido(),p.observacion(),p.fecha(),p.estado(),p.version(),
                p.contrato()==null?null:p.contrato().version(),
                p.contrato()==null?cl.casol.backend.procedimiento.domain.ModoEjecucionPaso.MANUAL:p.contrato().modo(),p.contrato()==null?null:p.contrato().configuracion());}
}
