package cl.casol.backend.ejecucion.infrastructure.web.dto;
import cl.casol.backend.identidad.domain.Usuario;
/**
 * Datos que se devuelven al frontend con ejecución usuario.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record EjecucionUsuarioResponse(Integer id,String nombre,String email) {
    // Crea el objeto a partir de los datos recibidos.
    public static EjecucionUsuarioResponse from(Usuario u){return new EjecucionUsuarioResponse(u.getId(),u.getNombre(),u.getEmail());}
}
