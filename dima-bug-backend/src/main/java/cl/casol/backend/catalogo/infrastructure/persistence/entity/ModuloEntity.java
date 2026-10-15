package cl.casol.backend.catalogo.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cl_modulo")
public class ModuloEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "modulo_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sistema_id")
    private SistemaEntity sistema;

    @Column(name = "modulo_nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "modulo_estado", nullable = false)
    private boolean activo;

    protected ModuloEntity() { }
    public ModuloEntity(Integer id, SistemaEntity sistema, String nombre, boolean activo) { this.id=id; this.sistema=sistema; this.nombre=nombre; this.activo=activo; }

    public Integer getId() { return id; }
    public SistemaEntity getSistema() { return sistema; }
    public String getNombre() { return nombre; }
    public boolean isActivo() { return activo; }
}
