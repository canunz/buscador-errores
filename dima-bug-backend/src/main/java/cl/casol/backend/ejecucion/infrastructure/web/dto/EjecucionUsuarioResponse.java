package cl.casol.backend.ejecucion.infrastructure.web.dto;
import cl.casol.backend.identidad.domain.Usuario;
public record EjecucionUsuarioResponse(Integer id,String nombre,String email) {
    public static EjecucionUsuarioResponse from(Usuario u){return new EjecucionUsuarioResponse(u.getId(),u.getNombre(),u.getEmail());}
}
