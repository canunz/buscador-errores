package cl.casol.backend.conocimiento.infrastructure.web;

import cl.casol.backend.conocimiento.application.service.MaterialConocimientoArchivoService;
import cl.casol.backend.conocimiento.domain.TipoMaterial;
import cl.casol.backend.conocimiento.infrastructure.web.dto.MaterialApoyoResponse;
import cl.casol.backend.shared.infrastructure.web.ArchivoHttp;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/conocimientos/{conocimientoId}/materiales")
public class MaterialConocimientoArchivoController {
    private final MaterialConocimientoArchivoService service;
    public MaterialConocimientoArchivoController(MaterialConocimientoArchivoService service) { this.service = service; }
    @PostMapping(value = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialApoyoResponse subir(@PathVariable Integer conocimientoId, @RequestParam String nombre,
            @RequestParam TipoMaterial tipo, @RequestParam MultipartFile archivo) {
        return MaterialApoyoResponse.from(service.subir(conocimientoId, nombre, tipo, ArchivoHttp.convertir(archivo)));
    }
    @GetMapping("/{materialId}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer conocimientoId, @PathVariable Integer materialId) {
        return ArchivoHttp.respuesta(service.descargar(conocimientoId, materialId));
    }
}
