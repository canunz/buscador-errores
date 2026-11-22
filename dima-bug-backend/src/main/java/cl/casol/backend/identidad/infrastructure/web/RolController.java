package cl.casol.backend.identidad.infrastructure.web;

import cl.casol.backend.identidad.application.service.ListarRolesService;
import cl.casol.backend.identidad.infrastructure.web.dto.RolResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST de rol. Atiende las rutas que empiezan con /api/roles.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final ListarRolesService service;

    public RolController(ListarRolesService service) {
        this.service = service;
    }

    // GET /api/roles: listar activos.
    @GetMapping
    public List<RolResponse> listarActivos() {
        return service.listarActivos().stream().map(RolResponse::from).toList();
    }
}
