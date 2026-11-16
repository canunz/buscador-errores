package cl.casol.backend.tickets.infrastructure.web.dto;

import cl.casol.backend.tickets.domain.NivelEtiqueta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GuardarEtiquetaRequest(
        @NotBlank(message = "El nombre de la etiqueta es obligatorio")
        @Size(max = 40, message = "El nombre no puede superar 40 caracteres") String nombre,
        @NotBlank(message = "El color es obligatorio")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe tener formato hexadecimal, por ejemplo #E53935")
        String color,
        /** Opcional: si no viene, queda NORMAL. */
        NivelEtiqueta nivel) {
}
