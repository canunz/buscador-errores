package cl.casol.backend.ejecucion.infrastructure.persistence.mapper;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.infrastructure.persistence.entity.*;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
/**
 * Convierte los datos de ejecución entre la base de datos o la API y el dominio.
 */
public final class EjecucionMapper {
    private EjecucionMapper(){}
    // Convierte la entidad al modelo del dominio.
    public static Ejecucion toDomain(EjecucionEntity e){return new Ejecucion(e.getId(),e.getProcedimientoId(),
        UsuarioMapper.toDomain(e.getUsuario()),e.getFechaInicio(),e.getFechaFin(),e.getEstado(),e.getObservaciones());}
    // Convierte el modelo del dominio a la entidad de base de datos.
    public static EjecucionEntity toEntity(Ejecucion e){return new EjecucionEntity(e.id(),e.procedimientoId(),
        UsuarioMapper.toEntity(e.usuario()),e.fechaInicio(),e.fechaFin(),e.estado(),e.observaciones());}
    // Convierte la entidad al modelo del dominio.
    public static EjecucionPaso toDomain(EjecucionPasoEntity e){return new EjecucionPaso(e.getId(),e.getEjecucionId(),
        e.getPasoId(),e.isCumplido(),e.getObservacion(),e.getFecha(),e.getContrato(),e.getEstado(),e.getRevision());}
    // Convierte el modelo del dominio a la entidad de base de datos.
    public static EjecucionPasoEntity toEntity(EjecucionPaso e){return new EjecucionPasoEntity(e.id(),e.ejecucionId(),
        e.pasoId(),e.cumplido(),e.observacion(),e.fecha(),e.contrato(),e.estado(),e.version());}
}
