package cl.casol.backend.tickets.application.port.out;

/** Datos del ticket con los que se prellena un conocimiento nuevo en BORRADOR. */
public record BorradorConocimiento(String titulo, String descripcion, Integer hardwareId, Integer sistemaId,
        Integer moduloId, String solucion, String codigoTicket) {
}