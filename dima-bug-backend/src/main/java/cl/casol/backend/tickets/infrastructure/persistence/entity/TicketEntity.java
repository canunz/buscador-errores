package cl.casol.backend.tickets.infrastructure.persistence.entity;

import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Las referencias a otros módulos (usuarios, catálogos, conocimiento) se guardan solo como id. */
@Entity
@Table(name = "ticket")
public class TicketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoTicket estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", nullable = false, length = 10)
    private PrioridadTicket prioridad;

    @Column(name = "solicitante_id", nullable = false)
    private Integer solicitanteId;

    @Column(name = "asignado_id")
    private Integer asignadoId;

    @Column(name = "hardware_id")
    private Integer hardwareId;

    @Column(name = "sistema_id")
    private Integer sistemaId;

    @Column(name = "modulo_id")
    private Integer moduloId;

    @Column(name = "conocimiento_id")
    private Integer conocimientoId;

    @Column(name = "solucion", columnDefinition = "TEXT")
    private String solucion;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 32)
    @CollectionTable(name = "ticket_ticket_etiqueta", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "etiqueta_id", nullable = false)
    private Set<Long> etiquetaIds = new LinkedHashSet<>();

    protected TicketEntity() { }

    public TicketEntity(Long id, String codigo, String titulo, String descripcion, EstadoTicket estado,
            PrioridadTicket prioridad, Integer solicitanteId, Integer asignadoId, Integer hardwareId,
            Integer sistemaId, Integer moduloId, Integer conocimientoId, String solucion,
            LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion, LocalDateTime fechaResolucion,
            LocalDateTime fechaCierre, Set<Long> etiquetaIds) {
        this.id = id; this.codigo = codigo; this.titulo = titulo; this.descripcion = descripcion;
        this.estado = estado; this.prioridad = prioridad; this.solicitanteId = solicitanteId;
        this.asignadoId = asignadoId; this.hardwareId = hardwareId; this.sistemaId = sistemaId;
        this.moduloId = moduloId; this.conocimientoId = conocimientoId; this.solucion = solucion;
        this.fechaCreacion = fechaCreacion; this.fechaActualizacion = fechaActualizacion;
        this.fechaResolucion = fechaResolucion; this.fechaCierre = fechaCierre;
        this.etiquetaIds = etiquetaIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(etiquetaIds);
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public EstadoTicket getEstado() { return estado; }
    public PrioridadTicket getPrioridad() { return prioridad; }
    public Integer getSolicitanteId() { return solicitanteId; }
    public Integer getAsignadoId() { return asignadoId; }
    public Integer getHardwareId() { return hardwareId; }
    public Integer getSistemaId() { return sistemaId; }
    public Integer getModuloId() { return moduloId; }
    public Integer getConocimientoId() { return conocimientoId; }
    public String getSolucion() { return solucion; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public Set<Long> getEtiquetaIds() { return etiquetaIds; }
}