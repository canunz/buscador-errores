package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Usuario;

public interface TokenServicePort {

    String generarToken(Usuario usuario);

    String obtenerEmail(String token);

    boolean esValido(String token);
}