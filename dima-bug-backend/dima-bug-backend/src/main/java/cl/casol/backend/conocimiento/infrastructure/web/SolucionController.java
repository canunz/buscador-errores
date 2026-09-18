package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerSolucionService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/soluciones")
public class SolucionController {
    private final MantenerSolucionService service;
    public SolucionController(MantenerSolucionService service) { this.service = service; }
    @GetMapping public List<SolucionResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listar(conocimientoId).stream().map(SolucionResponse::from).toList();
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SolucionResponse crear(@PathVariable Integer conocimientoId,
            @Valid @RequestBody GuardarSolucionRequest request) {
        return SolucionResponse.from(service.crear(conocimientoId, request.descripcion(), request.tipo(), request.orden()));
    }
    @PutMapping("/{solucionId}")
    public SolucionResponse modificar(@PathVariable Integer conocimientoId, @PathVariable Integer solucionId,
            @Valid @RequestBody GuardarSolucionRequest request) {
        return SolucionResponse.from(service.modificar(conocimientoId, solucionId,
                request.descripcion(), request.tipo(), request.orden()));
    }
}
