package cl.casol.backend.conocimiento.domain;
public record MaterialSolucion(Integer id,Integer solucionId,String nombre,TipoMaterial tipo,String url,boolean publicado) {
 public MaterialApoyo archivo(){return new MaterialApoyo(id,null,null,nombre,tipo,url);}
}
