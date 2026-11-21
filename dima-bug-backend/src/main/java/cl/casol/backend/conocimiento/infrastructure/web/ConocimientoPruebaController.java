package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.GestionarPruebasConocimientoService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de conocimiento prueba. Atiende las rutas que empiezan con /api/conocimientos/{conocimientoId}/pruebas.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/pruebas")
public class ConocimientoPruebaController {
    private final GestionarPruebasConocimientoService service;

    public ConocimientoPruebaController(GestionarPruebasConocimientoService service) { this.service = service; }

    // GET /api/conocimientos/{conocimientoId}/pruebas: listar.
    @GetMapping
    public List<ConocimientoPruebaResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listarPorConocimiento(conocimientoId).stream().map(ConocimientoPruebaResponse::from).toList();
    }

    // POST /api/conocimientos/{conocimientoId}/pruebas: asociar.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConocimientoPruebaResponse asociar(@PathVariable Integer conocimientoId,
            @Valid @RequestBody AsociarPruebaRequest request) {
        return ConocimientoPruebaResponse.from(service.asociar(conocimientoId, request.pruebaId(), request.orden()));
    }

    // PUT /api/conocimientos/{conocimientoId}/pruebas/{pruebaId}: actualizar orden.
    @PutMapping("/{pruebaId}")
    public ConocimientoPruebaResponse actualizarOrden(@PathVariable Integer conocimientoId,
            @PathVariable Integer pruebaId, @Valid @RequestBody ActualizarOrdenPruebaRequest request) {
        return ConocimientoPruebaResponse.from(service.actualizarOrden(conocimientoId, pruebaId, request.orden()));
    }
}
