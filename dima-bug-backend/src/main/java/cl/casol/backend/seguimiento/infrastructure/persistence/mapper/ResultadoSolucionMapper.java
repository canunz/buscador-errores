package cl.casol.backend.seguimiento.infrastructure.persistence.mapper;

import cl.casol.backend.seguimiento.domain.ResultadoSolucion;
import cl.casol.backend.seguimiento.infrastructure.persistence.entity.ResultadoSolucionEntity;

/**
 * Convierte los datos de resultado solución entre la base de datos o la API y el dominio.
 */
public final class ResultadoSolucionMapper {
    private ResultadoSolucionMapper() { }
    // Convierte la entidad al modelo del dominio.
    public static ResultadoSolucion toDomain(ResultadoSolucionEntity e){return new ResultadoSolucion(e.getId(),
            e.getSolucionId(),e.getUsuarioId(),e.isFunciono(),e.getComentario(),e.getFecha());}
    // Convierte el modelo del dominio a la entidad de base de datos.
    public static ResultadoSolucionEntity toEntity(ResultadoSolucion r){return new ResultadoSolucionEntity(r.id(),
            r.solucionId(),r.usuarioId(),r.funciono(),r.comentario(),r.fecha());}
}
