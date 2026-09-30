package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.DepartamentoContacto;

public record DepartamentoContactoResponse(Integer id, String tipo, String valor) {
    public static DepartamentoContactoResponse from(DepartamentoContacto contacto) {
        return new DepartamentoContactoResponse(contacto.id(), contacto.tipo(), contacto.valor());
    }
}
