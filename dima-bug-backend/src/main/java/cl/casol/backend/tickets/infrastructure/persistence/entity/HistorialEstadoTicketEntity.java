package cl.casol.backend.tickets.infrastructure.persistence.entity;

import cl.casol.backend.tickets.domain.EstadoTicket;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_historial_estado")
public class HistorialEstadoTicketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoTicket estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoTicket estadoNuevo;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected HistorialEstadoTicketEntity() { }

    public HistorialEstadoTicketEntity(Long id, Long ticketId, EstadoTicket estadoAnterior, EstadoTicket estadoNuevo,
            Integer usuarioId, String motivo, LocalDateTime fecha) {
        this.id = id; this.ticketId = ticketId; this.estadoAnterior = estadoAnterior; this.estadoNuevo = estadoNuevo;
        this.usuarioId = usuarioId; this.motivo = motivo; this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public EstadoTicket getEstadoAnterior() { return estadoAnterior; }
    public EstadoTicket getEstadoNuevo() { return estadoNuevo; }
    public Integer getUsuarioId() { return usuarioId; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFecha() { return fecha; }
}