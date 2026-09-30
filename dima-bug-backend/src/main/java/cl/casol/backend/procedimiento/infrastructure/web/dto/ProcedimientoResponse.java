package cl.casol.backend.procedimiento.infrastructure.web.dto;

import cl.casol.backend.procedimiento.domain.*;
import java.time.LocalDateTime;

public record ProcedimientoResponse(Integer id,String nombre,String descripcion,EstadoProcedimiento estado,
        UsuarioResumenResponse creadoPor,LocalDateTime fechaCreacion,UsuarioResumenResponse modificadoPor,
        LocalDateTime fechaModificacion) {
    public static ProcedimientoResponse from(Procedimiento p){return new ProcedimientoResponse(p.id(),p.nombre(),
            p.descripcion(),p.estado(),new UsuarioResumenResponse(p.creadoPor().getId(),p.creadoPor().getNombre()),
            p.fechaCreacion(),p.modificadoPor()==null?null:new UsuarioResumenResponse(p.modificadoPor().getId(),
            p.modificadoPor().getNombre()),p.fechaModificacion());}
    public record UsuarioResumenResponse(Integer id,String nombre) { }
}
