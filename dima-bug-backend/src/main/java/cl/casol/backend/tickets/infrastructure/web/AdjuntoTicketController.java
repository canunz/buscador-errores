package cl.casol.backend.tickets.infrastructure.web;

import cl.casol.backend.tickets.application.port.in.AdjuntarArchivoTicketUseCase;
import cl.casol.backend.tickets.application.port.in.AdjuntarArchivoTicketUseCase.ArchivoAdjunto;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.infrastructure.web.dto.TicketDetalleResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/tickets/{ticketId}/adjuntos")
public class AdjuntoTicketController {
    private final AdjuntarArchivoTicketUseCase adjuntos;

    public AdjuntoTicketController(AdjuntarArchivoTicketUseCase adjuntos) {
        this.adjuntos = adjuntos;
    }

    /** Sube un archivo (imagen, video o PDF). Con comentarioId queda dentro de ese mensaje. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetalleResponse subir(@PathVariable Long ticketId, @RequestParam MultipartFile archivo,
            @RequestParam(required = false) Long comentarioId, Authentication authentication) {
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException ex) {
            throw new TicketInvalidoException("No fue posible recibir el archivo");
        }
        return TicketDetalleResponse.from(adjuntos.adjuntar(ticketId, comentarioId, archivo.getOriginalFilename(),
                contenido, authentication.getName()));
    }

    @GetMapping("/{adjuntoId}")
    public ResponseEntity<byte[]> descargar(@PathVariable Long ticketId, @PathVariable Long adjuntoId,
            Authentication authentication) {
        ArchivoAdjunto archivo = adjuntos.descargar(ticketId, adjuntoId, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.mime()))
                .contentLength(archivo.contenido().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(archivo.contenido());
    }
}
