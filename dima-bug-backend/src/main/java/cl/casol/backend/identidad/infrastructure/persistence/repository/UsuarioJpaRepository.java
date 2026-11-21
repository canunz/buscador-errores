package cl.casol.backend.identidad.infrastructure.persistence.repository;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

//Antes Entity Paso 4: Habla con JPA. Gracias a eso Spring nos entrega automáticamente métodos
// sin escribir SQL.

public interface UsuarioJpaRepository
        extends JpaRepository<UsuarioEntity, Integer> {

    // Bloquea administradores activos.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioEntity u join fetch u.rol r where u.activo = true and upper(r.nombre) = 'ADMINISTRADOR' order by u.id")
    java.util.List<UsuarioEntity> bloquearAdministradoresActivos();

    // Busca por ids con rol.
    @Query("select u from UsuarioEntity u join fetch u.rol where u.id in :ids")
    List<UsuarioEntity> buscarPorIdsConRol(@Param("ids") Collection<Integer> ids);

    // Busca registros por email.
    Optional<UsuarioEntity> findByEmail(String email);

    // Busca bloqueado.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioEntity u join fetch u.rol where u.id = :id")
    Optional<UsuarioEntity> buscarBloqueado(@Param("id") Integer id);

    // Busca registros por email.
    Optional<UsuarioEntity> findByEmailIgnoreCase(String email);

    @Query(value = """
            SELECT * FROM u_usuario
            WHERE LOWER(usuario_email) = LOWER(:valor)
               OR LOWER(SUBSTRING_INDEX(usuario_email, '@', 1)) = LOWER(:valor)
               OR LOWER(CONCAT(LEFT(TRIM(usuario_nombre), 1),
                        SUBSTRING_INDEX(TRIM(usuario_nombre), ' ', -1))) = LOWER(:valor)
            LIMIT 1
            """, nativeQuery = true)
    // Busca registros por identificador.
    Optional<UsuarioEntity> findByIdentificador(@Param("valor") String valor);

    // Registra ingreso.
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE u_usuario SET usuario_ultimo_ingreso = :fecha WHERE usuario_id = :id", nativeQuery = true)
    int registrarIngreso(@Param("id") Integer id, @Param("fecha") java.time.LocalDateTime fecha);

    // Revisa si existe algún registro por email.
    boolean existsByEmail(String email);

    // Revisa si existe algún registro por email.
    boolean existsByEmailIgnoreCase(String email);

    // Revisa si existe algún registro por email y id distinto de.
    boolean existsByEmailAndIdNot(String email, Integer id);

    // Revisa si existe algún registro por email y id distinto de.
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Integer id);
}
