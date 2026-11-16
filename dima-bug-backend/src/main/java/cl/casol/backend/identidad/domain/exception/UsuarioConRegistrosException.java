package cl.casol.backend.identidad.domain.exception;

/** El usuario tiene tickets, comentarios u otros registros asociados y no se puede borrar. */
public class UsuarioConRegistrosException extends RuntimeException {
    public UsuarioConRegistrosException() {
        super("No se puede eliminar este usuario porque tiene tickets u otros registros asociados. Puedes inactivarlo para que no pueda ingresar.");
    }
}
