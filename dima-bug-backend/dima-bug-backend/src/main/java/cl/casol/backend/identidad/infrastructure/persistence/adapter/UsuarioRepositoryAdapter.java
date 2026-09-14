package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.List;

//Antes Mapper. Paso 6: Implementa y adpater voy a usar Spring Data JPA.

@Component
public class UsuarioRepositoryAdapter implements UsuarioRepository {

    private final UsuarioJpaRepository usuarioJpaRepository;

    public UsuarioRepositoryAdapter(
            UsuarioJpaRepository usuarioJpaRepository
    ) {
        this.usuarioJpaRepository = usuarioJpaRepository;
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {

        return usuarioJpaRepository
                .findByEmail(email)
                .map(UsuarioMapper::toDomain);
    }

    @Override
    public Optional<Usuario> buscarPorId(Integer id) {
        return usuarioJpaRepository.findById(id).map(UsuarioMapper::toDomain);
    }

    @Override
    public List<Usuario> buscarTodos() {
        return usuarioJpaRepository.findAll().stream().map(UsuarioMapper::toDomain).toList();
    }

    @Override
    public boolean existePorEmail(String email) {
        return usuarioJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existePorEmailYIdDistinto(String email, Integer id) {
        return usuarioJpaRepository.existsByEmailAndIdNot(email, id);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        return UsuarioMapper.toDomain(usuarioJpaRepository.save(UsuarioMapper.toEntity(usuario)));
    }
}
