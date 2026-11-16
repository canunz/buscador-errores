package cl.casol.backend.identidad.application.service;

import cl.casol.backend.correo.application.port.out.*;
import cl.casol.backend.correo.application.service.GmailException;
import cl.casol.backend.identidad.application.port.out.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static cl.casol.backend.identidad.application.port.out.CorreoCodigoStore.Estado.*;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "dimabug.auth.correo.habilitado", havingValue = "true", matchIfMissing = true)
public class DespacharCodigosService {
    private static final Logger log = LoggerFactory.getLogger(DespacharCodigosService.class);
    private final CorreoCodigoStore store;
    private final CodigoAccesoRepository codigos;
    private final CifradoGmailPort cifrado;
    private final CorreoTransportePort transporte;
    private final AtomicBoolean ocupado = new AtomicBoolean();
    @org.springframework.beans.factory.annotation.Value("${dimabug.auth.correo.maximo-minuto:10}")
    private int maximoMinuto = 10;
    private long inicioVentana = System.nanoTime();
    private int intentosVentana;

    private synchronized boolean admitirEnvio() {
        long ahora = System.nanoTime();
        if (ahora - inicioVentana >= 60_000_000_000L) { inicioVentana = ahora; intentosVentana = 0; }
        if (intentosVentana >= maximoMinuto) return false;
        intentosVentana++; return true;
    }

    public DespacharCodigosService(CorreoCodigoStore store, CodigoAccesoRepository codigos,
                                  CifradoGmailPort cifrado, CorreoTransportePort transporte) {
        this.store = store; this.codigos = codigos; this.cifrado = cifrado; this.transporte = transporte;
    }

    @Scheduled(fixedDelayString = "${dimabug.auth.correo.intervalo-ms:10000}",
            initialDelayString = "${dimabug.auth.correo.intervalo-ms:10000}", scheduler = "authCorreoScheduler")
    public void procesar() {
        if (!ocupado.compareAndSet(false, true)) return;
        try {
            for (String id : store.pendientes(LocalDateTime.now())) procesar(id);
        } catch (RuntimeException ex) {
            log.warn("No fue posible procesar la cola de autenticación; tipo={}", ex.getClass().getSimpleName());
        } finally { ocupado.set(false); }
    }

    public void procesar(String id) {
        if (!admitirEnvio()) return;
        var trabajo = store.reservar(id, LocalDateTime.now());
        if (trabajo.isEmpty()) return;
        var t = trabajo.get();
        var estado = ERROR_PERMANENTE;
        String referencia = null;
        try {
            if (!codigos.vigente(t.codigoId(), LocalDateTime.now())) estado = EXPIRADO;
            else {
                String raw = cifrado.descifrar(t.cifrado(), "auth:correo:" + id);
                referencia = transporte.enviarAutenticacion(raw);
                estado = ENVIADO;
            }
        } catch (GmailException ex) {
            estado = switch (ex.tipo) {
                case CONFIGURACION -> NO_CONFIGURADO;
                case AUTORIZACION -> REAUTORIZAR;
                // CONEXION solo procede de renovación OAuth, antes del POST de envío.
                case CONEXION, CONFLICTO -> t.intentos() < 3 ? ERROR_TEMPORAL : ERROR_PERMANENTE;
                case ENVIO -> INCIERTO;
                default -> ERROR_PERMANENTE;
            };
        } catch (RuntimeException ex) {
            // Una excepción inesperada puede ocurrir después de aceptar el mensaje.
            estado = INCIERTO;
        }
        store.terminar(id, estado, referencia, LocalDateTime.now().plusSeconds(20L * t.intentos()));
        log.info("Correo de autenticación id={} estado={}", id, estado);
    }
}
