package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.Solucion;
import cl.casol.backend.conocimiento.domain.TipoSolucion;

public record SolucionResponse(Integer id, String descripcion, TipoSolucion tipo, Integer orden) {
    public static SolucionResponse from(Solucion s) {
        return new SolucionResponse(s.id(), s.descripcion(), s.tipo(), s.orden());
    }
}
