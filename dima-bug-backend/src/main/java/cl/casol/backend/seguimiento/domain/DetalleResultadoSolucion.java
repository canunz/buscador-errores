package cl.casol.backend.seguimiento.domain;

import cl.casol.backend.identidad.domain.Usuario;

public record DetalleResultadoSolucion(ResultadoSolucion resultado, Usuario usuario) { }
