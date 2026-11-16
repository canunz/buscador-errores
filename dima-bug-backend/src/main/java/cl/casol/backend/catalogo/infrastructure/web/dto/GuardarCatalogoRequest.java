package cl.casol.backend.catalogo.infrastructure.web.dto;
import cl.casol.backend.catalogo.domain.CatalogoAdministrativo;
import jakarta.validation.constraints.*;
import java.util.List;
public record GuardarCatalogoRequest(@NotBlank @Size(max=120) String nombre,
        @NotNull Boolean activo, @Size(max=150) String sistemaOperativo,
        @Size(max=255) String descripcion, @Positive Integer sistemaId,
        @Positive Integer orden, List<@NotNull @Positive Integer> sistemaIds) {
    public CatalogoAdministrativo toDomain() { return new CatalogoAdministrativo(null,nombre,activo,sistemaOperativo,descripcion,sistemaId,orden,sistemaIds); }
}
