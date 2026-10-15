package cl.casol.backend.identidad.infrastructure.web.dto;
import cl.casol.backend.identidad.domain.ContactoDepartamentoAdministrativo;
public record ContactoDepartamentoAdminResponse(Integer id,Integer departamentoId,String tipo,String valor,
        boolean activo,boolean departamentoActivo,boolean disponible,Long version) {
    public static ContactoDepartamentoAdminResponse from(ContactoDepartamentoAdministrativo c,boolean padreActivo) {
        return new ContactoDepartamentoAdminResponse(c.id(),c.departamentoId(),c.tipo(),c.valor(),c.activo(),padreActivo,c.activo()&&padreActivo,c.version());
    }
}
