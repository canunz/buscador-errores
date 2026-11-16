package cl.casol.backend.tickets.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarTicketRequest(
        @NotBlank(message = "El comentario no puede estar vacío")
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres") String texto,
        Boolean interno) {

    public boolean esInterno() {
        return Boolean.TRUE.equals(interno);
    }
}