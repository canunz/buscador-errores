package cl.casol.backend.conocimiento.infrastructure.persistence.repository;
import cl.casol.backend.conocimiento.infrastructure.persistence.entity.MaterialSolucionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MaterialSolucionJpaRepository extends JpaRepository<MaterialSolucionEntity,Integer>{
 List<MaterialSolucionEntity> findBySolucionIdOrderByIdAsc(Integer solucionId);
}
