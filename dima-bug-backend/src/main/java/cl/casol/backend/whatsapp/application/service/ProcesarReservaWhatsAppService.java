package cl.casol.backend.whatsapp.application.service;

import cl.casol.backend.whatsapp.application.port.out.*;
import java.time.*;
import java.util.Set;
import java.util.function.Function;

/** OCR y búsqueda se ejecutan fuera de las transacciones del almacén. */
public final class ProcesarReservaWhatsAppService {
    private final AlmacenWhatsAppPort almacen;
    private final BuscarSolucionesCanalPort buscador;
    private final ReconocerImagenPort ocr;
    private final Function<Clock,ConversacionCanal> conversaciones;
    /** Flujo QA original: propuesta OCR con CONFIRMAR/CORREGIR/ELEGIR/CANCELAR. */
    public ProcesarReservaWhatsAppService(AlmacenWhatsAppPort a,BuscarSolucionesCanalPort b,ReconocerImagenPort o){
        almacen=a;buscador=b;ocr=o;conversaciones=reloj->new ProcesarMensajeService(b,o,reloj);
    }
    /** Autoatención: saludo, solución directa, verificación SÍ/NO y ticket en DimaBug. */
    public ProcesarReservaWhatsAppService(AlmacenWhatsAppPort a,BuscarSolucionesCanalPort b,ReconocerImagenPort o,CrearTicketCanalPort t){
        almacen=a;buscador=b;ocr=o;java.util.Objects.requireNonNull(t,"Puerto de tickets obligatorio");
        conversaciones=reloj->new AutoatencionWhatsAppService(b,o,t,reloj);
    }
    public void ejecutar(AlmacenWhatsAppPort.Reserva r) {
        try {
            // El reloj avanza durante OCR, conservando el origen UTC del reloj de BD.
            long inicio=System.nanoTime();
            Clock reloj=new Clock(){
                public ZoneId getZone(){return ZoneOffset.UTC;}
                public Clock withZone(ZoneId z){return this;}
                public Instant instant(){return r.ahora().plusNanos(System.nanoTime()-inicio);}
            };
            var procesador=conversaciones.apply(reloj);
            procesador.restaurar(r.mensaje(),r.estado());
            // El destinatario es el alias QA del participante; el adaptador de envío lo resuelve a teléfono.
            var respuesta=procesador.procesar(r.mensaje()).conDestinatario(r.mensaje().usuario());
            if(Set.of("ERROR_BUSCADOR","ERROR_OCR","TIEMPO_AGOTADO","ERROR_TICKET").contains(respuesta.estado().name())) {
                almacen.reintentar(r,respuesta.estado().name(),respuesta.estado().name().equals("TIEMPO_AGOTADO")?2:5);
            } else almacen.finalizar(r,procesador.instantanea(r.mensaje()),respuesta);
        } catch(RuntimeException e) { almacen.reintentar(r,"PROCESAMIENTO",5); }
    }
}
