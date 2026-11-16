package cl.casol.backend.conocimiento.infrastructure.web.dto;

import jakarta.validation.constraints.Min;

public record GuardarSolucionAsignacionRequest(
        @Min(value = 1, message = "departamentoId debe ser mayor que 0") Integer departamentoId,
        @Min(value = 1, message = "responsableId debe ser mayor que 0") Integer responsableId,
        Boolean principal) { }
