package cl.casol.backend.identidad.infrastructure.persistence.entity;

//Paso 3: Representa cómo está guardado el usuario en MySQL.
import jakarta.persistence.*;
import cl.casol.backend.identidad.domain.EstadoRegistro;
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

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @org.hibernate.annotations.ColumnDefault("'ADMINISTRADO'")
    @Column(name="registro_estado", nullable=false, length=24)
    private EstadoRegistro estadoRegistro = EstadoRegistro.ADMINISTRADO;

    public UsuarioEntity(Integer id, RolEntity rol, String nombre, String email, String passwordHash,
            boolean activo, LocalDateTime creada, LocalDateTime actualizada, EstadoRegistro estado) {
        this(id, rol, nombre, email, passwordHash, activo, creada, actualizada);
        this.estadoRegistro = java.util.Objects.requireNonNull(estado);
    }
    public EstadoRegistro getEstadoRegistro() { return estadoRegistro; }

    protected UsuarioEntity() {
    }

    public UsuarioEntity(Integer id, RolEntity rol, String nombre, String email,
                         String passwordHash, boolean activo, LocalDateTime fechaCreacion,
                         LocalDateTime fechaActualizacion) {
        this.id = id;
        this.rol = rol;
        this.nombre = nombre;
        this.email = email;
        this.passwordHash = passwordHash;
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaActualizacion;
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
