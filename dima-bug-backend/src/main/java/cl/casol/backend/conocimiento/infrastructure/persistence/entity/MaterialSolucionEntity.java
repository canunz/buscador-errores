package cl.casol.backend.conocimiento.infrastructure.persistence.entity;
import cl.casol.backend.conocimiento.domain.*;
import jakarta.persistence.*;
@Entity @Table(name="co_material_solucion")
public class MaterialSolucionEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="material_id") private Integer id;
 @Column(name="solucion_id",nullable=false) private Integer solucionId;
 @Column(nullable=false,length=150) private String nombre;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TipoMaterial tipo;
 @Column(nullable=false,length=500) private String url;
 @Column(nullable=false) private boolean publicado;
 protected MaterialSolucionEntity(){}
 public MaterialSolucionEntity(MaterialSolucion m){id=m.id();solucionId=m.solucionId();nombre=m.nombre();tipo=m.tipo();url=m.url();publicado=m.publicado();}
 public MaterialSolucion domain(){return new MaterialSolucion(id,solucionId,nombre,tipo,url,publicado);}
}
