package cl.casol.backend.identidad.infrastructure.persistence.entity;

//Paso 3: representa cómo está guardado el usuario en MySQL.
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "u_usuario")
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private RolEntity rol;

    @Column(name = "usuario_nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "usuario_email", nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "usuario_password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "usuario_estado", nullable = false)
    private boolean activo;

    @Column(name = "usuario_fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "usuario_fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected UsuarioEntity() {
    }

    public Integer getId() {
        return id;
    }

    public RolEntity getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActivo() {
        return activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}