package cl.casol.backend.seguimiento.infrastructure.persistence.mapper;

import cl.casol.backend.seguimiento.domain.ResultadoSolucion;
import cl.casol.backend.seguimiento.infrastructure.persistence.entity.ResultadoSolucionEntity;

public final class ResultadoSolucionMapper {
    private ResultadoSolucionMapper() { }
    public static ResultadoSolucion toDomain(ResultadoSolucionEntity e){return new ResultadoSolucion(e.getId(),
            e.getSolucionId(),e.getUsuarioId(),e.isFunciono(),e.getComentario(),e.getFecha());}
    public static ResultadoSolucionEntity toEntity(ResultadoSolucion r){return new ResultadoSolucionEntity(r.id(),
            r.solucionId(),r.usuarioId(),r.funciono(),r.comentario(),r.fecha());}
}
