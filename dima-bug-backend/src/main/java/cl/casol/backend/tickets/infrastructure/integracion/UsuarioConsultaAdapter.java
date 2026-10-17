package cl.casol.backend.tickets.infrastructure.integracion;

import cl.casol.backend.identidad.api.IdentidadFachada;
import cl.casol.backend.identidad.api.UsuarioPublico;
import cl.casol.backend.tickets.application.port.out.UsuarioConsultaPort;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.RolTicket;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Adaptador hacia la API pública del módulo de identidad. */
@Component
public class UsuarioConsultaAdapter implements UsuarioConsultaPort {
    private static final Set<String> ROLES_SOPORTE = Set.of("SOPORTE", "SOPORTE TI", "TECNICO", "TÉCNICO", "TI");

    private final IdentidadFachada identidad;

    public UsuarioConsultaAdapter(IdentidadFachada identidad) {
        this.identidad = identidad;
    }

    @Override
    public Optional<UsuarioTicket> buscarPorEmail(String email) {
        return identidad.buscarPorEmail(email).map(UsuarioConsultaAdapter::traducir);
    }

    @Override
    public Optional<UsuarioTicket> buscarPorId(Integer id) {
        return identidad.buscarPorId(id).map(UsuarioConsultaAdapter::traducir);
    }

    @Override
    public Map<Integer, UsuarioTicket> buscarPorIds(Collection<Integer> ids) {
        return identidad.buscarPorIds(ids).stream().map(UsuarioConsultaAdapter::traducir)
                .collect(Collectors.toMap(UsuarioTicket::id, Function.identity(), (a, b) -> a));
    }

    static RolTicket rol(String nombreRol) {
        String rol = nombreRol == null ? "" : nombreRol.trim().toUpperCase(Locale.ROOT);
        if (rol.equals("ADMINISTRADOR")) return RolTicket.ADMINISTRADOR;
        if (ROLES_SOPORTE.contains(rol)) return RolTicket.SOPORTE;
        return RolTicket.USUARIO;
    }

    private static UsuarioTicket traducir(UsuarioPublico usuario) {
        return new UsuarioTicket(usuario.id(), usuario.nombre(), usuario.email(), rol(usuario.rol()), usuario.activo());
    }
}