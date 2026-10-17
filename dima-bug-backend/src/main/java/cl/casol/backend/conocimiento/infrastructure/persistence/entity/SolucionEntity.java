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

    @Column(name = "solucion_titulo", length = 150) private String titulo;
    @Column(name = "solucion_estado", nullable = false) private boolean activo = true;
    @Column(name = "solucion_autoservicio", nullable = false) private boolean autoservicio;

    public SolucionEntity(Integer id, Integer conocimientoId, String descripcion, TipoSolucion tipo,
            Integer orden, String titulo, boolean activo, boolean autoservicio) {
        this(id, conocimientoId, descripcion, tipo, orden);
        this.titulo = titulo; this.activo = activo; this.autoservicio = autoservicio;
    }
    public String getTitulo() { return titulo; }
    public boolean isActivo() { return activo; }
    public boolean isAutoservicio() { return autoservicio; }

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
