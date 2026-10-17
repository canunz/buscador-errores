package cl.casol.backend.catalogo.api;

import cl.casol.backend.catalogo.application.port.out.HardwareRepository;
import cl.casol.backend.catalogo.application.port.out.ModuloRepository;
import cl.casol.backend.catalogo.application.port.out.SistemaRepository;
import cl.casol.backend.catalogo.domain.Hardware;
import cl.casol.backend.catalogo.domain.Modulo;
import cl.casol.backend.catalogo.domain.Sistema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** API pública mínima del módulo de catálogos: valida y nombra la cascada hardware → sistema → módulo. */
@Service
@Transactional(readOnly = true)
public class CatalogoFachada {
    private final HardwareRepository hardware;
    private final SistemaRepository sistemas;
    private final ModuloRepository modulos;

    public CatalogoFachada(HardwareRepository hardware, SistemaRepository sistemas, ModuloRepository modulos) {
        this.hardware = hardware;
        this.sistemas = sistemas;
        this.modulos = modulos;
    }

    /** Devuelve el motivo del rechazo, o vacío si la combinación es válida. Mismas reglas que el conocimiento. */
    public Optional<String> validarClasificacion(Integer hardwareId, Integer sistemaId, Integer moduloId) {
        if (hardwareId != null && hardware.buscarActivoPorId(hardwareId).isEmpty())
            return Optional.of("Hardware inexistente o inactivo");
        if (sistemaId != null && sistemas.buscarActivoPorId(sistemaId).isEmpty())
            return Optional.of("Sistema inexistente o inactivo");
        if (hardwareId != null && sistemaId != null && hardware.buscarSistemasActivosPorHardware(hardwareId).stream()
                .noneMatch(s -> sistemaId.equals(s.id())))
            return Optional.of("El sistema no está asociado al hardware informado");
        if (moduloId != null) {
            if (sistemaId == null) return Optional.of("Debe informar el sistema cuando informa el módulo");
            Optional<Modulo> modulo = modulos.buscarActivoPorId(moduloId);
            if (modulo.isEmpty()) return Optional.of("Módulo inexistente o inactivo");
            if (!sistemaId.equals(modulo.get().sistemaId())) return Optional.of("El módulo no pertenece al sistema informado");
        }
        return Optional.empty();
    }

    public NombresClasificacion nombres(Integer hardwareId, Integer sistemaId, Integer moduloId) {
        String hw = hardwareId == null ? null : hardware.buscarActivoPorId(hardwareId).map(Hardware::nombre).orElse(null);
        String sis = sistemaId == null ? null : sistemas.buscarActivoPorId(sistemaId).map(Sistema::nombre).orElse(null);
        String mod = moduloId == null ? null : modulos.buscarActivoPorId(moduloId).map(Modulo::nombre).orElse(null);
        return new NombresClasificacion(hw, sis, mod);
    }
}