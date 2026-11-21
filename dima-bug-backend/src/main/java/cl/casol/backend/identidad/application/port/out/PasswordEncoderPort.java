package cl.casol.backend.identidad.application.port.out;

//CASO DE USO: A) necesitamos abstraer la contraseña. No conviene meter BCryptPasswordEncoder
// directamente en el caso de uso, porque eso acoplaría Application a Spring Security.
public interface PasswordEncoderPort {

    // Indica si coincide.
    boolean coincide(String passwordPlano, String passwordHash);

    // Codifica el password plano.
    String codificar(String passwordPlano);
}
