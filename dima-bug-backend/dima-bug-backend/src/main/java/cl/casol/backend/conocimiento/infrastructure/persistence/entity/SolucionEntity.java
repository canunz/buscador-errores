package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import cl.casol.backend.conocimiento.domain.TipoSolucion;
import jakarta.persistence.*;

@Entity
@Table(name = "co_solucion")
public class SolucionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "solucion_id") private Integer id;
    @Column(name = "conocimiento_id", nullable = false) private Integer conocimientoId;
    @Column(name = "solucion_descripcion", nullable = false, columnDefinition = "TEXT") private String descripcion;
    @Enumerated(EnumType.STRING)
    @Column(name = "solucion_tipo", nullable = false) private TipoSolucion tipo;
    @Column(name = "solucion_orden", nullable = false) private Integer orden;

    protected SolucionEntity() { }
    public SolucionEntity(Integer id, Integer conocimientoId, String descripcion, TipoSolucion tipo, Integer orden) {
        this.id = id; this.conocimientoId = conocimientoId; this.descripcion = descripcion;
        this.tipo = tipo; this.orden = orden;
    }
    public Integer getId() { return id; }
    public Integer getConocimientoId() { return conocimientoId; }
    public String getDescripcion() { return descripcion; }
    public TipoSolucion getTipo() { return tipo; }
    public Integer getOrden() { return orden; }
}
