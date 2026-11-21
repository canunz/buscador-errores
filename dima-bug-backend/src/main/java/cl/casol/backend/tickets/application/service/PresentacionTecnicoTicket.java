package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.out.UsuarioConsultaPort;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.Ticket;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/** Saludo automático cuando un técnico toma el ticket (el usuario lo ve en la conversación). */
@Component
class PresentacionTecnicoTicket {
    private final UsuarioConsultaPort usuarios;

    PresentacionTecnicoTicket(UsuarioConsultaPort usuarios) {
        this.usuarios = usuarios;
    }

    void siCambioAsignado(Ticket ticket, Integer anterior, Integer nuevo, LocalDateTime ahora) {
        // Si es el mismo técnico, no repetimos el saludo.
        if (nuevo == null || Objects.equals(anterior, nuevo)) {
            return;
        }
        String nombre = usuarios.buscarPorId(nuevo)
                .map(UsuarioTicket::nombre)
                .map(n -> n == null ? "" : n.strip())
                .filter(n -> !n.isEmpty())
                .orElse("soporte");
        // Solo el primer nombre, para que suene natural: «Hola, soy Tito».
        String corto = nombre.split("\\s+")[0];
        // false = comentario público, el usuario lo lee (no es nota interna).
        ticket.comentar(nuevo,
                "Hola, soy " + corto + ". Yo te ayudaré con tu problema.",
                false,
                ahora);
    }
}
