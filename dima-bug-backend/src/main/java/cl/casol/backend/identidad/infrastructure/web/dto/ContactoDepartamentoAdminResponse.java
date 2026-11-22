package cl.casol.backend.identidad.infrastructure.web.dto;
import cl.casol.backend.identidad.domain.ContactoDepartamentoAdministrativo;
/**
 * Datos que se devuelven al frontend con contacto departamento admin.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record ContactoDepartamentoAdminResponse(Integer id,Integer departamentoId,String tipo,String valor,
        boolean activo,boolean departamentoActivo,boolean disponible,Long version) {
    // Crea el objeto a partir de los datos recibidos.
    public static ContactoDepartamentoAdminResponse from(ContactoDepartamentoAdministrativo c,boolean padreActivo) {
        return new ContactoDepartamentoAdminResponse(c.id(),c.departamentoId(),c.tipo(),c.valor(),c.activo(),padreActivo,c.activo()&&padreActivo,c.version());
    }
}
