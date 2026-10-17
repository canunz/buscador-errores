package cl.casol.backend.tickets.infrastructure.web;

import cl.casol.backend.tickets.application.port.in.AsignarTicketUseCase;
import cl.casol.backend.tickets.application.port.in.CambiarEstadoTicketUseCase;
import cl.casol.backend.tickets.application.port.in.ComentarTicketUseCase;
import cl.casol.backend.tickets.application.port.in.ConsultarTicketsUseCase;
import cl.casol.backend.tickets.application.port.in.CrearTicketUseCase;
import cl.casol.backend.tickets.application.port.in.EtiquetarTicketUseCase;
import cl.casol.backend.tickets.application.port.in.ResolverTicketUseCase;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.FiltroTickets;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.infrastructure.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final CrearTicketUseCase crear;
    private final ConsultarTicketsUseCase consultar;
    private final AsignarTicketUseCase asignar;
    private final CambiarEstadoTicketUseCase cambiarEstado;
    private final ResolverTicketUseCase resolver;
    private final ComentarTicketUseCase comentar;
    private final EtiquetarTicketUseCase etiquetar;

    public TicketController(CrearTicketUseCase crear, ConsultarTicketsUseCase consultar, AsignarTicketUseCase asignar,
            CambiarEstadoTicketUseCase cambiarEstado, ResolverTicketUseCase resolver, ComentarTicketUseCase comentar,
            EtiquetarTicketUseCase etiquetar) {
        this.crear = crear;
        this.consultar = consultar;
        this.asignar = asignar;
        this.cambiarEstado = cambiarEstado;
        this.resolver = resolver;
        this.comentar = comentar;
        this.etiquetar = etiquetar;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetalleResponse crear(@Valid @RequestBody CrearTicketRequest request, Authentication authentication) {
        return TicketDetalleResponse.from(crear.crear(new CrearTicketUseCase.Comando(request.titulo(),
                request.descripcion(), request.prioridad(), request.hardwareId(), request.sistemaId(),
                request.moduloId()), authentication.getName()));
    }

    @GetMapping
    public PaginaResponse<TicketResumenResponse> listar(
            @RequestParam(required = false) EstadoTicket estado,
            @RequestParam(required = false) PrioridadTicket prioridad,
            @RequestParam(required = false) Long etiquetaId,
            @RequestParam(required = false) Integer asignadoId,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        FiltroTickets filtro = new FiltroTickets(estado, prioridad, etiquetaId, asignadoId, null, texto);
        return PaginaResponse.from(consultar.listar(filtro, page, size, authentication.getName()),
                TicketResumenResponse::from);
    }

    /** Contadores de la bandeja en una sola llamada (antes eran ocho). */
    @GetMapping("/conteo")
    public ConteoTicketsResponse conteo(Authentication authentication) {
        return ConteoTicketsResponse.from(consultar.conteo(authentication.getName()));
    }

    @GetMapping("/sugerencias")
    public List<ConocimientoSugeridoResponse> sugerencias(@RequestParam(required = false) String texto) {
        return consultar.sugerencias(texto).stream().map(ConocimientoSugeridoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TicketDetalleResponse detalle(@PathVariable Long id, Authentication authentication) {
        return TicketDetalleResponse.from(consultar.detalle(id, authentication.getName()));
    }

    @PatchMapping("/{id}/asignacion")
    public TicketDetalleResponse asignar(@PathVariable Long id, @Valid @RequestBody AsignarTicketRequest request,
            Authentication authentication) {
        return TicketDetalleResponse.from(asignar.asignar(id, request.asignadoId(), authentication.getName()));
    }

    @PatchMapping("/{id}/estado")
    public TicketDetalleResponse cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody CambiarEstadoTicketRequest request, Authentication authentication) {
        return TicketDetalleResponse.from(cambiarEstado.cambiarEstado(id, request.estado(), request.motivo(),
                authentication.getName()));
    }

    @PatchMapping("/{id}/resolucion")
    public TicketDetalleResponse resolver(@PathVariable Long id, @Valid @RequestBody ResolverTicketRequest request,
            Authentication authentication) {
        return TicketDetalleResponse.from(resolver.resolver(id, request.solucion(), request.conocimientoId(),
                authentication.getName()));
    }

    @PostMapping("/{id}/conocimiento")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetalleResponse crearConocimiento(@PathVariable Long id, Authentication authentication) {
        return TicketDetalleResponse.from(resolver.crearConocimientoDesdeTicket(id, authentication.getName()));
    }

    @PostMapping("/{id}/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetalleResponse comentar(@PathVariable Long id, @Valid @RequestBody ComentarTicketRequest request,
            Authentication authentication) {
        return TicketDetalleResponse.from(comentar.comentar(id, request.texto(), request.esInterno(),
                authentication.getName()));
    }

    @PutMapping("/{id}/etiquetas")
    public TicketDetalleResponse etiquetar(@PathVariable Long id, @Valid @RequestBody EtiquetarTicketRequest request,
            Authentication authentication) {
        return TicketDetalleResponse.from(etiquetar.etiquetar(id, new LinkedHashSet<>(request.etiquetaIds()),
                authentication.getName()));
    }
}
