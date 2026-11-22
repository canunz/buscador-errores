package cl.casol.backend.whatsapp.application.port.out;

import cl.casol.backend.whatsapp.domain.*;
import java.time.Instant;
import java.util.List;

/** Operaciones atómicas del agregado, no CRUD independientes de sus tres tablas. */
public interface AlmacenWhatsAppPort {
    final class EventoConflictivo extends RuntimeException {
        public EventoConflictivo(){super("Identificador WhatsApp reutilizado con contenido distinto");}
    }
    record Entrada(MensajeCanal mensaje, Instant emitido) {
        @Override public String toString(){return "Entrada[contenido omitido]";}
    }
    record Recibo(int aceptados,int duplicados) { }
    record Reserva(long evento,long conversacion,String token,long cercado,MensajeCanal mensaje,
            EstadoConversacion estado,Instant ahora) {
        @Override public String toString(){return "Reserva[contenido omitido]";}
    }
    record Salida(String id,String token,long cercado,RespuestaCanal respuesta) {
        @Override public String toString(){return "Salida[contenido omitido]";}
    }
    Recibo recibir(List<Entrada> entradas);
    Recibo recibirLote(List<Entrada> entradas,List<EstadoEntregaCanal> estados);
    Reserva reservar(boolean imagen);
    boolean renovar(Reserva reserva);
    boolean finalizar(Reserva reserva,EstadoConversacion estado,RespuestaCanal respuesta);
    void reintentar(Reserva reserva,String codigo,int maximo);
    void recuperar();
    Salida reservarSalida();
    boolean simular(Salida salida,RespuestaCanal publica);
    void reintentarSalida(Salida salida);
    /** Registra que el proveedor aceptó el mensaje real. Solo guarda la huella de su identificador. */
    default boolean aceptar(Salida salida,RespuestaCanal publica,String idProveedor){
        throw new UnsupportedOperationException("El almacén no admite envío real");
    }
    /** Fallo del proveedor: reintento acotado (reintentable) o cierre definitivo. */
    default void reintentarEnvio(Salida salida,boolean reintentable){reintentarSalida(salida);}
}
