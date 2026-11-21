package cl.casol.backend.conocimiento.infrastructure.persistence.repository;

import cl.casol.backend.conocimiento.infrastructure.persistence.entity.PruebaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data de prueba.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface PruebaJpaRepository extends JpaRepository<PruebaEntity, Integer> {
    // Busca registros por activa en true, ordenados por descripción de menor a mayor.
    List<PruebaEntity> findByActivaTrueOrderByDescripcionAsc();
    // Busca registros por id y activa en true.
    Optional<PruebaEntity> findByIdAndActivaTrue(Integer id);
}
