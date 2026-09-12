package cl.casol.backend.identidad.domain;

//Paso 1: Objetos del negocio!, Usuario tiene un Rol
// El dominio no debería necesitar saber que existe: MySQL o PostgreSQL
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

