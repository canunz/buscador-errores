package cl.casol.backend.identidad.domain;

//Paso 1: Objetos del negocio!, Usuario tiene un Rol
// El dominio NO debería necesitar saber que existe: MySQL o PostgreSQL
import java.time.LocalDateTime;

public class Usuario {

    private Integer id;
    private Rol rol;
    private String nombre;
    private String email;
    private String passwordHash;
    private boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    private EstadoRegistro estadoRegistro = EstadoRegistro.ADMINISTRADO;

    public Usuario(Integer id, Rol rol, String nombre, String email, String passwordHash,
            boolean activo, LocalDateTime creada, LocalDateTime actualizada, EstadoRegistro estado) {
        this(id, rol, nombre, email, passwordHash, activo, creada, actualizada);
        this.estadoRegistro = java.util.Objects.requireNonNull(estado);
    }
    public EstadoRegistro getEstadoRegistro() { return estadoRegistro; }
    public boolean isAccesoBasico() { return estadoRegistro != EstadoRegistro.ADMINISTRADO; }
    public boolean puedeIngresar() {
        return activo && rol != null && rol.isActivo()
            && (estadoRegistro == EstadoRegistro.ADMINISTRADO
                || estadoRegistro == EstadoRegistro.AUTO_CONFIRMADO && "USUARIO".equals(rol.getNombre()));
    }
    public boolean puedeRecibirCodigo() {
        return puedeIngresar() || !activo && estadoRegistro == EstadoRegistro.AUTO_PENDIENTE
            && rol != null && rol.isActivo() && "USUARIO".equals(rol.getNombre());
    }
    public Usuario confirmarRegistro() {
        if (estadoRegistro != EstadoRegistro.AUTO_PENDIENTE || !puedeRecibirCodigo())
            throw new IllegalStateException("REGISTRO_NO_CONFIRMABLE");
        return new Usuario(id, rol, nombre, email, passwordHash, true, fechaCreacion,
            LocalDateTime.now(), EstadoRegistro.AUTO_CONFIRMADO);
    }

    public Usuario(
            Integer id,
            Rol rol,
            String nombre,
            String email,
            String passwordHash,
            boolean activo,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion
    ) {
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

    public Rol getRol() {
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

