package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.SolucionAsignacion;

/**
 * Datos que se devuelven al frontend con solución asignación.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record SolucionAsignacionResponse(Integer id, Integer responsableId, String responsableNombre,
        Integer departamentoId, String departamentoNombre, boolean principal) {
    // Crea el objeto a partir de los datos recibidos.
    public static SolucionAsignacionResponse from(SolucionAsignacion a) {
        return new SolucionAsignacionResponse(a.id(), a.responsable() == null ? null : a.responsable().id(),
                a.responsable() == null ? null : a.responsable().nombre(),
                a.departamento() == null ? null : a.departamento().id(),
                a.departamento() == null ? null : a.departamento().nombre(), a.principal());
    }
}
