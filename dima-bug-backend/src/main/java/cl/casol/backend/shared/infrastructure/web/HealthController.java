package cl.casol.backend.shared.infrastructure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ruta simple para comprobar que el backend está arriba (/api/health).
 * La usan el servidor y el monitoreo; no pide sesión.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    // GET /api/health: health.
    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "status", "OK",
                "application", "DimaBug API"
        );
    }
}