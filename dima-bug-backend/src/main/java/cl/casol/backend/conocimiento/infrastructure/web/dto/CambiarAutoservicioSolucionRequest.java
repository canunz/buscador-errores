package cl.casol.backend.conocimiento.infrastructure.web.dto;
import jakarta.validation.constraints.NotNull;
/**
 * Datos que llegan en la petición para cambiar autoservicio solución.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CambiarAutoservicioSolucionRequest(@NotNull Boolean autoservicio) { }
