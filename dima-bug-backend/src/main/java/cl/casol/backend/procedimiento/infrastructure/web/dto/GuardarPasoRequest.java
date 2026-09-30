package cl.casol.backend.procedimiento.infrastructure.web.dto;

import jakarta.validation.constraints.*;

public record GuardarPasoRequest(
        @NotNull(message="El orden es obligatorio") @Positive(message="El orden debe ser mayor que cero") Integer orden,
        @NotBlank(message="La instrucción es obligatoria") String instruccion,
        @NotNull(message="esCritico es obligatorio") Boolean esCritico) { }
