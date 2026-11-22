package cl.casol.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de inicio del backend. Arranca Spring Boot y carga todos los módulos.
 */
@SpringBootApplication
public class BackendApplication {

    // Punto de inicio de la aplicación.
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
