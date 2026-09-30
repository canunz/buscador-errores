package cl.casol.backend.identidad.infrastructure.web.dto;

//Paso II: Devolveremos datos mínimos. Jamás devolvemos passwordHash.
//Aunque sea un hash BCrypt, no tiene ningún motivo para salir por la API.

//JWT Paso 4: Agregamos el token
public record LoginResponse(
        String token,
        Integer usuarioId,
        String nombre,
        String email,
        String rol
) {
}