package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.GestionarAsignacionSolucionService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de solución asignación. Atiende las rutas que empiezan con /api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones")
public class SolucionAsignacionController {
    private final GestionarAsignacionSolucionService service;
    public SolucionAsignacionController(GestionarAsignacionSolucionService service) { this.service = service; }
    // GET /api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones: listar.
    @GetMapping public List<SolucionAsignacionResponse> listar(@PathVariable Integer conocimientoId,
            @PathVariable Integer solucionId) {
        return service.listar(conocimientoId, solucionId).stream().map(SolucionAsignacionResponse::from).toList();
    }
    // POST /api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones: crear.
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SolucionAsignacionResponse crear(@PathVariable Integer conocimientoId, @PathVariable Integer solucionId,
            @Valid @RequestBody GuardarSolucionAsignacionRequest request) {
        return SolucionAsignacionResponse.from(service.crear(conocimientoId, solucionId,
                request.departamentoId(), request.responsableId(), request.principal()));
    }
    // PUT /api/conocimientos/{conocimientoId}/soluciones/{solucionId}/asignaciones/{asignacionId}: modificar.
    @PutMapping("/{asignacionId}")
    public SolucionAsignacionResponse modificar(@PathVariable Integer conocimientoId,
            @PathVariable Integer solucionId, @PathVariable Integer asignacionId,
            @Valid @RequestBody GuardarSolucionAsignacionRequest request) {
        return SolucionAsignacionResponse.from(service.modificar(conocimientoId, solucionId, asignacionId,
                request.departamentoId(), request.responsableId(), request.principal()));
    }
}
