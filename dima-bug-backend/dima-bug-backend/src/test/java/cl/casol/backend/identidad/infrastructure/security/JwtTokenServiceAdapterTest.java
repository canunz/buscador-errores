package cl.casol.backend.identidad.infrastructure.security;

import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceAdapterTest {

    private static final String SECRET = "test-secret-with-at-least-32-bytes-long";
    private final JwtTokenServiceAdapter tokenService = new JwtTokenServiceAdapter(SECRET, 60_000);

    @Test
    void generaTokenFirmadoConEmailComoSubject() {
        String token = tokenService.generarToken(usuario());

        assertTrue(tokenService.esValido(token));
        assertEquals("user@example.com", tokenService.obtenerEmail(token));

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertEquals(3, claims.size());
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("passwordHash"));
    }

    @Test
    void rechazaTokenManipulado() {
        String token = tokenService.generarToken(usuario());
        char reemplazo = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';
        String manipulado = token.substring(0, token.length() - 1) + reemplazo;

        assertFalse(tokenService.esValido(manipulado));
    }

    @Test
    void rechazaTokenExpirado() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expirado = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        assertFalse(tokenService.esValido(expirado));
    }

    @Test
    void rechazaConfiguracionInsegura() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenServiceAdapter("short", 60_000));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenServiceAdapter(SECRET, 0));
    }

    private Usuario usuario() {
        Rol rol = new Rol(1, "USUARIO", null, true, null, null);
        return new Usuario(1, rol, "Usuario", "user@example.com", "hash-bcrypt",
                true, null, null);
    }
}
