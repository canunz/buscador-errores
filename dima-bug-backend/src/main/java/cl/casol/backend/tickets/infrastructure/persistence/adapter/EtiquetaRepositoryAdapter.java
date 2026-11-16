package cl.casol.backend.tickets.infrastructure.persistence.adapter;

import cl.casol.backend.tickets.application.port.out.EtiquetaRepositoryPort;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.infrastructure.persistence.mapper.TicketMapper;
import cl.casol.backend.tickets.infrastructure.persistence.repository.EtiquetaJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class EtiquetaRepositoryAdapter implements EtiquetaRepositoryPort {
    private final EtiquetaJpaRepository repository;

    public EtiquetaRepositoryAdapter(EtiquetaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Etiqueta> listar(boolean incluirInactivas) {
        return (incluirInactivas ? repository.findAllByOrderByNombreAsc()
                : repository.findAllByActivaTrueOrderByNombreAsc()).stream().map(TicketMapper::toDomain).toList();
    }

    @Override
    public Optional<Etiqueta> buscarPorId(Long id) {
        return repository.findById(id).map(TicketMapper::toDomain);
    }

    @Override
    public List<Etiqueta> buscarPorIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return repository.findAllById(ids).stream().map(TicketMapper::toDomain).toList();
    }

    @Override
    public boolean existeNombre(String nombre, Long excluirId) {
        return excluirId == null ? repository.existsByNombreIgnoreCase(nombre)
                : repository.existsByNombreIgnoreCaseAndIdNot(nombre, excluirId);
    }

    @Override
    public Etiqueta guardar(Etiqueta etiqueta) {
        return TicketMapper.toDomain(repository.save(TicketMapper.toEntity(etiqueta)));
    }
}