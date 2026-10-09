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

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        String valor = email == null ? "" : email.trim();
        if (valor.isEmpty()) {
            return Optional.empty();
        }

        return usuarioJpaRepository
                .findByEmailIgnoreCase(valor)
                .or(() -> usuarioJpaRepository.findByEmail(valor))
                .or(() -> valor.contains("@") ? Optional.empty() : usuarioJpaRepository.findByIdentificador(valor))
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
        String valor = email == null ? "" : email.trim();
        return usuarioJpaRepository.existsByEmailIgnoreCase(valor) || usuarioJpaRepository.existsByEmail(valor);
    }

    @Override
    public boolean existePorEmailYIdDistinto(String email, Integer id) {
        String valor = email == null ? "" : email.trim();
        return usuarioJpaRepository.existsByEmailIgnoreCaseAndIdNot(valor, id)
                || usuarioJpaRepository.existsByEmailAndIdNot(valor, id);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        Usuario guardado = UsuarioMapper.toDomain(usuarioJpaRepository.save(UsuarioMapper.toEntity(usuario)));
        usuariosAutenticados.invalidar(guardado.getEmail());
        if (usuario.getEmail() != null && !usuario.getEmail().equalsIgnoreCase(guardado.getEmail())) {
            usuariosAutenticados.invalidar(usuario.getEmail());
        }
        return guardado;
    }
}
