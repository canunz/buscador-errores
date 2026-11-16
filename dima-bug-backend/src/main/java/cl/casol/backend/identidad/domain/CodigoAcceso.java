package cl.casol.backend.identidad.domain;

import java.time.LocalDateTime;

/**
 * Código de acceso de un solo uso que se envía al correo del usuario para iniciar sesión.
 * Solo se guarda el hash del código, nunca el código en texto plano.
 */
public record CodigoAcceso(
        Long id,
        Integer usuarioId,
        String codigoHash,
        LocalDateTime creadoEn,
        LocalDateTime expiraEn,
        int intentos,
        boolean usado
) {

    public boolean expirado(LocalDateTime ahora) {
        return !ahora.isBefore(expiraEn);
    }

    public boolean disponible(LocalDateTime ahora, int maxIntentos) {
        return !usado && !expirado(ahora) && intentos < maxIntentos;
    }

    public CodigoAcceso conIntentoFallido() {
        return new CodigoAcceso(id, usuarioId, codigoHash, creadoEn, expiraEn, intentos + 1, usado);
    }

    public CodigoAcceso marcarUsado() {
        return new CodigoAcceso(id, usuarioId, codigoHash, creadoEn, expiraEn, intentos, true);
    }
}
