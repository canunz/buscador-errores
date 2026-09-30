package cl.casol.backend.ejecucion.infrastructure.web.dto;
import cl.casol.backend.ejecucion.domain.*;
import java.time.LocalDateTime;
public record EjecucionPasoResponse(Integer ejecucionPasoId,Integer pasoId,Integer orden,String instruccion,
        boolean esCritico,boolean cumplido,String observacion,LocalDateTime fecha) {
    public static EjecucionPasoResponse from(DetalleEjecucionPaso d){EjecucionPaso p=d.ejecucionPaso();
        return new EjecucionPasoResponse(p.id(),p.pasoId(),d.orden(),d.instruccion(),d.esCritico(),
                p.cumplido(),p.observacion(),p.fecha());}
}
