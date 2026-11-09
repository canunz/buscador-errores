package cl.casol.backend.ejecucion.infrastructure.automatizacion;
import cl.casol.backend.ejecucion.application.service.AutomatizarEvidenciaService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class SeguimientoEvidenciaWorker {
    private final AutomatizarEvidenciaService service;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(SeguimientoEvidenciaWorker.class);
    public SeguimientoEvidenciaWorker(AutomatizarEvidenciaService service){this.service=service;}
    @Scheduled(fixedDelayString="${dimabug.automatizacion.intervalo-ms:60000}",initialDelayString="${dimabug.automatizacion.intervalo-ms:60000}")
    public void ejecutar(){
        try{for(String id:service.vencidas())try{service.procesarSeguimiento(id);}catch(RuntimeException e){LOG.warn("Seguimiento no confirmado; se reintentará la consulta. Tipo: {}",e.getClass().getSimpleName());}}
        catch(RuntimeException e){LOG.warn("No se pudo consultar el seguimiento; revisar esquema/configuración. Tipo: {}",e.getClass().getSimpleName());}
    }
}
