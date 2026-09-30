package cl.casol.backend.catalogo.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cl_sistema")
public class SistemaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sistema_id")
    private Integer id;

    @Column(name = "sistema_nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "sistema_descripcion", length = 255)
    private String descripcion;

    @Column(name = "sistema_estado", nullable = false)
    private boolean activo;

    @ManyToMany(mappedBy = "sistemas", fetch = FetchType.LAZY)
    private Set<HardwareEntity> hardware = new HashSet<>();

    protected SistemaEntity() { }

    public SistemaEntity(Integer id, String nombre, String descripcion, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
    public Set<HardwareEntity> getHardware() { return hardware; }
}
