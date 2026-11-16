package cl.casol.backend.identidad.infrastructure.persistence.entity;

import cl.casol.backend.identidad.domain.Sucursal;
import jakarta.persistence.*;

@Entity @Table(name="u_sucursal",uniqueConstraints=@UniqueConstraint(name="uk_sucursal_codigo",columnNames="sucursal_codigo"))
public class SucursalEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="sucursal_id") private Integer id;
    @Column(name="sucursal_codigo",nullable=false,length=30) private String codigo;
    @Column(name="sucursal_nombre",nullable=false,length=100) private String nombre;
    @Column(name="responsable_nombre",nullable=false,length=100) private String responsableNombre;
    @Column(name="responsable_email",nullable=false,length=120) private String responsableEmail;
    @Column(name="sucursal_activa",nullable=false) private boolean activa;
    @Version @Column(name="revision",nullable=false) private Long version;
    @Column(name="destinatarios_json",columnDefinition="TEXT") private String destinatariosJson;
    @Column(name="ciudad",length=100) private String ciudad;
    @Column(name="responsable_cargo",length=100) private String responsableCargo;
    protected SucursalEntity() { }
    public SucursalEntity(Sucursal s) {
        ciudad=s.ciudad();responsableCargo=s.responsableCargo();
        id=s.id();codigo=s.codigo();nombre=s.nombre();responsableNombre=s.responsableNombre();
        responsableEmail=s.responsableEmail();activa=s.activa();version=s.version();
        destinatariosJson=cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.escribir(s.destinatarios());
    }
    public Sucursal toDomain() { return new Sucursal(id,codigo,nombre,responsableNombre,responsableEmail,activa,version,cl.casol.backend.procedimiento.infrastructure.persistence.mapper.ConfiguracionPasoJson.destinatarios(destinatariosJson),ciudad,responsableCargo); }
}
