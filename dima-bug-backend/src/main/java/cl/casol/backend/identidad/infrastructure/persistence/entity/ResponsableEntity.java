package cl.casol.backend.identidad.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "u_responsable")
public class ResponsableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "responsable_id")
    private Integer id;

    @Column(name = "departamento_id", nullable = false)
    private Integer departamentoId;

    @Column(name = "responsable_nombre", nullable = false)
    private String nombre;

    @Column(name = "responsable_cargo")
    private String cargo;

    @Column(name = "responsable_contacto")
    private String contacto;

    @Column(name = "responsable_estado", nullable = false)
    private boolean activo;

    protected ResponsableEntity() { }
    public ResponsableEntity(Integer departamentoId, String nombre, String cargo, String contacto, boolean activo) {
        this.departamentoId = departamentoId; actualizar(nombre, cargo, contacto, activo);
    }
    public void actualizar(String nombre, String cargo, String contacto, boolean activo) {
        this.nombre = nombre; this.cargo = cargo; this.contacto = contacto; this.activo = activo;
    }
    public Integer getId() { return id; }
    public Integer getDepartamentoId() { return departamentoId; }
    public String getNombre() { return nombre; }
    public String getCargo() { return cargo; }
    public String getContacto() { return contacto; }
    public boolean isActivo() { return activo; }
}
