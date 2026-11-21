package cl.casol.backend.tickets.infrastructure.persistence.repository;

import cl.casol.backend.tickets.infrastructure.persistence.entity.EtiquetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio Spring Data de etiqueta.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface EtiquetaJpaRepository extends JpaRepository<EtiquetaEntity, Long> {
    // Busca todos, ordenados por nombre de menor a mayor.
    List<EtiquetaEntity> findAllByOrderByNombreAsc();
    // Busca todos los registros por activa en true, ordenados por nombre de menor a mayor.
    List<EtiquetaEntity> findAllByActivaTrueOrderByNombreAsc();
    // Revisa si existe algún registro por nombre.
    boolean existsByNombreIgnoreCase(String nombre);
    // Revisa si existe algún registro por nombre y id distinto de.
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}