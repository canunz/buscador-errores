package cl.casol.backend.procedimiento.infrastructure.web.dto;

import cl.casol.backend.procedimiento.domain.Paso;

public record PasoResponse(Integer id,Integer procedimientoId,Integer orden,String instruccion,boolean esCritico){
    public static PasoResponse from(Paso p){return new PasoResponse(p.id(),p.procedimientoId(),p.orden(),p.instruccion(),p.esCritico());}
}
