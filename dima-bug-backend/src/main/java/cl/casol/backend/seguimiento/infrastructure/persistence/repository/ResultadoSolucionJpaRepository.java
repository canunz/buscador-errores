package cl.casol.backend.seguimiento.infrastructure.persistence.repository;

import cl.casol.backend.seguimiento.infrastructure.persistence.entity.ResultadoSolucionEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ResultadoSolucionJpaRepository extends JpaRepository<ResultadoSolucionEntity,Integer> {
    List<ResultadoSolucionEntity> findBySolucionIdOrderByFechaDescIdDesc(Integer solucionId);
    @Query(value="""
            SELECT COUNT(*) AS totalAplicaciones,
              COALESCE(SUM(CASE WHEN resultado_funciono = 1 THEN 1 ELSE 0 END), 0) AS totalFunciono,
              COALESCE(SUM(CASE WHEN resultado_funciono = 0 THEN 1 ELSE 0 END), 0) AS totalNoFunciono
            FROM se_resultado WHERE solucion_id = :solucionId
            """,nativeQuery=true)
    EfectividadProjection calcularEfectividad(@Param("solucionId") Integer solucionId);
}
