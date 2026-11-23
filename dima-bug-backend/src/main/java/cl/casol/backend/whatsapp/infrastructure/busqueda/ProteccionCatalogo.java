package cl.casol.backend.whatsapp.infrastructure.busqueda;

import cl.casol.backend.whatsapp.domain.RespuestaCanal;

final class ProteccionCatalogo {
    private ProteccionCatalogo() { }
    static RespuestaCanal consultaNueva(RespuestaCanal original) {
        // No reutilizar el mensaje ni las instrucciones de un plan de procedencia incompatible.
        return RespuestaCanal.simple(RespuestaCanal.Estado.SIN_RESULTADOS,
                "La fuente de soluciones cambió. Vuelve a escribir el problema para consultar el catálogo vigente.")
                .conDestinatario(original.destinatario());
    }
}
