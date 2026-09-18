package cl.casol.backend.conocimiento.infrastructure.web.dto;

import cl.casol.backend.conocimiento.domain.TipoMaterial;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GuardarMaterialApoyoRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres") String nombre,
        @NotNull(message = "El tipo es obligatorio") TipoMaterial tipo,
        @NotBlank(message = "La URL o ruta es obligatoria")
        @Size(max = 500, message = "La URL o ruta no puede superar 500 caracteres") String url) { }
