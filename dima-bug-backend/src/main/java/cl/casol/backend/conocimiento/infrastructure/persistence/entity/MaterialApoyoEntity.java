package cl.casol.backend.conocimiento.infrastructure.persistence.entity;

import cl.casol.backend.conocimiento.domain.TipoMaterial;
import jakarta.persistence.*;

@Entity
@Table(name = "co_material_apoyo")
public class MaterialApoyoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_id")
    private Integer id;

    @Column(name = "conocimiento_id")
    private Integer conocimientoId;

    @Column(name = "paso_id")
    private Integer pasoId;

    @Column(name = "material_nombre", nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_tipo", nullable = false)
    private TipoMaterial tipo;

    @Column(name = "material_url", nullable = false, length = 500)
    private String url;

    protected MaterialApoyoEntity() { }

    public MaterialApoyoEntity(Integer id, Integer conocimientoId, Integer pasoId,
            String nombre, TipoMaterial tipo, String url) {
        this.id = id; this.conocimientoId = conocimientoId; this.pasoId = pasoId;
        this.nombre = nombre; this.tipo = tipo; this.url = url;
    }

    public Integer getId() { return id; }
    public Integer getConocimientoId() { return conocimientoId; }
    public Integer getPasoId() { return pasoId; }
    public String getNombre() { return nombre; }
    public TipoMaterial getTipo() { return tipo; }
    public String getUrl() { return url; }
}
