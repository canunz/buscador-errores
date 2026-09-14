package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Departamento;

public record DepartamentoResponse(Integer id, String nombre) {
    public static DepartamentoResponse from(Departamento departamento) {
        return new DepartamentoResponse(departamento.id(), departamento.nombre());
    }
}
