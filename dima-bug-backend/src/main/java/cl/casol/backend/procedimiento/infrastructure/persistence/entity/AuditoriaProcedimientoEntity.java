package cl.casol.backend.procedimiento.infrastructure.persistence.entity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="pr_auditoria_procedimiento") @org.hibernate.annotations.Immutable
public class AuditoriaProcedimientoEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="procedimiento_id",nullable=false) private Integer procedimientoId;
    @Column(name="actor_id",nullable=false) private Integer actorId;
    @Column(nullable=false) private Instant fecha;
    @Column(nullable=false,length=40) private String accion;
    @Column(name="antes_json",columnDefinition="LONGTEXT") private String antes;
    @Column(name="despues_json",columnDefinition="LONGTEXT") private String despues;
    protected AuditoriaProcedimientoEntity(){}
    public AuditoriaProcedimientoEntity(Integer p,Integer actor,Instant fecha,String accion,String antes,String despues){this.procedimientoId=p;this.actorId=actor;this.fecha=fecha;this.accion=accion;this.antes=antes;this.despues=despues;}
    public Long getId(){return id;}public Integer getProcedimientoId(){return procedimientoId;}public Integer getActorId(){return actorId;}public Instant getFecha(){return fecha;}public String getAccion(){return accion;}public String getAntes(){return antes;}public String getDespues(){return despues;}
}
