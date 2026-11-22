package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.DepartamentoContacto;

/**
 * Datos que se devuelven al frontend con departamento contacto.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record DepartamentoContactoResponse(Integer id, String tipo, String valor) {
    // Crea el objeto a partir de los datos recibidos.
    public static DepartamentoContactoResponse from(DepartamentoContacto contacto) {
        return new DepartamentoContactoResponse(contacto.id(), contacto.tipo(), contacto.valor());
    }
}
