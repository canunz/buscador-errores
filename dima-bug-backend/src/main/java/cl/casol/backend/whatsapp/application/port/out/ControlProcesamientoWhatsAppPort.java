package cl.casol.backend.whatsapp.application.port.out;

/** Puertas independientes; envío significa despacho a la salida instalada. */
public interface ControlProcesamientoWhatsAppPort {
    boolean procesamiento();
    boolean medios();
    boolean envio();
}
