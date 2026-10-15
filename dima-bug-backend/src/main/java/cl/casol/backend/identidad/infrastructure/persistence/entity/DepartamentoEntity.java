package cl.casol.backend.identidad.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "u_departamento")
public class DepartamentoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "departamento_id")
    private Integer id;

    @Column(name = "departamento_nombre", nullable = false)
    private String nombre;

    @Column(name = "departamento_estado", nullable = false)
    private boolean activo;

    @Version
    @Column(name = "departamento_version", nullable = false)
    private Long version;

    public DepartamentoEntity(Integer id, String nombre, boolean activo, Long version) {
        this.id=id; this.nombre=nombre; this.activo=activo; this.version=version;
    }
    public Long getVersion() { return version; }
    protected DepartamentoEntity() { }
    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public boolean isActivo() { return activo; }
}
