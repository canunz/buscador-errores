package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.PrioridadTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en la petición para crear ticket.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record CrearTicketRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede superar 150 caracteres") String titulo,
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        PrioridadTicket prioridad,
        Integer hardwareId,
        Integer sistemaId,
        Integer moduloId) {
}