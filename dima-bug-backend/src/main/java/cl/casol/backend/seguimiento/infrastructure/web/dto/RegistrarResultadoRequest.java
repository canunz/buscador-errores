package cl.casol.backend.seguimiento.infrastructure.web.dto;

import jakarta.validation.constraints.*;

public record RegistrarResultadoRequest(
        @NotNull(message="funciono es obligatorio") Boolean funciono,
        @Size(max=300,message="El comentario no puede superar 300 caracteres") String comentario) { }
