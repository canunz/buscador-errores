package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.DepartamentoRepository;
import cl.casol.backend.identidad.application.port.out.ResponsableRepository;
import cl.casol.backend.identidad.domain.Departamento;
import cl.casol.backend.identidad.domain.Responsable;
import cl.casol.backend.identidad.domain.exception.DepartamentoNoEncontradoException;
import cl.casol.backend.identidad.domain.exception.ResponsableNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GestionarAsignacionSolucionService {
    private final ConocimientoRepository conocimientos;
    private final SolucionRepository soluciones;
    private final SolucionAsignacionRepository asignaciones;
    private final DepartamentoRepository departamentos;
    private final ResponsableRepository responsables;
    private final IndexarConocimientoService indexador;

    public GestionarAsignacionSolucionService(ConocimientoRepository conocimientos, SolucionRepository soluciones,
            SolucionAsignacionRepository asignaciones, DepartamentoRepository departamentos,
            ResponsableRepository responsables, IndexarConocimientoService indexador) {
        this.conocimientos = conocimientos;
        this.soluciones = soluciones;
        this.asignaciones = asignaciones;
        this.departamentos = departamentos;
        this.responsables = responsables;
        this.indexador = indexador;
    }

    public List<SolucionAsignacion> listar(Integer conocimientoId, Integer solucionId) {
        validarPadres(conocimientoId, solucionId);
        return asignaciones.buscarPorSolucionOrdenadas(solucionId);
    }

    @Transactional
    public SolucionAsignacion crear(Integer conocimientoId, Integer solucionId, Integer departamentoId,
            Integer responsableId, Boolean principal) {
        validarPadres(conocimientoId, solucionId);
        Destinatarios destinatarios = validarDestinatarios(departamentoId, responsableId);
        SolucionAsignacion guardada = asignaciones.guardar(new SolucionAsignacion(null, solucionId,
                destinatarios.responsable(), destinatarios.departamento(), principal == null || principal));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    @Transactional
    public SolucionAsignacion modificar(Integer conocimientoId, Integer solucionId, Integer asignacionId,
            Integer departamentoId, Integer responsableId, Boolean principal) {
        validarPadres(conocimientoId, solucionId);
        SolucionAsignacion actual = asignaciones.buscarPorId(asignacionId)
                .filter(asignacion -> solucionId.equals(asignacion.solucionId()))
                .orElseThrow(() -> new AsignacionSolucionNoEncontradaException(asignacionId));
        Destinatarios destinatarios = validarDestinatarios(departamentoId, responsableId);
        SolucionAsignacion guardada = asignaciones.guardar(new SolucionAsignacion(actual.id(), solucionId,
                destinatarios.responsable(), destinatarios.departamento(), principal == null || principal));
        indexador.indexar(conocimientoId);
        return guardada;
    }

    private void validarPadres(Integer conocimientoId, Integer solucionId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
        soluciones.buscarPorId(solucionId)
                .filter(solucion -> conocimientoId.equals(solucion.conocimientoId()))
                .orElseThrow(() -> new SolucionNoEncontradaException(solucionId));
    }

    private Destinatarios validarDestinatarios(Integer departamentoId, Integer responsableId) {
        if (departamentoId == null && responsableId == null) {
            throw new AsignacionSolucionInvalidaException(
                    "Debe informar al menos departamentoId o responsableId");
        }
        Departamento departamento = departamentoId == null ? null : departamentos.buscarActivoPorId(departamentoId)
                .orElseThrow(() -> new DepartamentoNoEncontradoException(departamentoId));
        Responsable responsable = responsableId == null ? null : responsables.buscarActivoPorId(responsableId)
                .orElseThrow(() -> new ResponsableNoEncontradoException(responsableId));
        if (departamento != null && responsable != null
                && !departamento.id().equals(responsable.departamentoId())) {
            throw new AsignacionSolucionInvalidaException(
                    "El responsable no pertenece al departamento informado");
        }
        return new Destinatarios(departamento, responsable);
    }

    private record Destinatarios(Departamento departamento, Responsable responsable) { }
}
