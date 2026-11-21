package cl.casol.backend.conocimiento.infrastructure.persistence.adapter;

import cl.casol.backend.conocimiento.application.port.out.SolucionRepository;
import cl.casol.backend.conocimiento.domain.Solucion;
import cl.casol.backend.conocimiento.infrastructure.persistence.mapper.SolucionMapper;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.SolucionJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de base de datos para solución.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class SolucionRepositoryAdapter implements SolucionRepository {
    // Busca activas autoservicio por conocimiento.
    public List<Solucion> buscarActivasAutoservicioPorConocimiento(Integer id) {
        return repository.findByConocimientoIdAndActivoTrueAndAutoservicioTrueOrderByOrdenAsc(id)
                .stream().map(SolucionMapper::toDomain).toList();
    }
    // Busca publicas.
    public List<Solucion> buscarPublicas(String texto,int limite){return repository.publicas(texto,org.springframework.data.domain.PageRequest.of(0,limite)).stream().map(SolucionMapper::toDomain).toList();}
    private final SolucionJpaRepository repository;
    public SolucionRepositoryAdapter(SolucionJpaRepository repository) { this.repository = repository; }
    // Busca por conocimiento ordenadas.
    public List<Solucion> buscarPorConocimientoOrdenadas(Integer id) {
        return repository.findByConocimientoIdOrderByOrdenAsc(id).stream().map(SolucionMapper::toDomain).toList();
    }
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    // Busca activas por conocimiento.
    public List<Solucion> buscarActivasPorConocimiento(Integer id) {
        return repository.findByConocimientoIdAndActivoTrueOrderByOrdenAsc(id).stream().map(SolucionMapper::toDomain).toList();
    }
    // Bloquea por conocimiento.
    public List<Solucion> bloquearPorConocimiento(Integer id) {
        return repository.bloquearPorConocimiento(id).stream().map(e -> {
            entityManager.refresh(e, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return SolucionMapper.toDomain(e);
        }).toList();
    }
    // Busca por id.
    public Optional<Solucion> buscarPorId(Integer id) { return repository.findById(id).map(SolucionMapper::toDomain); }
    // Guarda la solución.
    public Solucion guardar(Solucion solucion) {
        return SolucionMapper.toDomain(repository.save(SolucionMapper.toEntity(solucion)));
    }
}
