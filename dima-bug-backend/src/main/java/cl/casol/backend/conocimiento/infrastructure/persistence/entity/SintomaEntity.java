package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "co_sintoma")
public class SintomaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sintoma_id")
    private Integer id;

    @Column(name = "conocimiento_id", nullable = false)
    private Integer conocimientoId;

    @Column(name = "sintoma_descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "sintoma_orden", nullable = false)
    private Integer orden;

    protected SintomaEntity() { }

    public SintomaEntity(Integer id, Integer conocimientoId, String descripcion, Integer orden) {
        this.id = id;
        this.conocimientoId = conocimientoId;
        this.descripcion = descripcion;
        this.orden = orden;
    }

    public Integer getId() { return id; }
    public Integer getConocimientoId() { return conocimientoId; }
    public String getDescripcion() { return descripcion; }
    public Integer getOrden() { return orden; }
}
