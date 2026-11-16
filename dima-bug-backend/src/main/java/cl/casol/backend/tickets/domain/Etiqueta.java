package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;

import java.util.regex.Pattern;

public class Etiqueta {
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    public static final int MAX_NOMBRE = 40;

    private final Long id;
    private String nombre;
    private String color;
    private boolean activa;
    private NivelEtiqueta nivel;

    public Etiqueta(Long id, String nombre, String color, boolean activa) {
        this(id, nombre, color, activa, NivelEtiqueta.NORMAL);
    }

    public Etiqueta(Long id, String nombre, String color, boolean activa, NivelEtiqueta nivel) {
        this.id = id;
        this.nombre = nombre;
        this.color = color;
        this.activa = activa;
        this.nivel = nivel == null ? NivelEtiqueta.NORMAL : nivel;
    }

    public static Etiqueta nueva(String nombre, String color) {
        return nueva(nombre, color, NivelEtiqueta.NORMAL);
    }

    public static Etiqueta nueva(String nombre, String color, NivelEtiqueta nivel) {
        Etiqueta etiqueta = new Etiqueta(null, null, null, true, nivel);
        etiqueta.editar(nombre, color, nivel);
        return etiqueta;
    }

    public void editar(String nombre, String color, NivelEtiqueta nivel) {
        editar(nombre, color);
        this.nivel = nivel == null ? NivelEtiqueta.NORMAL : nivel;
    }

    public void editar(String nombre, String color) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty()) throw new TicketInvalidoException("El nombre de la etiqueta es obligatorio");
        if (limpio.length() > MAX_NOMBRE)
            throw new TicketInvalidoException("El nombre de la etiqueta no puede superar " + MAX_NOMBRE + " caracteres");
        if (color == null || !COLOR.matcher(color.strip()).matches())
            throw new TicketInvalidoException("El color debe tener formato hexadecimal, por ejemplo #E53935");
        this.nombre = limpio;
        this.color = color.strip().toUpperCase();
    }

    public void cambiarEstado(boolean activa) { this.activa = activa; }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getColor() { return color; }
    public boolean isActiva() { return activa; }
    public NivelEtiqueta getNivel() { return nivel; }
}
