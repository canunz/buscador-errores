package cl.casol.backend.identidad.application.service;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class ReindexarOrganizacionListener {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ReindexarOrganizacionListener.class);
    private final ReindexarOrganizacionService reindexar;
    public ReindexarOrganizacionListener(ReindexarOrganizacionService reindexar) { this.reindexar=reindexar; }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void actualizar(OrganizacionActualizada evento) {
        try { reindexar.actualizar(evento.departamentoId()); }
        catch(RuntimeException e) { LOG.error("Organización {} guardada; reindexación pendiente. Reintentar guardando de nuevo su estado vigente",evento.departamentoId(),e); }
    }
}
