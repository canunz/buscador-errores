package cl.casol.backend.ejecucion.infrastructure.web.dto;
import jakarta.validation.constraints.Size;
public record ActualizarEjecucionPasoRequest(Boolean cumplido,
        @Size(max=300,message="La observacion no puede superar 300 caracteres") String observacion) { }
