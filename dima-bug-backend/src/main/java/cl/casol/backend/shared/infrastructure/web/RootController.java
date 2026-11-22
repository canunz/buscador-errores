package cl.casol.backend.shared.infrastructure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador REST de root.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
public class RootController {

    // GET /: root.
    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "status", "OK",
                "application", "DimaBug API",
                "health", "/api/health"
        );
    }
}
