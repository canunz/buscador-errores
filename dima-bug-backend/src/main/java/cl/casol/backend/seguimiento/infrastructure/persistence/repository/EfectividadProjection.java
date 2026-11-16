package cl.casol.backend.seguimiento.infrastructure.persistence.repository;

public interface EfectividadProjection {
    long getTotalAplicaciones();
    long getTotalFunciono();
    long getTotalNoFunciono();
}
