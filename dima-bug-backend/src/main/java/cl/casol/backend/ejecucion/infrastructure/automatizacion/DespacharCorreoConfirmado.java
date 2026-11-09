package cl.casol.backend.ejecucion.infrastructure.automatizacion;

import cl.casol.backend.ejecucion.application.port.out.CorreoEvidenciaPort;
import cl.casol.backend.ejecucion.application.service.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;

@Component
public class DespacharCorreoConfirmado {
    private final AutomatizarEvidenciaService servicio;
    private final CorreoEvidenciaPort correo;
    public DespacharCorreoConfirmado(AutomatizarEvidenciaService servicio,CorreoEvidenciaPort correo){this.servicio=servicio;this.correo=correo;}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void enviar(CorreoPendiente evento){
        var reservada=servicio.reservarEnvioSmtp(evento.solicitudId(),evento.reglaHoras());
        if(reservada.isEmpty())return;
        String referencia;
        try{referencia=correo.enviar(reservada.get(),evento.reglaHoras());}
        catch(RuntimeException ex){servicio.registrarResultadoSmtp(evento.solicitudId(),evento.reglaHoras(),null);return;}
        servicio.registrarResultadoSmtp(evento.solicitudId(),evento.reglaHoras(),referencia);
    }
}
