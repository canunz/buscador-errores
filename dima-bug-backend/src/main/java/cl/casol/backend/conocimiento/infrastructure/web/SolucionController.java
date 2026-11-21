package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerSolucionService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de solución. Atiende las rutas que empiezan con /api/conocimientos/{conocimientoId}/soluciones.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/soluciones")
public class SolucionController {
    private final MantenerSolucionService service;
    public SolucionController(MantenerSolucionService service) { this.service = service; }
    // GET /api/conocimientos/{conocimientoId}/soluciones: listar.
    @GetMapping public List<SolucionResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listar(conocimientoId).stream().map(SolucionResponse::from).toList();
    }
    // POST /api/conocimientos/{conocimientoId}/soluciones: crear.
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SolucionResponse crear(@PathVariable Integer conocimientoId,
            @Valid @RequestBody GuardarSolucionRequest request) {
        return SolucionResponse.from(service.crear(conocimientoId, request.descripcion(), request.tipo(), request.orden(), request.titulo()));
    }
    // PUT /api/conocimientos/{conocimientoId}/soluciones/{solucionId}: modificar.
    @PutMapping("/{solucionId}")
    public SolucionResponse modificar(@PathVariable Integer conocimientoId, @PathVariable Integer solucionId,
            @Valid @RequestBody GuardarSolucionRequest request) {
        return SolucionResponse.from(service.modificar(conocimientoId, solucionId,
                request.descripcion(), request.tipo(), request.orden(), request.titulo()));
    }
}
