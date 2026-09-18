package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.TipoSolucion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GuardarSolucionRequest(
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        TipoSolucion tipo,
        @Min(value = 1, message = "orden debe ser mayor o igual a 1") Integer orden) { }
