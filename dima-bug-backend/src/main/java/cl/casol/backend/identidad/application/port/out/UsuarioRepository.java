package cl.casol.backend.identidad.application.port.out;

//Paso 2: Necesito buscar un usuario, pero no le importa cómo se busca.
//Podría ser MySQL, PostgreSQL, una API externa,

import cl.casol.backend.identidad.domain.Usuario;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

/**
 * Repositorio Spring Data de usuario.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface UsuarioRepository {
    // Busca fresco.
    default java.util.Optional<Usuario> buscarFresco(String email) { return buscarPorEmail(email); }
    // Busca por id bloqueado.
    default java.util.Optional<Usuario> buscarPorIdBloqueado(Integer id) { return buscarPorId(id); }


    // Busca por email.
    Optional<Usuario> buscarPorEmail(String email);

    // Busca por id.
    Optional<Usuario> buscarPorId(Integer id);

    // Busca por ids.
    List<Usuario> buscarPorIds(Collection<Integer> ids);

    // Busca todos.
    List<Usuario> buscarTodos();

    // Indica si existe por email.
    boolean existePorEmail(String email);

    // Indica si existe por email y id distinto.
    boolean existePorEmailYIdDistinto(String email, Integer id);

    // Bloquea administradores activos.
    List<Usuario> bloquearAdministradoresActivos();

    // Guarda el usuario.
    Usuario guardar(Usuario usuario);

    /** Guarda la fecha y hora del último inicio de sesión. */
    void registrarIngreso(Integer id, java.time.LocalDateTime cuando);

}
