package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Departamento;

/**
 * Datos que se devuelven al frontend con departamento.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record DepartamentoResponse(Integer id, String nombre) {
    // Crea el objeto a partir de los datos recibidos.
    public static DepartamentoResponse from(Departamento departamento) {
        return new DepartamentoResponse(departamento.id(), departamento.nombre());
    }
}
