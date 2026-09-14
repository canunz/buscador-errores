package cl.casol.backend.conocimiento.domain;

import cl.casol.backend.catalogo.domain.Frecuencia;
import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Modulo;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.identidad.domain.Usuario;

import java.time.LocalDateTime;

public class Conocimiento {
    private final Integer id;
    private final String titulo;
    private final String descripcion;
    private final EstadoConocimiento estado;
    private final Hardware hardware;
    private final Sistema sistema;
    private final Modulo modulo;
    private final Frecuencia frecuencia;
    private final String comentario;
    private final Usuario creadoPor;
    private final LocalDateTime fechaCreacion;
    private final Usuario modificadoPor;
    private final LocalDateTime fechaModificacion;

    public Conocimiento(Integer id, String titulo, String descripcion, EstadoConocimiento estado,
                        Hardware hardware, Sistema sistema, Modulo modulo, Frecuencia frecuencia,
                        String comentario, Usuario creadoPor, LocalDateTime fechaCreacion,
                        Usuario modificadoPor, LocalDateTime fechaModificacion) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.estado = estado;
        this.hardware = hardware;
        this.sistema = sistema;
        this.modulo = modulo;
        this.frecuencia = frecuencia;
        this.comentario = comentario;
        this.creadoPor = creadoPor;
        this.fechaCreacion = fechaCreacion;
        this.modificadoPor = modificadoPor;
        this.fechaModificacion = fechaModificacion;
    }

    public Integer getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public EstadoConocimiento getEstado() { return estado; }
    public Hardware getHardware() { return hardware; }
    public Sistema getSistema() { return sistema; }
    public Modulo getModulo() { return modulo; }
    public Frecuencia getFrecuencia() { return frecuencia; }
    public String getComentario() { return comentario; }
    public Usuario getCreadoPor() { return creadoPor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public Usuario getModificadoPor() { return modificadoPor; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
}
