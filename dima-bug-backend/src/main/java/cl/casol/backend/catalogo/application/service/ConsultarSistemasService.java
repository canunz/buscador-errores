package cl.casol.backend.catalogo.application.service;

import cl.casol.backend.catalogo.application.port.out.ModuloRepository;
import cl.casol.backend.catalogo.application.port.out.SistemaRepository;
import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Modulo;
import cl.casol.backend.catalogo.domain.Sistema;
import cl.casol.backend.catalogo.domain.exception.CatalogoNoEncontradoException;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Servicio de consultar sistemas (módulo de catálogos).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
public class ConsultarSistemasService {
    private final SistemaRepository sistemas;
    private final ModuloRepository modulos;

    public ConsultarSistemasService(SistemaRepository sistemas, ModuloRepository modulos) {
        this.sistemas = sistemas;
        this.modulos = modulos;
    }

    public List<Sistema> listar() { return sistemas.buscarActivos(); }

    // Busca por id.
    public Sistema buscar(Integer id) {
        return sistemas.buscarActivoPorId(id)
                .orElseThrow(() -> new CatalogoNoEncontradoException("Sistema", id));
    }

    // Lista módulos.
    public List<Modulo> listarModulos(Integer id) {
        buscar(id);
        return modulos.buscarActivosPorSistema(id);
    }

    // Lista hardware.
    public List<Hardware> listarHardware(Integer id) {
        buscar(id);
        return sistemas.buscarHardwareActivoPorSistema(id);
    }
}
