package cl.casol.backend.seguimiento.domain;

public record EfectividadSolucion(Integer solucionId, long totalAplicaciones,
        long totalFunciono, long totalNoFunciono, Double porcentajeEfectividad) { }
