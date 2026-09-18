package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerCausaService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.CausaResponse;
import cl.casol.backend.conocimiento.infrastructure.web.dto.GuardarCausaRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/causas")
public class CausaController {
    private final MantenerCausaService service;

    public CausaController(MantenerCausaService service) {
        this.service = service;
    }

    @GetMapping
    public List<CausaResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listar(conocimientoId).stream().map(CausaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CausaResponse crear(@PathVariable Integer conocimientoId,
            @Valid @RequestBody GuardarCausaRequest request) {
        return CausaResponse.from(service.crear(conocimientoId, request.descripcion(), request.orden()));
    }

    @PutMapping("/{causaId}")
    public CausaResponse modificar(@PathVariable Integer conocimientoId, @PathVariable Integer causaId,
            @Valid @RequestBody GuardarCausaRequest request) {
        return CausaResponse.from(service.modificar(conocimientoId, causaId,
                request.descripcion(), request.orden()));
    }
}
