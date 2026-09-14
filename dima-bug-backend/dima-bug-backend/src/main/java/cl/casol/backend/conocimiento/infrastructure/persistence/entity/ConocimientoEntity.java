package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import cl.casol.backend.catalogo.infrastructure.persistence.entity.*;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import cl.casol.backend.identidad.infrastructure.persistence.entity.UsuarioEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "co_conocimiento")
public class ConocimientoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conocimiento_id")
    private Integer id;

    @Column(name = "conocimiento_titulo", nullable = false)
    private String titulo;

    @Column(name = "conocimiento_descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "conocimiento_estado", nullable = false)
    private EstadoConocimiento estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hardware_id")
    private HardwareEntity hardware;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sistema_id")
    private SistemaEntity sistema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modulo_id")
    private ModuloEntity modulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frecuencia_id")
    private FrecuenciaEntity frecuencia;

    @Column(name = "conocimiento_comentario", columnDefinition = "TEXT")
    private String comentario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private UsuarioEntity creadoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modificado_por")
    private UsuarioEntity modificadoPor;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    protected ConocimientoEntity() { }

    public ConocimientoEntity(Integer id, String titulo, String descripcion, EstadoConocimiento estado,
            HardwareEntity hardware, SistemaEntity sistema, ModuloEntity modulo,
            FrecuenciaEntity frecuencia, String comentario, UsuarioEntity creadoPor,
            LocalDateTime fechaCreacion, UsuarioEntity modificadoPor, LocalDateTime fechaModificacion) {
        this.id = id; this.titulo = titulo; this.descripcion = descripcion; this.estado = estado;
        this.hardware = hardware; this.sistema = sistema; this.modulo = modulo;
        this.frecuencia = frecuencia; this.comentario = comentario; this.creadoPor = creadoPor;
        this.fechaCreacion = fechaCreacion; this.modificadoPor = modificadoPor;
        this.fechaModificacion = fechaModificacion;
    }

    public Integer getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public EstadoConocimiento getEstado() { return estado; }
    public HardwareEntity getHardware() { return hardware; }
    public SistemaEntity getSistema() { return sistema; }
    public ModuloEntity getModulo() { return modulo; }
    public FrecuenciaEntity getFrecuencia() { return frecuencia; }
    public String getComentario() { return comentario; }
    public UsuarioEntity getCreadoPor() { return creadoPor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public UsuarioEntity getModificadoPor() { return modificadoPor; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
}
