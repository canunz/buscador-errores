package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en la petición para comentar ticket.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record ComentarTicketRequest(
        @NotBlank(message = "El comentario no puede estar vacío")
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres") String texto,
        Boolean interno) {

    // Indica si es interno.
    public boolean esInterno() {
        return Boolean.TRUE.equals(interno);
    }
}