package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MantenerSintomaService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.GuardarSintomaRequest;
import cl.casol.backend.conocimiento.infrastructure.web.dto.SintomaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/sintomas")
public class SintomaController {
    private final MantenerSintomaService service;

    public SintomaController(MantenerSintomaService service) {
        this.service = service;
    }

    @GetMapping
    public List<SintomaResponse> listar(@PathVariable Integer conocimientoId) {
        return service.listar(conocimientoId).stream().map(SintomaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SintomaResponse crear(@PathVariable Integer conocimientoId,
            @Valid @RequestBody GuardarSintomaRequest request) {
        return SintomaResponse.from(service.crear(conocimientoId, request.descripcion(), request.orden()));
    }

    @PutMapping("/{sintomaId}")
    public SintomaResponse modificar(@PathVariable Integer conocimientoId, @PathVariable Integer sintomaId,
            @Valid @RequestBody GuardarSintomaRequest request) {
        return SintomaResponse.from(
                service.modificar(conocimientoId, sintomaId, request.descripcion(), request.orden()));
    }
}
