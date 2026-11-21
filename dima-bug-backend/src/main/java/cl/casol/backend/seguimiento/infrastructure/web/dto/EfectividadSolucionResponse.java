package cl.casol.backend.seguimiento.infrastructure.web.dto;

import cl.casol.backend.seguimiento.domain.EfectividadSolucion;

/**
 * Datos que se devuelven al frontend con efectividad solución.
 * Solo lleva lo que la pantalla necesita mostrar.
 */
public record EfectividadSolucionResponse(Integer solucionId,long totalAplicaciones,long totalFunciono,
        long totalNoFunciono,Double porcentajeEfectividad) {
    // Crea el objeto a partir de los datos recibidos.
    public static EfectividadSolucionResponse from(EfectividadSolucion e){return new EfectividadSolucionResponse(
            e.solucionId(),e.totalAplicaciones(),e.totalFunciono(),e.totalNoFunciono(),e.porcentajeEfectividad());}
}
