package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.SolucionRepository;
import cl.casol.backend.conocimiento.domain.Solucion;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.SolucionMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.SolucionJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class SolucionRepositoryAdapter implements SolucionRepository {
    private final SolucionJpaRepository repository;
    public SolucionRepositoryAdapter(SolucionJpaRepository repository) { this.repository = repository; }
    public List<Solucion> buscarPorConocimientoOrdenadas(Integer id) {
        return repository.findByConocimientoIdOrderByOrdenAsc(id).stream().map(SolucionMapper::toDomain).toList();
    }
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public List<Solucion> buscarActivasPorConocimiento(Integer id) {
        return repository.findByConocimientoIdAndActivoTrueOrderByOrdenAsc(id).stream().map(SolucionMapper::toDomain).toList();
    }
    public List<Solucion> bloquearPorConocimiento(Integer id) {
        return repository.bloquearPorConocimiento(id).stream().map(e -> {
            entityManager.refresh(e, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return SolucionMapper.toDomain(e);
        }).toList();
    }
    public Optional<Solucion> buscarPorId(Integer id) { return repository.findById(id).map(SolucionMapper::toDomain); }
    public Solucion guardar(Solucion solucion) {
        return SolucionMapper.toDomain(repository.save(SolucionMapper.toEntity(solucion)));
    }
}
