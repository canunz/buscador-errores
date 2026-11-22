package cl.casol.backend.procedimiento.infrastructure.web;

import cl.casol.backend.procedimiento.application.service.MaterialPasoArchivoService;
import cl.casol.backend.conocimiento.domain.TipoMaterial;
import cl.casol.backend.conocimiento.infrastructure.web.dto.MaterialApoyoResponse;
import cl.casol.backend.shared.infrastructure.web.ArchivoHttp;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controlador REST de material paso archivo. Atiende las rutas que empiezan con /api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales.
 * Solo recibe la petición, valida los datos y llama al caso de uso que corresponde.
 */
@RestController
@RequestMapping("/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales")
public class MaterialPasoArchivoController {
    private final MaterialPasoArchivoService service;
    private final cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria;
    public MaterialPasoArchivoController(MaterialPasoArchivoService service,cl.casol.backend.procedimiento.application.service.AuditoriaProcedimientoService auditoria) { this.service = service;this.auditoria=auditoria; }
    // POST /api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales/archivo: subir.
    @PostMapping(value = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED) @org.springframework.transaction.annotation.Transactional
    public MaterialApoyoResponse subir(@PathVariable Integer procedimientoId, @PathVariable Integer pasoId,
            @RequestParam String nombre, @RequestParam TipoMaterial tipo, @RequestParam MultipartFile archivo,org.springframework.security.core.Authentication a) {
        var m=service.subir(procedimientoId,pasoId,nombre,tipo,ArchivoHttp.convertir(archivo));auditoria.registrar(procedimientoId,"ARCHIVO_AGREGADO",a.getName(),null,m);return MaterialApoyoResponse.fromPaso(m,procedimientoId,pasoId);
    }
    // GET /api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales/{materialId}/archivo: descargar.
    @GetMapping("/{materialId}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer procedimientoId, @PathVariable Integer pasoId,
            @PathVariable Integer materialId) {
        return ArchivoHttp.respuesta(service.descargar(procedimientoId, pasoId, materialId));
    }
}
