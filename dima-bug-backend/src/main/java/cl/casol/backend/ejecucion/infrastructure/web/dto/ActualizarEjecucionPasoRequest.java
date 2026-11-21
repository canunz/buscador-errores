package cl.casol.backend.ejecucion.infrastructure.web.dto;
import jakarta.validation.constraints.Size;
/**
 * Datos que llegan en la petición para actualizar ejecución paso.
 * Las anotaciones de validación revisan los campos antes de llegar al servicio.
 */
public record ActualizarEjecucionPasoRequest(Boolean cumplido,
        @Size(max=300,message="La observacion no puede superar 300 caracteres") String observacion,
        @jakarta.validation.constraints.PositiveOrZero Long versionEsperada) { }
