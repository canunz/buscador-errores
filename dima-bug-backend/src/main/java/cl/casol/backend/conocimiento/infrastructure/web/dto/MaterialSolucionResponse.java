package cl.casol.backend.conocimiento.infrastructure.web.dto;
import cl.casol.backend.conocimiento.domain.*;
public record MaterialSolucionResponse(Integer id,String nombre,TipoMaterial tipo,String url,boolean publicado){
 public static MaterialSolucionResponse from(MaterialSolucion m,boolean publico){String base=publico?"/api/publico/soluciones/":"/api/admin/soluciones/";return new MaterialSolucionResponse(m.id(),m.nombre(),m.tipo(),m.url().startsWith("file:")?base+m.solucionId()+"/materiales/"+m.id()+"/archivo":m.url(),m.publicado());}
}
