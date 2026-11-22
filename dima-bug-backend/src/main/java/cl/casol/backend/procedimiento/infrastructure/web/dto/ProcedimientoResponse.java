package cl.casol.backend.procedimiento.infrastructure.web.dto;

import cl.casol.backend.procedimiento.domain.*;
import java.time.LocalDateTime;

/**
 * Datos que se devuelven al frontend con procedimiento.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ProcedimientoResponse(Integer id,String nombre,String descripcion,EstadoProcedimiento estado,
        UsuarioResumenResponse creadoPor,LocalDateTime fechaCreacion,UsuarioResumenResponse modificadoPor,
        LocalDateTime fechaModificacion,Integer sistemaId,Integer moduloId,String informacionComplementaria,Integer origenId,int revision,boolean vigente,Integer hardwareId) {
    // Crea el objeto a partir de los datos recibidos.
    public static ProcedimientoResponse from(Procedimiento p){return new ProcedimientoResponse(p.id(),p.nombre(),
            p.descripcion(),p.estado(),new UsuarioResumenResponse(p.creadoPor().getId(),p.creadoPor().getNombre()),
            p.fechaCreacion(),p.modificadoPor()==null?null:new UsuarioResumenResponse(p.modificadoPor().getId(),
            p.modificadoPor().getNombre()),p.fechaModificacion(),p.datos().sistemaId(),p.datos().moduloId(),p.datos().informacionComplementaria(),p.datos().origenId(),p.datos().revision(),p.datos().vigente(),p.hardwareId());}
    // Devuelve usuario resumen response.
    public record UsuarioResumenResponse(Integer id,String nombre) { }
}
