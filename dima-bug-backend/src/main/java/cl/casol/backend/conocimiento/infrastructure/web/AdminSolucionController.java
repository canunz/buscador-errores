package cl.casol.backend.conocimiento.infrastructure.web;
import cl.casol.backend.conocimiento.application.service.AdministrarSolucionService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de admin solución. Atiende las rutas que empiezan con /api/admin/conocimientos/{conocimientoId}/soluciones.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/admin/conocimientos/{conocimientoId}/soluciones")
public class AdminSolucionController {
    private final AdministrarSolucionService service;
    public AdminSolucionController(AdministrarSolucionService service) { this.service = service; }
    // GET /api/admin/conocimientos/{conocimientoId}/soluciones: listar.
    @GetMapping
    public List<SolucionResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listar(conocimientoId).stream().map(SolucionResponse::from).toList();
    }
    // PATCH /api/admin/conocimientos/{conocimientoId}/soluciones/{solucionId}/estado: estado.
    @PatchMapping("/{solucionId}/estado")
    public SolucionResponse estado(@PathVariable Integer conocimientoId, @PathVariable Integer solucionId,
            @Valid @RequestBody CambiarEstadoSolucionRequest request) {
        return SolucionResponse.from(service.cambiarEstado(conocimientoId, solucionId, request.activo()));
    }
    // PATCH /api/admin/conocimientos/{conocimientoId}/soluciones/{solucionId}/autoservicio: autoservicio.
    @PatchMapping("/{solucionId}/autoservicio")
    public SolucionResponse autoservicio(@PathVariable Integer conocimientoId, @PathVariable Integer solucionId,
            @Valid @RequestBody CambiarAutoservicioSolucionRequest request) {
        return SolucionResponse.from(service.cambiarAutoservicio(conocimientoId, solucionId, request.autoservicio()));
    }
}
