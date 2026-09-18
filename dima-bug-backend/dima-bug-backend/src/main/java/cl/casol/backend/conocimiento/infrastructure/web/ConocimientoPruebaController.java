package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.GestionarPruebasConocimientoService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/pruebas")
public class ConocimientoPruebaController {
    private final GestionarPruebasConocimientoService service;

    public ConocimientoPruebaController(GestionarPruebasConocimientoService service) { this.service = service; }

    @GetMapping
    public List<ConocimientoPruebaResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listarPorConocimiento(conocimientoId).stream().map(ConocimientoPruebaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConocimientoPruebaResponse asociar(@PathVariable Integer conocimientoId,
            @Valid @RequestBody AsociarPruebaRequest request) {
        return ConocimientoPruebaResponse.from(service.asociar(conocimientoId, request.pruebaId(), request.orden()));
    }

    @PutMapping("/{pruebaId}")
    public ConocimientoPruebaResponse actualizarOrden(@PathVariable Integer conocimientoId,
            @PathVariable Integer pruebaId, @Valid @RequestBody ActualizarOrdenPruebaRequest request) {
        return ConocimientoPruebaResponse.from(service.actualizarOrden(conocimientoId, pruebaId, request.orden()));
    }
}
