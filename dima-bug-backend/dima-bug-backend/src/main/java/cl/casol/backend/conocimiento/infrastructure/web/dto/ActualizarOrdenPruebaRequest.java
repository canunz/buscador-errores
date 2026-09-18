package cl.casol.backend.conocimiento.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActualizarOrdenPruebaRequest(
        @NotNull(message = "orden es obligatorio")
        @Min(value = 1, message = "orden debe ser mayor o igual a 1") Integer orden) { }
