package cl.casol.backend.tickets.infrastructure.persistence.entity;

import cl.casol.backend.tickets.domain.NivelEtiqueta;
import jakarta.persistence.*;

@Entity
@Table(name = "ticket_etiqueta")
public class EtiquetaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 15)
    private NivelEtiqueta nivel = NivelEtiqueta.NORMAL;

    protected EtiquetaEntity() { }

    public EtiquetaEntity(Long id, String nombre, String color, boolean activa, NivelEtiqueta nivel) {
        this.id = id; this.nombre = nombre; this.color = color; this.activa = activa;
        this.nivel = nivel == null ? NivelEtiqueta.NORMAL : nivel;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getColor() { return color; }
    public boolean isActiva() { return activa; }
    public NivelEtiqueta getNivel() { return nivel; }
}
