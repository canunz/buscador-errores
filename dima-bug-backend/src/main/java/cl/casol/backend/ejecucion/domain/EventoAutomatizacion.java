package cl.casol.backend.ejecucion.domain;

import java.time.Instant;

/**
 * Modelo de dominio de evento automatización. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record EventoAutomatizacion(Long id,Integer ejecucionId,String solicitudId,String tipo,
        Integer actorId,String detalle,Instant fecha) { }
