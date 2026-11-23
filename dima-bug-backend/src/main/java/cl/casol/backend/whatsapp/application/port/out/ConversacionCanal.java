package cl.casol.backend.whatsapp.application.port.out;

import cl.casol.backend.whatsapp.domain.*;

/** Reglas de una conversación restaurada desde el almacén durable: una instancia por reserva. */
public interface ConversacionCanal {
    void restaurar(MensajeCanal mensaje, EstadoConversacion estado);
    EstadoConversacion instantanea(MensajeCanal mensaje);
    RespuestaCanal procesar(MensajeCanal mensaje);
}
