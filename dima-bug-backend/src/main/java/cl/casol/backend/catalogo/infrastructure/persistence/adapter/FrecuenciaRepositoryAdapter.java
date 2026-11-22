package cl.casol.backend.catalogo.infrastructure.persistence.adapter;

import cl.casol.backend.catalogo.application.port.out.FrecuenciaRepository;
import cl.casol.backend.catalogo.domain.Frecuencia;
import cl.casol.backend.catalogo.infrastructure.persistence.repository.FrecuenciaJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

/**
 * Adaptador de base de datos para frecuencia.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
@Component
public class FrecuenciaRepositoryAdapter implements FrecuenciaRepository {
    private final FrecuenciaJpaRepository repository;

    public FrecuenciaRepositoryAdapter(FrecuenciaJpaRepository repository) {
        this.repository = repository;
    }

    // Busca activa por id.
    @Override
    public Optional<Frecuencia> buscarActivaPorId(Integer id) {
        return repository.findByIdAndActivaTrue(id)
                .map(entity -> new Frecuencia(entity.getId(), entity.getNombre(), entity.isActiva()));
    }

    // Busca activas ordenadas.
    @Override
    public List<Frecuencia> buscarActivasOrdenadas() {
        return repository.findByActivaTrueOrderByOrdenAsc().stream()
                .map(entity -> new Frecuencia(entity.getId(), entity.getNombre(), entity.isActiva()))
                .toList();
    }
}
