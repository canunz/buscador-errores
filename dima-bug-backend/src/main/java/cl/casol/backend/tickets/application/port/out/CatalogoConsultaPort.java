package cl.casol.backend.tickets.application.port.out;

import java.util.Optional;

/** Valida hardware → sistema → módulo usando la API pública del módulo de catálogos. */
public interface CatalogoConsultaPort {
    /** Devuelve el motivo si la combinación no es válida; vacío si está correcta. */
    Optional<String> validarClasificacion(Integer hardwareId, Integer sistemaId, Integer moduloId);

    ClasificacionTicket nombres(Integer hardwareId, Integer sistemaId, Integer moduloId);
}