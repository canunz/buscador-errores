package cl.casol.backend.tickets.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import cl.casol.backend.tickets.application.service.CrearSolicitudService;
/** No authenticated user id, role, priority, catalog labels or assignee are client-bindable. */
public record DatosSolicitanteRequest(
        @NotBlank(message="El nombre completo es obligatorio") @Size(max=150) String nombreCompleto,
        @NotNull(message="Selecciona una sucursal") @Positive Integer sucursalId,
        @NotNull(message="Selecciona un área o departamento") @Positive Integer departamentoId,
        @NotBlank(message="El teléfono o anexo es obligatorio") @Size(max=50) String contacto,
        @Size(max=100) String equipo,
        @Size(max=80) String codigoUsuario) {
    public CrearSolicitudService.DatosEntrada entrada() {
        return new CrearSolicitudService.DatosEntrada(nombreCompleto,sucursalId,departamentoId,contacto,equipo,codigoUsuario);
    }
}
