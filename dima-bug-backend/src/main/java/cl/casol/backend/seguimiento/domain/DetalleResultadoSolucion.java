package cl.casol.backend.seguimiento.domain;

import cl.casol.backend.identidad.domain.Usuario;

/**
 * Modelo de dominio de detalle resultado solución. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record DetalleResultadoSolucion(ResultadoSolucion resultado, Usuario usuario) { }
