package cl.casol.backend.identidad.domain.exception;

/**
 * Error del módulo de usuarios y acceso: departamento no encontrado.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class DepartamentoNoEncontradoException extends RuntimeException {
    public DepartamentoNoEncontradoException(Integer id) {
        super("Departamento no encontrado o inactivo: " + id);
    }
}
