package cl.casol.backend.ejecucion.application.port.out;
import cl.casol.backend.ejecucion.domain.SolicitudEvidencia;
public interface CorreoEvidenciaPort {
    boolean simulado();
    boolean disponible();
    default String transporte(){return simulado()?"SIMULADO":"SMTP";}
    String enviar(SolicitudEvidencia solicitud,int reglaHoras);
}
