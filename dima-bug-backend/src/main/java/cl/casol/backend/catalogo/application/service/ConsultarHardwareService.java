package cl.casol.backend.catalogo.application.service;

import cl.casol.backend.catalogo.application.port.out.HardwareRepository;
import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.catalogo.domain.exception.CatalogoNoEncontradoException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ConsultarHardwareService {
    private final HardwareRepository hardware;

    public ConsultarHardwareService(HardwareRepository hardware) {
        this.hardware = hardware;
    }

    public List<Hardware> listar() { return hardware.buscarActivos(); }

    public Hardware buscar(Integer id) {
        return hardware.buscarActivoPorId(id)
                .orElseThrow(() -> new CatalogoNoEncontradoException("Hardware", id));
    }

    public List<Sistema> listarSistemas(Integer id) {
        buscar(id);
        return hardware.buscarSistemasActivosPorHardware(id);
    }
}
