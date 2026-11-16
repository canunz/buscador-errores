package cl.casol.backend.catalogo.infrastructure.web;

import cl.casol.backend.catalogo.application.service.ConsultarHardwareService;
import cl.casol.backend.catalogo.infrastructure.web.dto.HardwareResponse;
import cl.casol.backend.catalogo.infrastructure.web.dto.SistemaResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/hardware")
public class HardwareController {
    private final ConsultarHardwareService service;

    public HardwareController(ConsultarHardwareService service) { this.service = service; }

    @GetMapping
    public List<HardwareResponse> listar() {
        return service.listar().stream().map(HardwareResponse::from).toList();
    }

    @GetMapping("/{id}")
    public HardwareResponse buscar(@PathVariable Integer id) { return HardwareResponse.from(service.buscar(id)); }

    @GetMapping("/{id}/sistemas")
    public List<SistemaResponse> listarSistemas(@PathVariable Integer id) {
        return service.listarSistemas(id).stream().map(SistemaResponse::from).toList();
    }
}
