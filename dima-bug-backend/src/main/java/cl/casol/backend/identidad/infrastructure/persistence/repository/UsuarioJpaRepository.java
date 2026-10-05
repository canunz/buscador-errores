package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

//Antes Entity Paso 4: Habla con JPA. Gracias a eso Spring nos entrega automáticamente métodos
// sin escribir SQL.

public interface UsuarioJpaRepository
        extends JpaRepository<UsuarioEntity, Integer> {

    Optional<UsuarioEntity> findByEmail(String email);

    Optional<UsuarioEntity> findByEmailIgnoreCase(String email);

    @Query(value = """
            SELECT * FROM u_usuario
            WHERE LOWER(usuario_email) = LOWER(:valor)
               OR LOWER(SUBSTRING_INDEX(usuario_email, '@', 1)) = LOWER(:valor)
               OR LOWER(CONCAT(LEFT(TRIM(usuario_nombre), 1),
                        SUBSTRING_INDEX(TRIM(usuario_nombre), ' ', -1))) = LOWER(:valor)
            LIMIT 1
            """, nativeQuery = true)
    Optional<UsuarioEntity> findByIdentificador(@Param("valor") String valor);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailAndIdNot(String email, Integer id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Integer id);
}
