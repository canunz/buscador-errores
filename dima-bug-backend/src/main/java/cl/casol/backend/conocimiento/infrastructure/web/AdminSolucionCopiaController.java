package cl.casol.backend.conocimiento.infrastructure.web;
import cl.casol.backend.conocimiento.application.service.CopiarSolucionService;
import cl.casol.backend.conocimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/conocimientos/{conocimientoId}/soluciones/copias")
public class AdminSolucionCopiaController {
    private final CopiarSolucionService service;
    public AdminSolucionCopiaController(CopiarSolucionService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SolucionResponse copiar(@PathVariable Integer conocimientoId,@Valid @RequestBody CopiarSolucionRequest r) {
        return SolucionResponse.from(service.copiar(r.conocimientoOrigenId(),r.solucionOrigenId(),conocimientoId,
                Boolean.TRUE.equals(r.confirmarAutoservicio())));
    }
}
