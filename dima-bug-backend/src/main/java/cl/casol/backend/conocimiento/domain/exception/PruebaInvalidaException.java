package cl.casol.backend.conocimiento.domain.exception;

/**
 * Error del módulo de conocimiento: prueba inválida.
 * El manejador global de errores la transforma en una respuesta HTTP con su mensaje.
 */
public class PruebaInvalidaException extends RuntimeException { public PruebaInvalidaException(String mensaje){super(mensaje);} }
