package cl.casol.backend.catalogo.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cl_frecuencia")
public class FrecuenciaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "frecuencia_id")
    private Integer id;

    @Column(name = "frecuencia_nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "frecuencia_orden", nullable = false)
    private Integer orden;

    @Column(name = "frecuencia_estado", nullable = false)
    private boolean activa;

    protected FrecuenciaEntity() { }
    public FrecuenciaEntity(Integer id, String nombre, Integer orden, boolean activa) { this.id=id; this.nombre=nombre; this.orden=orden; this.activa=activa; }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public Integer getOrden() { return orden; }
    public boolean isActiva() { return activa; }
}
