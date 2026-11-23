package cl.casol.backend.whatsapp.domain;

import java.time.Instant;

/** Notificación neutral; todavía no implica correlación con un envío real. */
public record EstadoEntregaCanal(String cuenta,String canal,String mensaje,String participante,
        String estado,Instant emitido) {
    @Override public String toString(){return "EstadoEntregaCanal[contenido omitido]";}
}
