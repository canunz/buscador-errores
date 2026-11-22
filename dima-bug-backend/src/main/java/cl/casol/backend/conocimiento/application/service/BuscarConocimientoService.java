package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.BusquedaConocimientoRepository;
import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Búsqueda de conocimientos (usa el índice de texto completo de MySQL). */
@Service
@Transactional(readOnly = true)
public class BuscarConocimientoService {
    private final BusquedaConocimientoRepository busqueda;

    public BuscarConocimientoService(BusquedaConocimientoRepository busqueda) {
        this.busqueda = busqueda;
    }

    /** Búsqueda para el chatbot y el autoservicio: solo conocimientos publicados. */
    public List<ResultadoBusquedaConocimiento> buscarAutoservicio(String texto) {
        return busqueda.buscarAutoservicio(texto.trim());
    }

    /** Búsqueda interna con filtros de hardware, sistema y módulo. */
    public List<ResultadoBusquedaConocimiento> buscar(String texto, Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId) {
        String textoUtil = texto == null || texto.isBlank() ? null : texto.trim();
        return busqueda.buscar(textoUtil, hardwareId, sistemaId, moduloId, frecuenciaId);
    }
}
