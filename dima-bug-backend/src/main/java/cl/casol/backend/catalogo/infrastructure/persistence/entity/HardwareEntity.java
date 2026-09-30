package cl.casol.backend.catalogo.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cl_hardware")
public class HardwareEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hardware_id")
    private Integer id;

    @Column(name = "hardware_nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "hardware_sistema_operativo", length = 150)
    private String sistemaOperativo;

    @Column(name = "hardware_estado", nullable = false)
    private boolean activo;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "cl_hardware_sistema",
            joinColumns = @JoinColumn(name = "hardware_id"),
            inverseJoinColumns = @JoinColumn(name = "sistema_id"))
    private Set<SistemaEntity> sistemas = new HashSet<>();

    protected HardwareEntity() { }

    public HardwareEntity(Integer id, String nombre, String sistemaOperativo, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.sistemaOperativo = sistemaOperativo;
        this.activo = activo;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getSistemaOperativo() { return sistemaOperativo; }
    public boolean isActivo() { return activo; }
    public Set<SistemaEntity> getSistemas() { return sistemas; }
}
