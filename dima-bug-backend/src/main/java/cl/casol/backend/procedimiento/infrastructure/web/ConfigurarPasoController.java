package cl.casol.backend.procedimiento.infrastructure.web;
import cl.casol.backend.procedimiento.application.service.ConfigurarPasoService;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.infrastructure.web.dto.PasoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class ConfigurarPasoController {
    private final ConfigurarPasoService service;
    private final cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria;
    public ConfigurarPasoController(ConfigurarPasoService service,cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria){this.service=service;this.auditoria=auditoria;}
    public record Configurar(@NotNull ModoEjecucionPaso modoEjecucion,@NotNull ConfiguracionPaso configuracion,@NotBlank String revisionConfiguracion){}
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/api/procedimientos/{procedimiento}/pasos/{paso}/configuracion")
    public PasoResponse configurar(@PathVariable Integer procedimiento,@PathVariable Integer paso,@Valid @RequestBody Configurar r,Authentication a){var p=service.configurar(procedimiento,paso,r.modoEjecucion(),r.configuracion(),r.revisionConfiguracion(),a.getName());auditoria.registrar(procedimiento,"AUTOMATIZACION_CONFIGURADA",a.getName(),java.util.Map.of("revision",r.revisionConfiguracion()),p);return PasoResponse.from(p);}
}
