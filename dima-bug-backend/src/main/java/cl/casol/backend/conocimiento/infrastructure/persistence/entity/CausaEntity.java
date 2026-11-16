package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "co_causa")
public class CausaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "causa_id")
    private Integer id;

    @Column(name = "conocimiento_id", nullable = false)
    private Integer conocimientoId;

    @Column(name = "causa_descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "causa_orden", nullable = false)
    private Integer orden;

    protected CausaEntity() { }

    public CausaEntity(Integer id, Integer conocimientoId, String descripcion, Integer orden) {
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
