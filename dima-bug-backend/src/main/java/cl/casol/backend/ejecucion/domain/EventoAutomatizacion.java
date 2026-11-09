package cl.casol.backend.ejecucion.domain;

import java.time.Instant;

public record EventoAutomatizacion(Long id,Integer ejecucionId,String solicitudId,String tipo,
        Integer actorId,String detalle,Instant fecha) { }
