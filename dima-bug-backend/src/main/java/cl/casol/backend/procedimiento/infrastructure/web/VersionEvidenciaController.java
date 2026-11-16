package cl.casol.backend.procedimiento.infrastructure.web;
import cl.casol.backend.procedimiento.application.service.PrepararVersionEvidenciaService;
import cl.casol.backend.procedimiento.infrastructure.web.dto.ProcedimientoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class VersionEvidenciaController {
    private final PrepararVersionEvidenciaService service;
    public VersionEvidenciaController(PrepararVersionEvidenciaService service){this.service=service;}
    @PostMapping("/api/procedimientos/{id}/version-evidencia") @ResponseStatus(HttpStatus.CREATED)
    public ProcedimientoResponse crear(@PathVariable Integer id,Authentication a){return ProcedimientoResponse.from(service.crear(id,a.getName()));}
}
