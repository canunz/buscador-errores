package cl.casol.backend.identidad.application.port.out;
import cl.casol.backend.identidad.domain.*;
import java.util.*;
public interface AdministracionOrganizacionRepository {
    void bloquearEscrituras();
    void bloquearDepartamento(Integer id);
    List<DepartamentoAdministrativo> listarDepartamentos();
    Optional<DepartamentoAdministrativo> buscarDepartamento(Integer id);
    DepartamentoAdministrativo guardarDepartamento(DepartamentoAdministrativo departamento);
    List<ContactoDepartamentoAdministrativo> listarContactos(Integer departamentoId);
    Optional<ContactoDepartamentoAdministrativo> buscarContacto(Integer id);
    ContactoDepartamentoAdministrativo guardarContacto(ContactoDepartamentoAdministrativo contacto);
}
