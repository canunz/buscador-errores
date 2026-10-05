package cl.casol.backend.procedimiento.infrastructure.web;

import cl.casol.backend.procedimiento.application.service.MaterialPasoArchivoService;
import cl.casol.backend.conocimiento.domain.TipoMaterial;
import cl.casol.backend.conocimiento.infrastructure.web.dto.MaterialApoyoResponse;
import cl.casol.backend.shared.infrastructure.web.ArchivoHttp;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/procedimientos/{procedimientoId}/pasos/{pasoId}/materiales")
public class MaterialPasoArchivoController {
    private final MaterialPasoArchivoService service;
    public MaterialPasoArchivoController(MaterialPasoArchivoService service) { this.service = service; }
    @PostMapping(value = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse subir(@PathVariable Integer procedimientoId, @PathVariable Integer pasoId,
            @RequestParam String nombre, @RequestParam TipoMaterial tipo, @RequestParam MultipartFile archivo) {
        return MaterialApoyoResponse.fromPaso(service.subir(procedimientoId, pasoId, nombre, tipo, ArchivoHttp.convertir(archivo)), procedimientoId, pasoId);
    }
    @GetMapping("/{materialId}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer procedimientoId, @PathVariable Integer pasoId,
            @PathVariable Integer materialId) {
        return ArchivoHttp.respuesta(service.descargar(procedimientoId, pasoId, materialId));
    }
}
