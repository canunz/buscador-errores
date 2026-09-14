package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.DepartamentoRepository;
import cl.casol.backend.identidad.application.port.out.ResponsableRepository;
import cl.casol.backend.identidad.domain.Departamento;
import cl.casol.backend.identidad.domain.Responsable;
import cl.casol.backend.identidad.domain.exception.DepartamentoNoEncontradoException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ConsultarOrganizacionService {
    private final DepartamentoRepository departamentos;
    private final ResponsableRepository responsables;

    public ConsultarOrganizacionService(DepartamentoRepository departamentos, ResponsableRepository responsables) {
        this.departamentos = departamentos;
        this.responsables = responsables;
    }

    public List<Departamento> listarDepartamentos() {
        return departamentos.buscarActivosOrdenadosPorNombre();
    }

    public List<Responsable> listarResponsables(Integer departamentoId) {
        departamentos.buscarActivoPorId(departamentoId)
                .orElseThrow(() -> new DepartamentoNoEncontradoException(departamentoId));
        return responsables.buscarActivosPorDepartamentoOrdenadosPorNombre(departamentoId);
    }
}
