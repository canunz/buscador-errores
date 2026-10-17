package cl.casol.backend.tickets.application.port.out;

import java.util.Optional;

public interface DetalleTicketConsultaPort {
    Optional<DetalleTicketPlano> buscar(Long id);
}
