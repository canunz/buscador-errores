package cl.casol.backend.identidad.domain;

//Paso 1
import java.time.LocalDateTime;

public class Rol {

    private Integer id;
    private String nombre;
    private String descripcion;
    private boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Rol(
            Integer id,
            String nombre,
            String descripcion,
            boolean activo,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion
    ) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaActualizacion;
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