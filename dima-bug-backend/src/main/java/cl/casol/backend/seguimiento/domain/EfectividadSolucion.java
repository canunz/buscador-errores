package cl.casol.backend.seguimiento.domain;

/**
 * Modelo de dominio de efectividad solución. Es inmutable: cada cambio crea un objeto nuevo.
 */
public record EfectividadSolucion(Integer solucionId, long totalAplicaciones,
        long totalFunciono, long totalNoFunciono, Double porcentajeEfectividad) { }
