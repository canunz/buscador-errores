package cl.casol.backend.identidad.domain.exception;

//Paso VI: Manejo de errores
public class UsuarioNoEncontradoException extends RuntimeException {
    public UsuarioNoEncontradoException() {
        super("Usuario no encontrado");
    }
}
