package cl.casol.backend.ejecucion.application.service;

/** Se entrega solo después de confirmar la preparación SQL. No contiene credenciales. */
public record CorreoPendiente(String solicitudId,int reglaHoras) { }
