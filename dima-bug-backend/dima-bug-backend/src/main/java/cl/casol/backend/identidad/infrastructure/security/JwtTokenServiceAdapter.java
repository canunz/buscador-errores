package cl.casol.backend.identidad.infrastructure.security;

import cl.casol.backend.identidad.application.port.out.TokenServicePort;
import cl.casol.backend.identidad.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenServiceAdapter implements TokenServicePort {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenServiceAdapter.class);

    private final SecretKey secretKey;
    private final long expiration;

    public JwtTokenServiceAdapter(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET es obligatorio");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 bytes");
        }
        if (expiration <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION debe ser mayor que cero");
        }
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expiration = expiration;
    }

    @Override
    public String generarToken(Usuario usuario) {

        Date ahora = new Date();
        Date expiracion = new Date(
                ahora.getTime() + expiration
        );

        return Jwts.builder()
                .subject(usuario.getEmail())
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public String obtenerEmail(String token) {
        return obtenerClaims(token).getSubject();
    }

    @Override
    public boolean esValido(String token) {
        try {
            Claims claims = obtenerClaims(token);

            return claims.getSubject() != null
                    && !claims.getSubject().isBlank()
                    && claims.getExpiration() != null
                    && claims.getExpiration().after(new Date());

        } catch (ExpiredJwtException exception) {
            log.debug("JWT: validacion rechazada; tipo=ExpiredJwtException");
            return false;
        } catch (SignatureException exception) {
            log.debug("JWT: validacion rechazada; tipo=SignatureException");
            return false;
        } catch (MalformedJwtException exception) {
            log.debug("JWT: validacion rechazada; tipo=MalformedJwtException");
            return false;
        } catch (UnsupportedJwtException exception) {
            log.debug("JWT: validacion rechazada; tipo=UnsupportedJwtException");
            return false;
        } catch (IllegalArgumentException exception) {
            log.debug("JWT: validacion rechazada; tipo=IllegalArgumentException");
            return false;
        } catch (Exception exception) {
            log.debug("JWT: validacion rechazada; tipo={}", exception.getClass().getSimpleName());
            return false;
        }
    }

    private Claims obtenerClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
