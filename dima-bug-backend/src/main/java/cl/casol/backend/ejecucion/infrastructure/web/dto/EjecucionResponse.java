package cl.casol.backend.ejecucion.infrastructure.web.dto;
import cl.casol.backend.ejecucion.domain.*;
import java.time.LocalDateTime;
public record EjecucionResponse(Integer id,Integer procedimientoId,EstadoEjecucion estado,
        LocalDateTime fechaInicio,LocalDateTime fechaFin,String observaciones,EjecucionUsuarioResponse usuario) {
    public static EjecucionResponse from(Ejecucion e){return new EjecucionResponse(e.id(),e.procedimientoId(),e.estado(),
        e.fechaInicio(),e.fechaFin(),e.observaciones(),EjecucionUsuarioResponse.from(e.usuario()));}
}
