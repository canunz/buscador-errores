package cl.casol.backend.identidad.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "u_departamento_contacto")
public class DepartamentoContactoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contacto_id")
    private Integer id;

    @Column(name = "departamento_id", nullable = false)
    private Integer departamentoId;

    @Column(name = "contacto_tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "contacto_valor", nullable = false, length = 100)
    private String valor;

    @Column(name = "contacto_estado", nullable = false)
    private boolean activo;

    protected DepartamentoContactoEntity() { }

    public Integer getId() { return id; }
    public Integer getDepartamentoId() { return departamentoId; }
    public String getTipo() { return tipo; }
    public String getValor() { return valor; }
    public boolean isActivo() { return activo; }
}
