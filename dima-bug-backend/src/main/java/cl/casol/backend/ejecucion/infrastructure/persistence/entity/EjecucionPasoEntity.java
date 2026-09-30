package cl.casol.backend.ejecucion.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="se_ejecucion_paso",uniqueConstraints=@UniqueConstraint(columnNames={"ejecucion_id","paso_id"}))
public class EjecucionPasoEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="ejecucion_paso_id") private Integer id;
    @Column(name="ejecucion_id",nullable=false) private Integer ejecucionId;
    @Column(name="paso_id",nullable=false) private Integer pasoId;
    @Column(name="ejecucion_paso_cumplido",nullable=false) private boolean cumplido;
    @Column(name="ejecucion_paso_observacion",length=300) private String observacion;
    @Column(name="ejecucion_paso_fecha") private LocalDateTime fecha;
    protected EjecucionPasoEntity() { }
    public EjecucionPasoEntity(Integer id,Integer ejecucionId,Integer pasoId,boolean cumplido,String observacion,
            LocalDateTime fecha){this.id=id;this.ejecucionId=ejecucionId;this.pasoId=pasoId;this.cumplido=cumplido;
        this.observacion=observacion;this.fecha=fecha;}
    public Integer getId(){return id;} public Integer getEjecucionId(){return ejecucionId;}
    public Integer getPasoId(){return pasoId;} public boolean isCumplido(){return cumplido;}
    public String getObservacion(){return observacion;} public LocalDateTime getFecha(){return fecha;}
}
