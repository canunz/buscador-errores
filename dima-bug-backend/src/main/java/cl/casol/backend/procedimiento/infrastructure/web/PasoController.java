package cl.casol.backend.procedimiento.infrastructure.web;

import cl.casol.backend.procedimiento.application.service.MantenerPasoService;
import cl.casol.backend.procedimiento.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Controlador REST de paso. Atiende las rutas que empiezan con /api/procedimientos/{procedimientoId}/pasos.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/procedimientos/{procedimientoId}/pasos")
public class PasoController {
    private final MantenerPasoService service;
    private final cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria;
    public PasoController(MantenerPasoService service,cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria){this.service=service;this.auditoria=auditoria;}
    public enum Direccion { ARRIBA, ABAJO }
    // Mueve la dirección.
    public record Mover(@jakarta.validation.constraints.NotNull Direccion direccion,@jakarta.validation.constraints.NotEmpty java.util.List<Integer> ordenEsperado){}
    // PATCH /api/procedimientos/{procedimientoId}/pasos/{pasoId}/orden: mover.
    @PatchMapping("/{pasoId}/orden") @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<PasoResponse> mover(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@Valid @RequestBody Mover r,org.springframework.security.core.Authentication a){var antes=service.listar(procedimientoId);var despues=service.mover(procedimientoId,pasoId,r.direccion()==Direccion.ARRIBA,r.ordenEsperado());auditoria.registrar(procedimientoId,"PASOS_REORDENADOS",a.getName(),antes,despues);return despues.stream().map(PasoResponse::from).toList();}
    // DELETE /api/procedimientos/{procedimientoId}/pasos/{pasoId}: eliminar.
    @DeleteMapping("/{pasoId}") @ResponseStatus(HttpStatus.NO_CONTENT) @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void eliminar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,org.springframework.security.core.Authentication a){var antes=service.listar(procedimientoId).stream().filter(p->p.id().equals(pasoId)).findFirst().orElse(null);service.eliminar(procedimientoId,pasoId);auditoria.registrar(procedimientoId,"PASO_ELIMINADO",a.getName(),antes,null);}
    // GET /api/procedimientos/{procedimientoId}/pasos: listar.
    @GetMapping public List<PasoResponse> listar(@PathVariable Integer procedimientoId){return service.listar(procedimientoId).stream().map(PasoResponse::from).toList();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PasoResponse crear(@PathVariable Integer procedimientoId,@Valid @RequestBody GuardarPasoRequest r,org.springframework.security.core.Authentication a){var p=r.modoEjecucion()==null?service.crear(procedimientoId,r.orden(),r.instruccion(),r.esCritico()):service.crear(procedimientoId,r.orden(),r.instruccion(),r.esCritico(),r.modoEjecucion(),r.mensajeEvidencia(),r.configuracion());auditoria.registrar(procedimientoId,"PASO_CREADO",a.getName(),null,p);return PasoResponse.from(p);}
    // PUT /api/procedimientos/{procedimientoId}/pasos/{pasoId}: modificar.
    @PutMapping("/{pasoId}") @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PasoResponse modificar(@PathVariable Integer procedimientoId,@PathVariable Integer pasoId,@Valid @RequestBody GuardarPasoRequest r,org.springframework.security.core.Authentication a){var antes=service.listar(procedimientoId).stream().filter(p->p.id().equals(pasoId)).findFirst().orElse(null);var p=r.modoEjecucion()==null?service.modificar(procedimientoId,pasoId,r.orden(),r.instruccion(),r.esCritico()):service.modificar(procedimientoId,pasoId,r.orden(),r.instruccion(),r.esCritico(),r.modoEjecucion(),r.mensajeEvidencia(),r.configuracion());auditoria.registrar(procedimientoId,"PASO_MODIFICADO",a.getName(),antes,p);return PasoResponse.from(p);}
}
