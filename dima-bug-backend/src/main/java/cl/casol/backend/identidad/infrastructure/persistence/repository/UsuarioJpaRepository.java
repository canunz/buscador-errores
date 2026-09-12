package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

//Antes Entity Paso 4: Habla con JPA. Gracias a eso Spring nos entrega automáticamente métodos
// sin escribir SQL.

public interface UsuarioJpaRepository
        extends JpaRepository<UsuarioEntity, Integer> {

    Optional<UsuarioEntity> findByEmail(String email);
}