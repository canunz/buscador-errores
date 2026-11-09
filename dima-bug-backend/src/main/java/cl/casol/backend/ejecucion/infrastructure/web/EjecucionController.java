package cl.casol.backend.ejecucion.infrastructure.web;
import cl.casol.backend.ejecucion.application.service.GestionarEjecucionService;
import cl.casol.backend.ejecucion.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class EjecucionController {
    private final GestionarEjecucionService service;
    public EjecucionController(GestionarEjecucionService service){this.service=service;}
    public record Inicio(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Integer sucursalId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="[0-9a-fA-F-]{36}") String claveInicio,
            java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatariosAdicionales) { }
    @PostMapping("/api/procedimientos/{procedimientoId}/ejecuciones") @ResponseStatus(HttpStatus.CREATED)
    public EjecucionResponse iniciar(@PathVariable Integer procedimientoId,@Valid @RequestBody(required=false) Inicio r,Authentication a){
        return EjecucionResponse.from(r==null?service.iniciar(procedimientoId,a.getName()):r.destinatariosAdicionales()==null?service.iniciar(procedimientoId,r.sucursalId(),r.claveInicio(),a.getName()):service.iniciar(procedimientoId,r.sucursalId(),r.claveInicio(),r.destinatariosAdicionales(),a.getName()));}
    public record Siguiente(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long versionEsperada,@jakarta.validation.constraints.NotNull Boolean confirmacionCritica,@jakarta.validation.constraints.Size(max=300) String observacion){}
    @PostMapping("/api/ejecuciones/{id}/pasos/{paso}/siguiente") public EjecucionPasoResponse siguiente(@PathVariable Integer id,@PathVariable Integer paso,@Valid @RequestBody Siguiente r,Authentication a){return EjecucionPasoResponse.from(service.siguiente(id,paso,r.versionEsperada(),r.confirmacionCritica(),r.observacion(),a.getName()));}
    @GetMapping("/api/ejecuciones/{id}")
    public EjecucionResponse buscar(@PathVariable Integer id,Authentication a){return EjecucionResponse.from(service.buscar(id,a.getName()));}
    @GetMapping("/api/ejecuciones")
    public List<EjecucionResponse> listar(Authentication a){return service.listar(a.getName()).stream().map(EjecucionResponse::from).toList();}
    @GetMapping("/api/ejecuciones/{id}/pasos")
    public List<EjecucionPasoResponse> pasos(@PathVariable Integer id,Authentication a){return service.listarPasos(id,a.getName()).stream().map(EjecucionPasoResponse::from).toList();}
    @PatchMapping("/api/ejecuciones/{id}/pasos/{ejecucionPasoId}")
    public EjecucionPasoResponse actualizarPaso(@PathVariable Integer id,@PathVariable Integer ejecucionPasoId,
            @Valid @RequestBody ActualizarEjecucionPasoRequest r,Authentication a){
        return EjecucionPasoResponse.from(r.versionEsperada()==null
                ? service.actualizarPaso(id,ejecucionPasoId,r.cumplido(),r.observacion(),a.getName())
                : service.actualizarPaso(id,ejecucionPasoId,r.cumplido(),r.observacion(),r.versionEsperada(),a.getName()));
    }
    @PatchMapping("/api/ejecuciones/{id}/completar")
    public EjecucionResponse completar(@PathVariable Integer id,Authentication a){return EjecucionResponse.from(service.completar(id,a.getName()));}
    @PatchMapping("/api/ejecuciones/{id}/cancelar")
    public EjecucionResponse cancelar(@PathVariable Integer id,@RequestBody(required=false) CancelarEjecucionRequest r,Authentication a){
        return EjecucionResponse.from(service.cancelar(id,r==null?null:r.observaciones(),a.getName()));}
}
