package cl.casol.backend.tickets.infrastructure.web;

import cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException;
import cl.casol.backend.tickets.domain.exception.AdjuntoNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.ConocimientoYaVinculadoException;
import cl.casol.backend.tickets.domain.exception.EtiquetaDuplicadaException;
import cl.casol.backend.tickets.domain.exception.EtiquetaNoEncontradaException;
import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Errores propios del módulo de tickets, con el mismo formato {"message": "..."} del GlobalExceptionHandler.
 * Las validaciones de @Valid y los errores generales siguen en el manejador global.
 */
@RestControllerAdvice(basePackages = "cl.casol.backend.tickets")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TicketExceptionHandler {

    @ExceptionHandler({TicketNoEncontradoException.class, EtiquetaNoEncontradaException.class,
            AdjuntoNoEncontradoException.class})
    public ResponseEntity<Map<String, String>> noEncontrado(RuntimeException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({TransicionEstadoInvalidaException.class, EtiquetaDuplicadaException.class,
            ConocimientoYaVinculadoException.class})
    public ResponseEntity<Map<String, String>> conflicto(RuntimeException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TicketInvalidoException.class)
    public ResponseEntity<Map<String, String>> invalido(TicketInvalidoException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccesoTicketDenegadoException.class)
    public ResponseEntity<Map<String, String>> sinPermiso(AccesoTicketDenegadoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "El parámetro " + ex.getName() + " no es válido");
    }

    private static ResponseEntity<Map<String, String>> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("message", mensaje));
    }
}
