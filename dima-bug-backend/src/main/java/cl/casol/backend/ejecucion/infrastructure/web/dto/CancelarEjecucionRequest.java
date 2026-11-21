package cl.casol.backend.ejecucion.infrastructure.web.dto;
/**
 * Datos que llegan en la petición para cancelar ejecución.
 * El controlador los recibe y los pasa al caso de uso.
 */
public record CancelarEjecucionRequest(String observaciones) { }
