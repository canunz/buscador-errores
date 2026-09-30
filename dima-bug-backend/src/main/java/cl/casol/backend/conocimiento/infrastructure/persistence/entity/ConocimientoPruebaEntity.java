package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "co_conocimiento_prueba")
public class ConocimientoPruebaEntity {
    @EmbeddedId
    private ConocimientoPruebaId id;

    @MapsId("pruebaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prueba_id", nullable = false)
    private PruebaEntity prueba;

    @Column(name = "prueba_orden", nullable = false)
    private Integer orden;

    protected ConocimientoPruebaEntity() { }

    public ConocimientoPruebaEntity(ConocimientoPruebaId id, PruebaEntity prueba, Integer orden) {
        this.id = id;
        this.prueba = prueba;
        this.orden = orden;
    }

    public ConocimientoPruebaId getId() { return id; }
    public PruebaEntity getPrueba() { return prueba; }
    public Integer getOrden() { return orden; }
}
