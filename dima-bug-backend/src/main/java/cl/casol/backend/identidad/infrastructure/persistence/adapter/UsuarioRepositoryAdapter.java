package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
import cl.casol.backend.identidad.infrastructure.security.UsuarioAutenticadoCache;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.List;

//Antes Mapper. Paso 6: Buscar email usando JPA y devuelve un OBJETO DE DOMINO
//Se junta applicacion, domain e infraestrcuture

@Component
public class UsuarioRepositoryAdapter implements UsuarioRepository {

    private final UsuarioJpaRepository usuarioJpaRepository;
    private final UsuarioAutenticadoCache usuariosAutenticados;

    public UsuarioRepositoryAdapter(
            UsuarioJpaRepository usuarioJpaRepository,
            UsuarioAutenticadoCache usuariosAutenticados
    ) {
        this.usuarioJpaRepository = usuarioJpaRepository;
        this.usuariosAutenticados = usuariosAutenticados;
    }

    // Busca fresco.
    @Override
    public Optional<Usuario> buscarFresco(String email) {
        return usuarioJpaRepository.findByEmailIgnoreCase(email).map(UsuarioMapper::toDomain);
    }
    // Busca por email.
    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        String valor = email == null ? "" : email.trim();
        if (valor.isEmpty()) {
            return Optional.empty();
        }

        // Security-sensitive status/origin reads must not reuse the five-minute identity cache.
        return usuarioJpaRepository
                .findByEmailIgnoreCase(valor)
                .or(() -> usuarioJpaRepository.findByEmail(valor))
                .or(() -> valor.contains("@") ? Optional.empty() : usuarioJpaRepository.findByIdentificador(valor))
                .map(UsuarioMapper::toDomain);
    }

    // Busca por id.
    @Override
    public Optional<Usuario> buscarPorId(Integer id) {
        return usuarioJpaRepository.findById(id).map(UsuarioMapper::toDomain);
    }

    // Busca por id bloqueado.
    @Override
    public Optional<Usuario> buscarPorIdBloqueado(Integer id) {
        return usuarioJpaRepository.buscarBloqueado(id).map(UsuarioMapper::toDomain);
    }

    // Busca por ids.
    @Override
    public List<Usuario> buscarPorIds(java.util.Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return usuarioJpaRepository.buscarPorIdsConRol(ids).stream().map(UsuarioMapper::toDomain).toList();
    }

    // Busca todos.
    @Override
    public List<Usuario> buscarTodos() {
        return usuarioJpaRepository.findAll().stream().map(UsuarioMapper::toDomain).toList();
    }

    // Indica si existe por email.
    @Override
    public boolean existePorEmail(String email) {
        String valor = email == null ? "" : email.trim();
        return usuarioJpaRepository.existsByEmailIgnoreCase(valor) || usuarioJpaRepository.existsByEmail(valor);
    }

    // Indica si existe por email y id distinto.
    @Override
    public boolean existePorEmailYIdDistinto(String email, Integer id) {
        String valor = email == null ? "" : email.trim();
        return usuarioJpaRepository.existsByEmailIgnoreCaseAndIdNot(valor, id)
                || usuarioJpaRepository.existsByEmailAndIdNot(valor, id);
    }

    // Bloquea administradores activos.
    @Override
    public List<Usuario> bloquearAdministradoresActivos() {
        return usuarioJpaRepository.bloquearAdministradoresActivos().stream().map(UsuarioMapper::toDomain).toList();
    }

    // Guarda el usuario.
    @Override
    public Usuario guardar(Usuario usuario) {
        Usuario guardado = UsuarioMapper.toDomain(usuarioJpaRepository.save(UsuarioMapper.toEntity(usuario)));
        usuariosAutenticados.invalidar(guardado.getEmail());
        if (usuario.getEmail() != null && !usuario.getEmail().equalsIgnoreCase(guardado.getEmail())) {
            usuariosAutenticados.invalidar(usuario.getEmail());
        }
        return guardado;
    }

    // Registra ingreso.
    @Override
    public void registrarIngreso(Integer id, java.time.LocalDateTime cuando) {
        usuarioJpaRepository.registrarIngreso(id, cuando);
    }
}
