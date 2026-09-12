package cl.casol.backend.identidad.application.port.out;

public interface PasswordEncoderPort {

    boolean coincide(String passwordPlano, String passwordHash);
}