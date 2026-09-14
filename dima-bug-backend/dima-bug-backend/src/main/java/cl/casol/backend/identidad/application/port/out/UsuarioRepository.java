package cl.casol.backend.identidad.application.port.out;

//Paso 2: “Necesito buscar un usuario”, pero no le importa cómo se busca.
//Podría ser MySQL, PostgreSQL, una API externa,

import cl.casol.backend.identidad.domain.Usuario;
import java.util.Optional;
import java.util.List;

public interface UsuarioRepository {

    Optional<Usuario> buscarPorEmail(String email);

    Optional<Usuario> buscarPorId(Integer id);

    List<Usuario> buscarTodos();

    boolean existePorEmail(String email);

    boolean existePorEmailYIdDistinto(String email, Integer id);

    Usuario guardar(Usuario usuario);

}
