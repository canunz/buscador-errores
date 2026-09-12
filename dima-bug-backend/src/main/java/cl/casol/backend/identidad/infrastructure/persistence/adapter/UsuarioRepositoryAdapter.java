package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;
import java.util.Optional;

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
}