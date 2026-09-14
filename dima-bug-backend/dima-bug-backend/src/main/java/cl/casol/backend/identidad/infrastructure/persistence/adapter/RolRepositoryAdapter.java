package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.RolRepository;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.infrastructure.persistence.mapper.UsuarioMapper;
import cl.casol.backend.identidad.infrastructure.persistence.repository.RolJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RolRepositoryAdapter implements RolRepository {

    private final RolJpaRepository rolJpaRepository;

    public RolRepositoryAdapter(RolJpaRepository rolJpaRepository) {
        this.rolJpaRepository = rolJpaRepository;
    }

    @Override
    public Optional<Rol> buscarPorId(Integer id) {
        return rolJpaRepository.findById(id).map(UsuarioMapper::rolToDomain);
    }

    @Override
    public List<Rol> buscarActivos() {
        return rolJpaRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(UsuarioMapper::rolToDomain)
                .toList();
    }
}
