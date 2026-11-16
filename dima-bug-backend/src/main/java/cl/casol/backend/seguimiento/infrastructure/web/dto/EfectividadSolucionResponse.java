package cl.casol.backend.seguimiento.infrastructure.web.dto;

import cl.casol.backend.seguimiento.domain.EfectividadSolucion;

public record EfectividadSolucionResponse(Integer solucionId,long totalAplicaciones,long totalFunciono,
        long totalNoFunciono,Double porcentajeEfectividad) {
    public static EfectividadSolucionResponse from(EfectividadSolucion e){return new EfectividadSolucionResponse(
            e.solucionId(),e.totalAplicaciones(),e.totalFunciono(),e.totalNoFunciono(),e.porcentajeEfectividad());}
}
