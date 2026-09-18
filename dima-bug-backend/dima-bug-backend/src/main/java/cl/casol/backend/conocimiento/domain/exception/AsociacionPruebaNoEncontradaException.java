package cl.casol.backend.conocimiento.domain.exception;

public class AsociacionPruebaNoEncontradaException extends RuntimeException {
    public AsociacionPruebaNoEncontradaException(Integer conocimientoId, Integer pruebaId) {
        super("No existe la asociación entre el conocimiento " + conocimientoId + " y la prueba " + pruebaId);
    }
}
