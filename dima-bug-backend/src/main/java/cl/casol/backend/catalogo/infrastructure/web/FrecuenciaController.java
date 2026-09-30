package cl.casol.backend.catalogo.infrastructure.web;

import cl.casol.backend.catalogo.application.service.ConsultarFrecuenciasService;
import cl.casol.backend.catalogo.infrastructure.web.dto.FrecuenciaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/frecuencias")
public class FrecuenciaController {
    private final ConsultarFrecuenciasService service;

    public FrecuenciaController(ConsultarFrecuenciasService service) { this.service = service; }

    @GetMapping
    public List<FrecuenciaResponse> listar() {
        return service.listar().stream().map(FrecuenciaResponse::from).toList();
    }
}
