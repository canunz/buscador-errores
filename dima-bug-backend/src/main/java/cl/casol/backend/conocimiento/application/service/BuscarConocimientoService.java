package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.BusquedaConocimientoRepository;
import cl.casol.backend.conocimiento.domain.ResultadoBusquedaConocimiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BuscarConocimientoService {
    private final BusquedaConocimientoRepository busqueda;

    public BuscarConocimientoService(BusquedaConocimientoRepository busqueda) {
        this.busqueda = busqueda;
    }

    public List<ResultadoBusquedaConocimiento> buscar(String texto, Integer hardwareId, Integer sistemaId,
            Integer moduloId, Integer frecuenciaId) {
        String textoUtil = texto == null || texto.isBlank() ? null : texto.trim();
        return busqueda.buscar(textoUtil, hardwareId, sistemaId, moduloId, frecuenciaId);
    }
}
