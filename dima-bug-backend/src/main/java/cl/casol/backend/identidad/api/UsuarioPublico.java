package cl.casol.backend.identidad.api;

/** Datos de un usuario que otros módulos pueden consultar. No expone el dominio ni el hash de la clave. */
public record UsuarioPublico(Integer id, String nombre, String email, String rol, boolean activo, boolean accesoBasico) {
    public UsuarioPublico(Integer id, String nombre, String email, String rol, boolean activo) { this(id,nombre,email,rol,activo,false); }
}