package cl.casol.backend.seguimiento.infrastructure.persistence.repository;

/**
 * Contrato de efectividad projection (módulo de seguimiento).
 */
public interface EfectividadProjection {
    long getTotalAplicaciones();
    long getTotalFunciono();
    long getTotalNoFunciono();
}
