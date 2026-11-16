package cl.casol.backend.conocimiento.application.service;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component public class ReindexarPruebaListener {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ReindexarPruebaListener.class);
 private final ReindexarPruebaService servicio;
 public ReindexarPruebaListener(ReindexarPruebaService servicio){this.servicio=servicio;}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void actualizar(PruebaActualizada e){try{servicio.actualizar(e.id());}catch(RuntimeException fallo){LOG.error("Prueba {} guardada; reindexación pendiente. Reintentar su estado vigente",e.id(),fallo);}}
}