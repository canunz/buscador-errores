package cl.casol.backend.conocimiento.domain;
/**
 * Modelo de dominio de material solución. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record MaterialSolucion(Integer id,Integer solucionId,String nombre,TipoMaterial tipo,String url,boolean publicado) {
 public MaterialApoyo archivo(){return new MaterialApoyo(id,null,null,nombre,tipo,url);}
}
