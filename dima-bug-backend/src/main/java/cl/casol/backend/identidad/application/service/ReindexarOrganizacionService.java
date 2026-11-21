package cl.casol.backend.identidad.application.service;
import cl.casol.backend.identidad.application.port.out.ReferenciasOrganizacionRepository;
import cl.casol.backend.conocimiento.application.service.IndexarConocimientoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
/**
 * Servicio de reindexar organización (módulo de usuarios y acceso).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
public class ReindexarOrganizacionService {
    private final ReferenciasOrganizacionRepository referencias;
    private final IndexarConocimientoService indexador;
    public ReindexarOrganizacionService(ReferenciasOrganizacionRepository referencias,IndexarConocimientoService indexador) {
        this.referencias=referencias; this.indexador=indexador;
    }
    // Actualiza por id.
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void actualizar(Integer id) { referencias.conocimientos(id).forEach(indexador::indexar); }
}
