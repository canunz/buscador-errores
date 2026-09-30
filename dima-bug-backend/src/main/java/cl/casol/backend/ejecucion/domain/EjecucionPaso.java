package cl.casol.backend.ejecucion.domain;

import java.time.LocalDateTime;

public record EjecucionPaso(Integer id, Integer ejecucionId, Integer pasoId,
        boolean cumplido, String observacion, LocalDateTime fecha) { }
