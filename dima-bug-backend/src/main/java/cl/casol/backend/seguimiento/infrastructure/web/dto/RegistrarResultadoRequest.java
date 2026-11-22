package cl.casol.backend.seguimiento.infrastructure.web.dto;

import jakarta.validation.constraints.*;

/**
 * Datos que llegan en la petición para registrar resultado.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record RegistrarResultadoRequest(
        @NotNull(message="funciono es obligatorio") Boolean funciono,
        @Size(max=300,message="El comentario no puede superar 300 caracteres") String comentario) { }
