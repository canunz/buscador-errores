package cl.casol.backend.ejecucion.infrastructure.persistence.entity;

import cl.casol.backend.ejecucion.domain.EstadoEjecucion;
import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="se_ejecucion")
public class EjecucionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="ejecucion_id") private Integer id;
    @Column(name="procedimiento_id",nullable=false) private Integer procedimientoId;
    @ManyToOne(fetch=FetchType.EAGER,optional=false)
    @JoinColumn(name="usuario_id",nullable=false) private UsuarioEntity usuario;
    @Column(name="ejecucion_fecha_inicio",nullable=false) private LocalDateTime fechaInicio;
    @Column(name="ejecucion_fecha_fin") private LocalDateTime fechaFin;
    @Enumerated(EnumType.STRING) @Column(name="ejecucion_estado",nullable=false) private EstadoEjecucion estado;
    @Column(name="ejecucion_observaciones") private String observaciones;
    protected EjecucionEntity() { }
    public EjecucionEntity(Integer id,Integer procedimientoId,UsuarioEntity usuario,LocalDateTime fechaInicio,
            LocalDateTime fechaFin,EstadoEjecucion estado,String observaciones){this.id=id;this.procedimientoId=procedimientoId;
        this.usuario=usuario;this.fechaInicio=fechaInicio;this.fechaFin=fechaFin;this.estado=estado;this.observaciones=observaciones;}
    public Integer getId(){return id;} public Integer getProcedimientoId(){return procedimientoId;}
    public UsuarioEntity getUsuario(){return usuario;} public LocalDateTime getFechaInicio(){return fechaInicio;}
    public LocalDateTime getFechaFin(){return fechaFin;} public EstadoEjecucion getEstado(){return estado;}
    public String getObservaciones(){return observaciones;}
}
