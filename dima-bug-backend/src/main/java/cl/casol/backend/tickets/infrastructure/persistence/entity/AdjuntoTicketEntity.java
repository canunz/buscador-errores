package cl.casol.backend.tickets.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_adjunto")
public class AdjuntoTicketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "comentario_id")
    private Long comentarioId;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "mime", nullable = false, length = 60)
    private String mime;

    @Column(name = "tamano", nullable = false)
    private long tamano;

    @Column(name = "referencia", nullable = false, unique = true, length = 80)
    private String referencia;

    @Column(name = "autor_id", nullable = false)
    private Integer autorId;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected AdjuntoTicketEntity() { }

    public AdjuntoTicketEntity(Long id, Long ticketId, Long comentarioId, String nombre, String mime, long tamano,
            String referencia, Integer autorId, LocalDateTime fecha) {
        this.id = id; this.ticketId = ticketId; this.comentarioId = comentarioId; this.nombre = nombre;
        this.mime = mime; this.tamano = tamano; this.referencia = referencia; this.autorId = autorId;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getComentarioId() { return comentarioId; }
    public String getNombre() { return nombre; }
    public String getMime() { return mime; }
    public long getTamano() { return tamano; }
    public String getReferencia() { return referencia; }
    public Integer getAutorId() { return autorId; }
    public LocalDateTime getFecha() { return fecha; }
}
