package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.service.ConsultarOrganizacionService;
import cl.casol.backend.identidad.infrastructure.web.dto.DepartamentoResponse;
import cl.casol.backend.identidad.infrastructure.web.dto.DepartamentoContactoResponse;
import cl.casol.backend.identidad.infrastructure.web.dto.ResponsableResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de departamento. Atiende las rutas que empiezan con /api/departamentos.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/departamentos")
public class DepartamentoController {
    private final ConsultarOrganizacionService service;
    public DepartamentoController(ConsultarOrganizacionService service) { this.service = service; }

    // GET /api/departamentos: listar.
    @GetMapping
    public List<DepartamentoResponse> listar() {
        return service.listarDepartamentos().stream().map(DepartamentoResponse::from).toList();
    }

    // GET /api/departamentos/{id}/responsables: listar responsables.
    @GetMapping("/{id}/responsables")
    public List<ResponsableResponse> listarResponsables(@PathVariable Integer id) {
        return service.listarResponsables(id).stream().map(ResponsableResponse::from).toList();
    }

    // GET /api/departamentos/{id}/contactos: listar contactos.
    @GetMapping("/{id}/contactos")
    public List<DepartamentoContactoResponse> listarContactos(@PathVariable Integer id) {
        return service.listarContactos(id).stream().map(DepartamentoContactoResponse::from).toList();
    }
}
