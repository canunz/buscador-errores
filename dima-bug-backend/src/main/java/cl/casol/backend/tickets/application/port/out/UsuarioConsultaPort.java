package cl.casol.backend.tickets.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** ¿Existe el usuario? ¿Qué rol tiene? Se resuelve contra la API pública del módulo de identidad. */
public interface UsuarioConsultaPort {
    // Busca por email.
    Optional<UsuarioTicket> buscarPorEmail(String email);
    // Busca por id.
    Optional<UsuarioTicket> buscarPorId(Integer id);
    // Busca por ids.
    Map<Integer, UsuarioTicket> buscarPorIds(Collection<Integer> ids);
}