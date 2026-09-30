package cl.casol.backend.identidad.application.port.out;

//CASO DE USO: A) necesitamos abstraer la contraseña. No conviene meter BCryptPasswordEncoder
// directamente en el caso de uso, porque eso acoplaría Application a Spring Security.
public interface PasswordEncoderPort {

    boolean coincide(String passwordPlano, String passwordHash);

    String codificar(String passwordPlano);
}
