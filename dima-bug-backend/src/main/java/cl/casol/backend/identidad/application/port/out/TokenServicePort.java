package cl.casol.backend.identidad.application.port.out;

import cl.casol.backend.identidad.domain.Usuario;
//JWT Paso 1: Antes descargar Dependencia/librería desde Maven (pom.xml)
//Necesito generar un token
//Filtro JWT Paso 1: ¿Es válido?
public interface TokenServicePort {

    String generarToken(Usuario usuario);

    String obtenerEmail(String token);

    boolean esValido(String token);
}