package cl.casol.backend.catalogo.infrastructure.web;

import cl.casol.backend.catalogo.application.service.ConsultarSistemasService;
import cl.casol.backend.catalogo.infrastructure.web.dto.HardwareResponse;
import cl.casol.backend.catalogo.infrastructure.web.dto.ModuloResponse;
import cl.casol.backend.catalogo.infrastructure.web.dto.SistemaResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de sistema. Atiende las rutas que empiezan con /api/sistemas.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/sistemas")
public class SistemaController {
    private final ConsultarSistemasService service;

    public SistemaController(ConsultarSistemasService service) { this.service = service; }

    // GET /api/sistemas: listar.
    @GetMapping
    public List<SistemaResponse> listar() {
        return service.listar().stream().map(SistemaResponse::from).toList();
    }

    // GET /api/sistemas/{id}: buscar.
    @GetMapping("/{id}")
    public SistemaResponse buscar(@PathVariable Integer id) { return SistemaResponse.from(service.buscar(id)); }

    // GET /api/sistemas/{id}/modulos: listar módulos.
    @GetMapping("/{id}/modulos")
    public List<ModuloResponse> listarModulos(@PathVariable Integer id) {
        return service.listarModulos(id).stream().map(ModuloResponse::from).toList();
    }

    // GET /api/sistemas/{id}/hardware: listar hardware.
    @GetMapping("/{id}/hardware")
    public List<HardwareResponse> listarHardware(@PathVariable Integer id) {
        return service.listarHardware(id).stream().map(HardwareResponse::from).toList();
    }
}
