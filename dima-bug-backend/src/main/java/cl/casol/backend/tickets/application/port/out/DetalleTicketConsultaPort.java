package cl.casol.backend.tickets.application.port.out;

import java.util.Optional;

/**
 * Puerto de salida de detalle ticket consulta.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface DetalleTicketConsultaPort {
    // Busca por id.
    Optional<DetalleTicketPlano> buscar(Long id);
}
