package cl.casol.backend.tickets.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** ¿Existe el usuario? ¿Qué rol tiene? Se resuelve contra la API pública del módulo de identidad. */
public interface UsuarioConsultaPort {
    Optional<UsuarioTicket> buscarPorEmail(String email);
    Optional<UsuarioTicket> buscarPorId(Integer id);
    Map<Integer, UsuarioTicket> buscarPorIds(Collection<Integer> ids);
}