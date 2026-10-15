package cl.casol.backend.catalogo.infrastructure.web.dto;
import cl.casol.backend.catalogo.domain.CatalogoAdministrativo;
import java.util.List;
public record CatalogoAdministrativoResponse(Integer id,String nombre,boolean activo,String sistemaOperativo,
        String descripcion,Integer sistemaId,Integer orden,List<Integer> sistemaIds) {
    public static CatalogoAdministrativoResponse from(CatalogoAdministrativo r) {
        return new CatalogoAdministrativoResponse(r.id(),r.nombre(),r.activo(),r.sistemaOperativo(),r.descripcion(),r.sistemaId(),r.orden(),r.sistemaIds());
    }
}
