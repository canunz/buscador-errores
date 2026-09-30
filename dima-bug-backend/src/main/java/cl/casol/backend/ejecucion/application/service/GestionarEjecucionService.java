package cl.casol.backend.ejecucion.application.service;

import cl.casol.backend.ejecucion.application.port.out.*;
import cl.casol.backend.ejecucion.domain.*;
import cl.casol.backend.ejecucion.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.UsuarioNoEncontradoException;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.ProcedimientoNoEncontradoException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class GestionarEjecucionService {
    private final EjecucionRepository ejecuciones;
    private final EjecucionPasoRepository ejecucionPasos;
    private final ProcedimientoRepository procedimientos;
    private final PasoRepository pasos;
    private final UsuarioRepository usuarios;

    public GestionarEjecucionService(EjecucionRepository ejecuciones, EjecucionPasoRepository ejecucionPasos,
            ProcedimientoRepository procedimientos, PasoRepository pasos, UsuarioRepository usuarios) {
        this.ejecuciones=ejecuciones; this.ejecucionPasos=ejecucionPasos;
        this.procedimientos=procedimientos; this.pasos=pasos; this.usuarios=usuarios;
    }

    @Transactional
    public Ejecucion iniciar(Integer procedimientoId, String email) {
        Procedimiento procedimiento = procedimientos.buscarPorId(procedimientoId)
                .orElseThrow(() -> new ProcedimientoNoEncontradoException(procedimientoId));
        if (procedimiento.estado() != EstadoProcedimiento.PUBLICADO) {
            throw new ReglaEjecucionException("Solo se puede ejecutar un procedimiento PUBLICADO");
        }
        List<Paso> actuales = pasos.buscarPorProcedimientoOrdenados(procedimientoId);
        if (actuales.isEmpty()) throw new ReglaEjecucionException("No se puede ejecutar un procedimiento sin pasos");
        Usuario usuario = usuario(email);
        Ejecucion creada = ejecuciones.guardar(new Ejecucion(null, procedimientoId, usuario,
                LocalDateTime.now(), null, EstadoEjecucion.EN_CURSO, null));
        ejecucionPasos.guardarTodos(actuales.stream().map(p -> new EjecucionPaso(null, creada.id(),
                p.id(), false, null, null)).toList());
        return creada;
    }

    public Ejecucion buscar(Integer id, String email) {
        Usuario usuario = usuario(email);
        Ejecucion ejecucion = obtener(id);
        autorizar(ejecucion, usuario);
        return ejecucion;
    }

    public List<Ejecucion> listar(String email) {
        Usuario usuario = usuario(email);
        return esAdministrador(usuario) ? ejecuciones.buscarTodas() : ejecuciones.buscarPorUsuario(usuario.getId());
    }

    public List<DetalleEjecucionPaso> listarPasos(Integer id, String email) {
        buscar(id, email);
        Map<Integer, Paso> definiciones = new HashMap<>();
        return ejecucionPasos.buscarPorEjecucion(id).stream().map(ep -> {
            Paso p = definiciones.computeIfAbsent(ep.pasoId(), pasoId -> pasos.buscarPorId(pasoId)
                    .orElseThrow(() -> new EjecucionPasoNoEncontradoException(ep.id())));
            return new DetalleEjecucionPaso(ep, p.orden(), p.instruccion(), p.esCritico());
        }).sorted(Comparator.comparing(DetalleEjecucionPaso::orden)).toList();
    }

    @Transactional
    public DetalleEjecucionPaso actualizarPaso(Integer ejecucionId, Integer ejecucionPasoId,
            Boolean cumplido, String observacion, String email) {
        Usuario usuario = usuario(email);
        Ejecucion ejecucion = obtener(ejecucionId);
        autorizar(ejecucion, usuario); exigirEnCurso(ejecucion);
        EjecucionPaso actual = ejecucionPasos.buscarPorId(ejecucionPasoId)
                .filter(p -> ejecucionId.equals(p.ejecucionId()))
                .orElseThrow(() -> new EjecucionPasoNoEncontradoException(ejecucionPasoId));
        boolean nuevoCumplido = cumplido == null ? actual.cumplido() : cumplido;
        LocalDateTime fecha = nuevoCumplido ? (actual.cumplido() && actual.fecha()!=null ? actual.fecha() : LocalDateTime.now()) : null;
        EjecucionPaso guardado = ejecucionPasos.guardar(new EjecucionPaso(actual.id(), ejecucionId, actual.pasoId(),
                nuevoCumplido, observacion, fecha));
        Paso definicion = pasos.buscarPorId(guardado.pasoId())
                .orElseThrow(() -> new EjecucionPasoNoEncontradoException(guardado.id()));
        return new DetalleEjecucionPaso(guardado, definicion.orden(), definicion.instruccion(), definicion.esCritico());
    }

    @Transactional
    public Ejecucion completar(Integer id, String email) {
        Usuario usuario=usuario(email); Ejecucion actual=obtener(id); autorizar(actual,usuario); exigirEnCurso(actual);
        if (ejecucionPasos.buscarPorEjecucion(id).stream().anyMatch(p -> !p.cumplido()))
            throw new ReglaEjecucionException("Todos los pasos deben estar cumplidos para completar la ejecucion");
        return cerrar(actual, EstadoEjecucion.COMPLETADA, actual.observaciones());
    }

    @Transactional
    public Ejecucion cancelar(Integer id, String observaciones, String email) {
        Usuario usuario=usuario(email); Ejecucion actual=obtener(id); autorizar(actual,usuario); exigirEnCurso(actual);
        return cerrar(actual, EstadoEjecucion.CANCELADA,
                observaciones == null ? actual.observaciones() : observaciones);
    }

    private Ejecucion cerrar(Ejecucion e, EstadoEjecucion estado, String observaciones) {
        return ejecuciones.guardar(new Ejecucion(e.id(),e.procedimientoId(),e.usuario(),e.fechaInicio(),
                LocalDateTime.now(),estado,observaciones));
    }
    private Ejecucion obtener(Integer id) { return ejecuciones.buscarPorId(id)
            .orElseThrow(() -> new EjecucionNoEncontradaException(id)); }
    private Usuario usuario(String email) { return usuarios.buscarPorEmail(email)
            .orElseThrow(UsuarioNoEncontradoException::new); }
    private void exigirEnCurso(Ejecucion e) { if(e.estado()!=EstadoEjecucion.EN_CURSO)
        throw new ReglaEjecucionException("La ejecucion debe estar EN_CURSO"); }
    private void autorizar(Ejecucion e, Usuario u) { if(!esAdministrador(u) && !u.getId().equals(e.usuario().getId()))
        throw new AccessDeniedException("No puede acceder a una ejecucion de otro usuario"); }
    private boolean esAdministrador(Usuario u) { return "ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre().trim()); }
}
