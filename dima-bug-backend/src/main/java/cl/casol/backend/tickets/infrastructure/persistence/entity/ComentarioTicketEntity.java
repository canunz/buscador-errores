package cl.casol.backend.tickets.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_comentario")
public class ComentarioTicketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "autor_id", nullable = false)
    private Integer autorId;

    @Column(name = "texto", nullable = false, length = 1000)
    private String texto;

    @Column(name = "interno", nullable = false)
    private boolean interno;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected ComentarioTicketEntity() { }

    public ComentarioTicketEntity(Long id, Long ticketId, Integer autorId, String texto, boolean interno,
            LocalDateTime fecha) {
        this.id = id; this.ticketId = ticketId; this.autorId = autorId; this.texto = texto;
        this.interno = interno; this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Integer getAutorId() { return autorId; }
    public String getTexto() { return texto; }
    public boolean isInterno() { return interno; }
    public LocalDateTime getFecha() { return fecha; }
}