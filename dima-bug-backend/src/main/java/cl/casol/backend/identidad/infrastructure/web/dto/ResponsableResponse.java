package cl.casol.backend.identidad.infrastructure.web.dto;

import cl.casol.backend.identidad.domain.Responsable;

/**
 * Datos que se devuelven al frontend con responsable.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ResponsableResponse(Integer id, String nombre, String cargo, String contacto) {
    // Crea el objeto a partir de los datos recibidos.
    public static ResponsableResponse from(Responsable responsable) {
        return new ResponsableResponse(responsable.id(), responsable.nombre(), responsable.cargo(), responsable.contacto());
    }
}
