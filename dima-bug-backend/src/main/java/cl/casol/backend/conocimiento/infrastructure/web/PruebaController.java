package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.GestionarPruebasConocimientoService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.PruebaResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pruebas")
public class PruebaController {
    private final GestionarPruebasConocimientoService service;

    public PruebaController(GestionarPruebasConocimientoService service) { this.service = service; }

    @GetMapping
    public List<PruebaResponse> listar() {
        return service.listarCatalogo().stream().map(PruebaResponse::from).toList();
    }
}
