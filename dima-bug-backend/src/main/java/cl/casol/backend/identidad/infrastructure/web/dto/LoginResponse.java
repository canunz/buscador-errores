package cl.casol.backend.identidad.infrastructure.web.dto;
//Paso: 12
public record LoginResponse(
        String token,
        Integer usuarioId,
        String nombre,
        String email,
        String rol
) {
}