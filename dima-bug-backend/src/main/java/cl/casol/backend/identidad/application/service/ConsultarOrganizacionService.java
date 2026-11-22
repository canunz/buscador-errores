package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.DepartamentoRepository;
import cl.casol.backend.identidad.application.port.out.DepartamentoContactoRepository;
import cl.casol.backend.identidad.application.port.out.ResponsableRepository;
import cl.casol.backend.identidad.domain.Departamento;
import cl.casol.backend.identidad.domain.DepartamentoContacto;
import cl.casol.backend.identidad.domain.Responsable;
import cl.casol.backend.identidad.domain.exception.DepartamentoNoEncontradoException;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Servicio de consultar organización (módulo de usuarios y acceso).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
public class ConsultarOrganizacionService {
    private final DepartamentoRepository departamentos;
    private final ResponsableRepository responsables;
    private final DepartamentoContactoRepository contactos;

    public ConsultarOrganizacionService(DepartamentoRepository departamentos, ResponsableRepository responsables,
            DepartamentoContactoRepository contactos) {
        this.departamentos = departamentos;
        this.responsables = responsables;
        this.contactos = contactos;
    }

    // Lista departamentos.
    public List<Departamento> listarDepartamentos() {
        return departamentos.buscarActivosOrdenadosPorNombre();
    }

    // Lista responsables.
    public List<Responsable> listarResponsables(Integer departamentoId) {
        departamentos.buscarActivoPorId(departamentoId)
                .orElseThrow(() -> new DepartamentoNoEncontradoException(departamentoId));
        return responsables.buscarActivosPorDepartamentoOrdenadosPorNombre(departamentoId);
    }

    // Lista contactos.
    public List<DepartamentoContacto> listarContactos(Integer departamentoId) {
        departamentos.buscarActivoPorId(departamentoId)
                .orElseThrow(() -> new DepartamentoNoEncontradoException(departamentoId));
        return contactos.buscarActivosPorDepartamentoOrdenadosPorId(departamentoId);
    }
}
