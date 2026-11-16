package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AdministrarResponsablesService {
    private final AdministracionResponsableRepository responsables;
    private final AdministracionOrganizacionRepository organizacion;
    private final ApplicationEventPublisher eventos;
    public AdministrarResponsablesService(AdministracionResponsableRepository responsables,
            AdministracionOrganizacionRepository organizacion, ApplicationEventPublisher eventos) {
        this.responsables = responsables; this.organizacion = organizacion; this.eventos = eventos;
    }
    public List<ResponsableAdministrativo> listar(Integer departamentoId) {
        if (departamentoId != null) departamento(departamentoId);
        return responsables.listar(departamentoId).stream().map(ResponsableAdministrativo::from).toList();
    }
    public ResponsableAdministrativo buscar(Integer id) {
        return ResponsableAdministrativo.from(responsables.buscar(id).orElseThrow(() -> new ResponsableNoEncontradoException(id)));
    }
    @Transactional
    public ResponsableAdministrativo crear(Integer departamentoId, String nombre, String cargo, String contacto) {
        if (departamentoId == null) throw new OrganizacionInvalidaException("Departamento obligatorio");
        organizacion.bloquearEscrituras(); organizacion.bloquearDepartamento(departamentoId);
        disponible(departamento(departamentoId));
        return guardar(new Responsable(null, departamentoId, texto(nombre,160,true,"Nombre"),
                texto(cargo,120,false,"Cargo"), texto(contacto,160,false,"Contacto"), true));
    }
    @Transactional
    public ResponsableAdministrativo editar(Integer id, String nombre, String cargo, String contacto, String revision) {
        Responsable actual = original(id, revision);
        return guardar(new Responsable(id, actual.departamentoId(), texto(nombre,160,true,"Nombre"),
                texto(cargo,120,false,"Cargo"), texto(contacto,160,false,"Contacto"), actual.activo()));
    }
    @Transactional
    public ResponsableAdministrativo estado(Integer id, boolean activo, String revision) {
        Responsable actual = original(id, revision);
        if (activo && !actual.activo()) disponible(departamento(actual.departamentoId()));
        if (activo == actual.activo()) return ResponsableAdministrativo.from(actual);
        return guardar(new Responsable(id, actual.departamentoId(), actual.nombre(), actual.cargo(), actual.contacto(), activo));
    }
    private Responsable original(Integer id, String revision) {
        organizacion.bloquearEscrituras();
        Responsable actual = responsables.bloquear(id).orElseThrow(() -> new ResponsableNoEncontradoException(id));
        if (!Objects.equals(ResponsableAdministrativo.from(actual).revision(), revision))
            throw new OrganizacionConflictoException("El responsable cambió en otra sesión. Recarga y revisa antes de guardar");
        // Los históricos sin departamento siguen consultables; no se inventa ni modifica su pertenencia.
        if (actual.departamentoId() == null)
            throw new OrganizacionConflictoException("Responsable histórico sin departamento: requiere revisión de su asociación antes de modificarlo");
        return actual;
    }
    private ResponsableAdministrativo guardar(Responsable responsable) {
        var resultado = ResponsableAdministrativo.from(responsables.guardar(responsable));
        eventos.publishEvent(new OrganizacionActualizada(responsable.departamentoId()));
        return resultado;
    }
    private DepartamentoAdministrativo departamento(Integer id) {
        return organizacion.buscarDepartamento(id).orElseThrow(() -> new DepartamentoNoEncontradoException(id));
    }
    private void disponible(DepartamentoAdministrativo d) {
        if (!d.activo()) throw new OrganizacionConflictoException("Reactiva el departamento antes de crear o activar responsables");
    }
    private String texto(String valor, int maximo, boolean obligatorio, String campo) {
        if (valor == null || valor.isBlank()) {
            if (obligatorio) throw new OrganizacionInvalidaException(campo + " obligatorio");
            return null;
        }
        if (valor.length() > maximo) throw new OrganizacionInvalidaException(campo + ": máximo " + maximo + " caracteres");
        return valor.strip();
    }
}
