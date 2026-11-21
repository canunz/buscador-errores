package cl.casol.backend.catalogo.infrastructure.persistence.repository;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.FrecuenciaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

/**
 * Repositorio Spring Data de frecuencia.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface FrecuenciaJpaRepository extends JpaRepository<FrecuenciaEntity, Integer> {
    // Busca registros por id y activa en true.
    Optional<FrecuenciaEntity> findByIdAndActivaTrue(Integer id);
    // Busca registros por activa en true, ordenados por orden de menor a mayor.
    List<FrecuenciaEntity> findByActivaTrueOrderByOrdenAsc();
}
