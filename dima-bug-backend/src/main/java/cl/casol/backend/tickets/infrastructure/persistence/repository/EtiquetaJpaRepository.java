package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.infrastructure.persistence.entity.EtiquetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtiquetaJpaRepository extends JpaRepository<EtiquetaEntity, Long> {
    List<EtiquetaEntity> findAllByOrderByNombreAsc();
    List<EtiquetaEntity> findAllByActivaTrueOrderByNombreAsc();
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}