package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.PruebaRepository;
import cl.casol.backend.conocimiento.domain.Prueba;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.PruebaMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.PruebaJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para prueba.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class PruebaRepositoryAdapter implements PruebaRepository {
    // Bloquea por id.
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    public void bloquear(Integer id) { var p=em.find(cl.casol.backend.conocimiento.infrastructure.persistence.entity.PruebaEntity.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); if(p!=null) em.refresh(p,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
    private final PruebaJpaRepository repository;

    public PruebaRepositoryAdapter(PruebaJpaRepository repository) { this.repository = repository; }

    // Busca activas ordenadas por descripción.
    @Override
    public List<Prueba> buscarActivasOrdenadasPorDescripcion() {
        return repository.findByActivaTrueOrderByDescripcionAsc().stream().map(PruebaMapper::toDomain).toList();
    }

    // Busca activa por id.
    @Override
    public Optional<Prueba> buscarActivaPorId(Integer id) {
        return repository.findByIdAndActivaTrue(id).map(PruebaMapper::toDomain);
    }
}
