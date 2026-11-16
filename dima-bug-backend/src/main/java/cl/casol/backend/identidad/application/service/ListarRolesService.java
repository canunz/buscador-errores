package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.RolRepository;
import cl.casol.backend.identidad.domain.Rol;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListarRolesService {

    private final RolRepository rolRepository;

    public ListarRolesService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    public List<Rol> listarActivos() {
        return rolRepository.buscarActivos();
    }
}
