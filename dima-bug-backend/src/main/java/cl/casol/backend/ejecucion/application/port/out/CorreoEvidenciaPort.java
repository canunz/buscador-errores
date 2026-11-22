package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
/**
 * Puerto de salida de correo evidencia.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface CorreoEvidenciaPort {
    // Indica si está simulado.
    boolean simulado();
    // Indica si está disponible.
    boolean disponible();
    default String transporte(){return simulado()?"SIMULADO":"SMTP";}
    // Envía la solicitud.
    String enviar(SolicitudEvidencia solicitud,int reglaHoras);
}
