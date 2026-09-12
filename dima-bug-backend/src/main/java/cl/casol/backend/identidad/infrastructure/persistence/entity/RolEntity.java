package cl.casol.backend.identidad.infrastructure.persistence.entity;

//Paso 3: representa cómo está guardado el usuario en MySQL.
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "u_rol")
public class RolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rol_id")
    private Integer id;

    @Column(name = "rol_nombre", nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(name = "rol_descripcion", length = 200)
    private String descripcion;

    @Column(name = "rol_estado", nullable = false)
    private boolean activo;

    @Column(name = "rol_fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "rol_fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected RolEntity() {
    }

    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
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