package cl.casol.backend.identidad.application.port.out;
import cl.casol.backend.identidad.domain.*;
import java.util.*;
/**
 * Repositorio Spring Data de administración organización.
 * Spring genera las consultas a partir del nombre de cada método.
 */
public interface AdministracionOrganizacionRepository {
    // Bloquea escrituras.
    void bloquearEscrituras();
    // Bloquea departamento.
    void bloquearDepartamento(Integer id);
    // Lista departamentos.
    List<DepartamentoAdministrativo> listarDepartamentos();
    // Busca departamento.
    Optional<DepartamentoAdministrativo> buscarDepartamento(Integer id);
    // Guarda departamento.
    DepartamentoAdministrativo guardarDepartamento(DepartamentoAdministrativo departamento);
    // Lista contactos.
    List<ContactoDepartamentoAdministrativo> listarContactos(Integer departamentoId);
    // Busca contacto.
    Optional<ContactoDepartamentoAdministrativo> buscarContacto(Integer id);
    // Guarda contacto.
    ContactoDepartamentoAdministrativo guardarContacto(ContactoDepartamentoAdministrativo contacto);
}
