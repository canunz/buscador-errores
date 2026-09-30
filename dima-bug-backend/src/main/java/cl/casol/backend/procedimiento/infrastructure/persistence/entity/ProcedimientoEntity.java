package cl.casol.backend.procedimiento.infrastructure.persistence.entity;

import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import cl.casol.backend.procedimiento.domain.EstadoProcedimiento;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pr_procedimiento")
public class ProcedimientoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "procedimiento_id") private Integer id;
    @Column(name = "procedimiento_nombre", nullable = false, length = 150) private String nombre;
    @Column(name = "procedimiento_descripcion", columnDefinition = "TEXT") private String descripcion;
    @Enumerated(EnumType.STRING)
    @Column(name = "procedimiento_estado", nullable = false) private EstadoProcedimiento estado;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false) private UsuarioEntity creadoPor;
    @Column(name = "fecha_creacion", nullable = false) private LocalDateTime fechaCreacion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modificado_por") private UsuarioEntity modificadoPor;
    @Column(name = "fecha_modificacion") private LocalDateTime fechaModificacion;

    protected ProcedimientoEntity() { }
    public ProcedimientoEntity(Integer id, String nombre, String descripcion, EstadoProcedimiento estado,
            UsuarioEntity creadoPor, LocalDateTime fechaCreacion, UsuarioEntity modificadoPor,
            LocalDateTime fechaModificacion) {
        this.id=id; this.nombre=nombre; this.descripcion=descripcion; this.estado=estado;
        this.creadoPor=creadoPor; this.fechaCreacion=fechaCreacion; this.modificadoPor=modificadoPor;
        this.fechaModificacion=fechaModificacion;
    }
    public Integer getId(){return id;} public String getNombre(){return nombre;}
    public String getDescripcion(){return descripcion;} public EstadoProcedimiento getEstado(){return estado;}
    public UsuarioEntity getCreadoPor(){return creadoPor;} public LocalDateTime getFechaCreacion(){return fechaCreacion;}
    public UsuarioEntity getModificadoPor(){return modificadoPor;} public LocalDateTime getFechaModificacion(){return fechaModificacion;}
}
