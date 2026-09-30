package cl.casol.backend.procedimiento.infrastructure.web.dto;

import jakarta.validation.constraints.*;

public record GuardarProcedimientoRequest(
        @NotBlank(message="El nombre es obligatorio")
        @Size(max=150,message="El nombre no puede superar 150 caracteres") String nombre,
        String descripcion) { }
