package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.out.AdjuntoRepositoryPort;
import cl.casol.backend.tickets.application.port.out.AlmacenAdjuntosPort;
import cl.casol.backend.tickets.application.port.out.CatalogoConsultaPort;
import cl.casol.backend.tickets.application.port.out.ClasificacionTicket;
import cl.casol.backend.tickets.application.port.out.ConocimientoPort;
import cl.casol.backend.tickets.application.port.out.EtiquetaRepositoryPort;
import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.application.port.out.UsuarioConsultaPort;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.RolTicket;
import cl.casol.backend.tickets.domain.Ticket;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Puertos de salida simulados y datos comunes para probar los casos de uso. */
class EscenarioTickets {
    static final String EMAIL_USUARIO = "usuario@dimarsa.cl";
    static final String EMAIL_OTRO = "otro@dimarsa.cl";
    static final String EMAIL_SOPORTE = "soporte@dimarsa.cl";
    static final String EMAIL_ADMIN = "admin@dimarsa.cl";
    static final UsuarioTicket USUARIO = new UsuarioTicket(1, "Ana Usuario", EMAIL_USUARIO, RolTicket.USUARIO, true);
    static final UsuarioTicket OTRO = new UsuarioTicket(2, "Otro Usuario", EMAIL_OTRO, RolTicket.USUARIO, true);
    static final UsuarioTicket SOPORTE = new UsuarioTicket(3, "Tito Soporte", EMAIL_SOPORTE, RolTicket.SOPORTE, true);
    static final UsuarioTicket ADMIN = new UsuarioTicket(4, "Admin", EMAIL_ADMIN, RolTicket.ADMINISTRADOR, true);
    static final UsuarioTicket SOPORTE_2 = new UsuarioTicket(5, "Otra Soporte", "s2@dimarsa.cl", RolTicket.SOPORTE, true);

    final TicketRepositoryPort tickets = mock(TicketRepositoryPort.class);
    final EtiquetaRepositoryPort etiquetas = mock(EtiquetaRepositoryPort.class);
    final UsuarioConsultaPort usuarios = mock(UsuarioConsultaPort.class);
    final CatalogoConsultaPort catalogo = mock(CatalogoConsultaPort.class);
    final ConocimientoPort conocimiento = mock(ConocimientoPort.class);
    final AdjuntoRepositoryPort adjuntos = mock(AdjuntoRepositoryPort.class);
    final AlmacenAdjuntosPort almacen = mock(AlmacenAdjuntosPort.class);
    final ContextoTicketService contexto = new ContextoTicketService(tickets, etiquetas, usuarios, catalogo, conocimiento,
            adjuntos);

    EscenarioTickets() {
        for (UsuarioTicket u : List.of(USUARIO, OTRO, SOPORTE, ADMIN, SOPORTE_2)) {
            when(usuarios.buscarPorEmail(u.email())).thenReturn(Optional.of(u));
            when(usuarios.buscarPorId(u.id())).thenReturn(Optional.of(u));
        }
        when(usuarios.buscarPorIds(any())).thenReturn(Map.of());
        when(catalogo.nombres(any(), any(), any())).thenReturn(ClasificacionTicket.VACIA);
        when(tickets.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Ticket del USUARIO (id 1) en el estado pedido. */
    Ticket ticket(EstadoTicket estado, Integer asignadoId) {
        Ticket ticket = new Ticket(100L, "TK-2026-0001", "Impresora", "No imprime", estado, PrioridadTicket.MEDIA,
                USUARIO.id(), asignadoId, null, null, null, null,
                estado == EstadoTicket.RESUELTO ? "Se cambió el tóner" : null,
                LocalDateTime.now(), LocalDateTime.now(), null, null, Set.of(), List.of(), List.of());
        when(tickets.buscarPorId(100L)).thenReturn(Optional.of(ticket));
        return ticket;
    }
}
