package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.RolRepository;
import cl.casol.backend.identidad.domain.Rol;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de listar roles (módulo de usuarios y acceso).
 * Aquí van las reglas de negocio; para leer o guardar datos usa los puertos de salida.
 */
@Service
public class ListarRolesService {

    private final RolRepository rolRepository;

    public ListarRolesService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    // Lista activos.
    public List<Rol> listarActivos() {
        return rolRepository.buscarActivos();
    }
}
