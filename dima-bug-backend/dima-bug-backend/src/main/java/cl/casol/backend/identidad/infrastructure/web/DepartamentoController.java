package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.service.ConsultarOrganizacionService;
import cl.casol.backend.identidad.infrastructure.web.dto.DepartamentoResponse;
import cl.casol.backend.identidad.infrastructure.web.dto.ResponsableResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/departamentos")
public class DepartamentoController {
    private final ConsultarOrganizacionService service;
    public DepartamentoController(ConsultarOrganizacionService service) { this.service = service; }

    @GetMapping
    public List<DepartamentoResponse> listar() {
        return service.listarDepartamentos().stream().map(DepartamentoResponse::from).toList();
    }

    @GetMapping("/{id}/responsables")
    public List<ResponsableResponse> listarResponsables(@PathVariable Integer id) {
        return service.listarResponsables(id).stream().map(ResponsableResponse::from).toList();
    }
}
