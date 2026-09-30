package cl.casol.backend.ejecucion.infrastructure.persistence.mapper;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.infrastructure.persistence.entity.*;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
public final class EjecucionMapper {
    private EjecucionMapper(){}
    public static Ejecucion toDomain(EjecucionEntity e){return new Ejecucion(e.getId(),e.getProcedimientoId(),
        UsuarioMapper.toDomain(e.getUsuario()),e.getFechaInicio(),e.getFechaFin(),e.getEstado(),e.getObservaciones());}
    public static EjecucionEntity toEntity(Ejecucion e){return new EjecucionEntity(e.id(),e.procedimientoId(),
        UsuarioMapper.toEntity(e.usuario()),e.fechaInicio(),e.fechaFin(),e.estado(),e.observaciones());}
    public static EjecucionPaso toDomain(EjecucionPasoEntity e){return new EjecucionPaso(e.getId(),e.getEjecucionId(),
        e.getPasoId(),e.isCumplido(),e.getObservacion(),e.getFecha());}
    public static EjecucionPasoEntity toEntity(EjecucionPaso e){return new EjecucionPasoEntity(e.id(),e.ejecucionId(),
        e.pasoId(),e.cumplido(),e.observacion(),e.fecha());}
}
