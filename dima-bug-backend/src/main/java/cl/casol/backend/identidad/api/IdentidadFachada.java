package cl.casol.backend.identidad.api;

import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** API pública mínima del módulo de identidad para el resto de los módulos. */
@Service
@Transactional(readOnly = true)
public class IdentidadFachada {
    private final UsuarioRepository usuarios;

    public IdentidadFachada(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Optional<UsuarioPublico> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return usuarios.buscarPorEmail(email.trim()).map(IdentidadFachada::publico);
    }

    public Optional<UsuarioPublico> buscarPorId(Integer id) {
        if (id == null) return Optional.empty();
        return usuarios.buscarPorId(id).map(IdentidadFachada::publico);
    }

    public List<UsuarioPublico> buscarPorIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Integer> distintos = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distintos.isEmpty()) return List.of();
        return usuarios.buscarPorIds(distintos).stream().map(IdentidadFachada::publico).toList();
    }

    private static UsuarioPublico publico(Usuario usuario) {
        String rol = usuario.getRol() == null ? null : usuario.getRol().getNombre();
        return new UsuarioPublico(usuario.getId(), usuario.getNombre(), usuario.getEmail(), rol, usuario.isActivo());
    }
}