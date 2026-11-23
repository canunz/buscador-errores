package cl.casol.backend.whatsapp.application.port.out;

import java.util.List;

/** Levanta un ticket en DimaBug cuando la autoatención no resolvió el problema. */
public interface CrearTicketCanalPort {
    /**
     * @param mensajeId identificador del mensaje de WhatsApp que originó el ticket: clave de idempotencia
     *                  para que un reintento del mismo mensaje no cree un segundo ticket.
     * @param origen IMAGEN o TEXTO; @param solucionesOfrecidas títulos ya sugeridos al usuario.
     */
    record Solicitud(String mensajeId, String participante, String consulta, String origen, List<String> solucionesOfrecidas) {
        public Solicitud { solucionesOfrecidas = List.copyOf(solucionesOfrecidas); }
        @Override public String toString() { return "Solicitud[contenido omitido]"; }
    }
    /** Devuelve el código visible del ticket (por ejemplo, TK-2026-0123). */
    String crear(Solicitud solicitud);
    class FalloTicket extends RuntimeException {
        public FalloTicket() { super("No fue posible registrar el ticket."); }
    }
}
