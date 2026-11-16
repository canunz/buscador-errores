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
@Transactional(readOnly = true, timeout = 8)
public class GestionarEjecucionService {
    private final EjecucionRepository ejecuciones;
    private final EjecucionPasoRepository ejecucionPasos;
    private final ProcedimientoRepository procedimientos;
    private final PasoRepository pasos;
    private final UsuarioRepository usuarios;
    private final AutomatizacionEjecucionPort automatizacion;

    public GestionarEjecucionService(EjecucionRepository ejecuciones, EjecucionPasoRepository ejecucionPasos,
            ProcedimientoRepository procedimientos, PasoRepository pasos, UsuarioRepository usuarios) {
        this(ejecuciones,ejecucionPasos,procedimientos,pasos,usuarios,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public GestionarEjecucionService(EjecucionRepository ejecuciones,EjecucionPasoRepository ejecucionPasos,
            ProcedimientoRepository procedimientos,PasoRepository pasos,UsuarioRepository usuarios,AutomatizacionEjecucionPort automatizacion) {
        this.ejecuciones=ejecuciones; this.ejecucionPasos=ejecucionPasos;
        this.procedimientos=procedimientos; this.pasos=pasos; this.usuarios=usuarios;
        this.automatizacion=automatizacion;
    }

    @Transactional
    public Ejecucion iniciar(Integer procedimientoId, String email) {
        return iniciar(procedimientoId,null,null,email);
    }
    @Transactional
    public Ejecucion iniciar(Integer procedimientoId,Integer sucursalId,String claveInicio,String email) {
        return iniciar(procedimientoId,sucursalId,claveInicio,java.util.List.of(),email);
    }
    @Transactional public Ejecucion iniciar(Integer procedimientoId,Integer sucursalId,String claveInicio,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> adicionales,String email) {
        procedimientos.bloquear(procedimientoId);
        Procedimiento procedimiento = procedimientos.buscarPorId(procedimientoId)
                .orElseThrow(() -> new ProcedimientoNoEncontradoException(procedimientoId));
        if (procedimiento.estado() != EstadoProcedimiento.PUBLICADO) {
            throw new ReglaEjecucionException("Solo se puede ejecutar un procedimiento PUBLICADO");
        }
        List<Paso> actuales = pasos.buscarPorProcedimientoOrdenados(procedimientoId);
        if (actuales.isEmpty()) throw new ReglaEjecucionException("No se puede ejecutar un procedimiento sin pasos");
        Usuario usuario = usuario(email);
        boolean externo=actuales.stream().anyMatch(p->p.modoEjecucion().tieneAccion());
        if(externo && sucursalId!=null) {
            if(sucursalId==null || automatizacion==null)throw new ReglaEjecucionException("Selecciona la sucursal para solicitar evidencias");
            var repetida=automatizacion.buscarInicio(usuario.getId(),claveInicio,procedimientoId,sucursalId,adicionales);
            if(repetida.isPresent())return buscar(repetida.get(),email);
            automatizacion.validarInicio(sucursalId);
        }
        Ejecucion creada = ejecuciones.guardar(new Ejecucion(null, procedimientoId, usuario,
                LocalDateTime.now(), null, EstadoEjecucion.EN_CURSO, null));
        var instancias=ejecucionPasos.guardarTodos(actuales.stream().map(p -> new EjecucionPaso(null, creada.id(),
                p.id(), false, null, null, ContratoPaso.capturar(p), EstadoEjecucionPaso.PENDIENTE, 0)).toList());
        if(externo && sucursalId!=null)automatizacion.iniciar(creada,instancias,actuales,sucursalId,claveInicio,adicionales);
        else if(automatizacion!=null)automatizacion.avanzar(creada);
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
            if (ep.contrato() != null) return detalle(ep);
            Paso p = definiciones.computeIfAbsent(ep.pasoId(), pasoId -> pasos.buscarPorId(pasoId)
                    .orElseThrow(() -> new EjecucionPasoNoEncontradoException(ep.id())));
            return new DetalleEjecucionPaso(ep, p.orden(), p.instruccion(), p.esCritico());
        }).sorted(Comparator.comparing(DetalleEjecucionPaso::orden)).toList();
    }

    @Transactional
    public DetalleEjecucionPaso actualizarPaso(Integer ejecucionId, Integer ejecucionPasoId,
            Boolean cumplido, String observacion, String email) {
        return actualizarPaso(ejecucionId, ejecucionPasoId, cumplido, observacion, null, email);
    }

    @Transactional
    public DetalleEjecucionPaso actualizarPaso(Integer ejecucionId, Integer ejecucionPasoId,
            Boolean cumplido, String observacion, Long versionEsperada, String email) {
        Usuario usuario = usuario(email);
        ejecuciones.bloquear(ejecucionId);
        Ejecucion ejecucion = obtener(ejecucionId);
        autorizar(ejecucion, usuario); exigirEnCurso(ejecucion);
        EjecucionPaso actual = ejecucionPasos.buscarPorId(ejecucionPasoId)
                .filter(p -> ejecucionId.equals(p.ejecucionId()))
                .orElseThrow(() -> new EjecucionPasoNoEncontradoException(ejecucionPasoId));
        if (versionEsperada != null && versionEsperada != actual.version())
            throw new ConflictoEjecucionException("El paso cambió en otra sesión. Recarga antes de guardar");
        if (actual.contrato() != null && actual.contrato().modo() != ModoEjecucionPaso.MANUAL && cumplido != null)
            throw new ConflictoEjecucionException("El paso requiere su flujo de validación; no admite marcado manual");
        boolean flujoExterno=false;
        if(actual.contrato()!=null && cumplido!=null) {
            var todos=ejecucionPasos.buscarPorEjecucion(ejecucionId);
            flujoExterno=todos.stream().anyMatch(p->p.contrato()!=null && p.contrato().modo().tieneAccion());
            if(flujoExterno && cumplido && todos.stream().anyMatch(p->p.contrato()!=null && p.contrato().orden()<actual.contrato().orden() && !p.cumplido()))
                throw new ConflictoEjecucionException("Primero completa y valida los pasos anteriores");
            if(flujoExterno && !cumplido && actual.cumplido())throw new ConflictoEjecucionException("No se puede reabrir una dependencia validada; cancela e inicia una ejecución nueva");
        }
        boolean nuevoCumplido = cumplido == null ? actual.cumplido() : cumplido;
        EjecucionPaso guardado;
        if (cumplido == null) {
            guardado = ejecucionPasos.guardar(new EjecucionPaso(actual.id(), ejecucionId, actual.pasoId(),
                    actual.cumplido(), observacion, actual.fecha(), actual.contrato(), actual.estado(), actual.version()+1));
        } else {
            guardado = ejecucionPasos.guardar(actual.actualizar(nuevoCumplido, observacion, LocalDateTime.now()));
        }
        if(flujoExterno)automatizacion.avanzar(ejecucion);
        if (guardado.contrato() != null) return detalle(guardado);
        Paso definicion = pasos.buscarPorId(guardado.pasoId())
                .orElseThrow(() -> new EjecucionPasoNoEncontradoException(guardado.id()));
        return new DetalleEjecucionPaso(guardado, definicion.orden(), definicion.instruccion(), definicion.esCritico());
    }

    @Transactional
    public Ejecucion completar(Integer id, String email) {
        ejecuciones.bloquear(id);
        Usuario usuario=usuario(email); Ejecucion actual=obtener(id); autorizar(actual,usuario); exigirEnCurso(actual);
        if (ejecucionPasos.buscarPorEjecucion(id).stream().anyMatch(p -> !p.cumplido()))
            throw new ReglaEjecucionException("Todos los pasos deben estar cumplidos para completar la ejecucion");
        return cerrar(actual, EstadoEjecucion.COMPLETADA, actual.observaciones());
    }
    @Transactional public DetalleEjecucionPaso siguiente(Integer id,Integer pasoId,long version,boolean confirmaCritico,String email){
        return siguiente(id,pasoId,version,confirmaCritico,null,email);
    }
    @Transactional public DetalleEjecucionPaso siguiente(Integer id,Integer pasoId,long version,boolean confirmaCritico,String observacion,String email){
        ejecuciones.bloquear(id);var usuario=usuario(email);var e=obtener(id);autorizar(e,usuario);exigirEnCurso(e);
        var actual=ejecucionPasos.buscarPorId(pasoId).filter(p->p.ejecucionId().equals(id)).orElseThrow(()->new EjecucionPasoNoEncontradoException(pasoId));
        if(actual.version()!=version)throw new ConflictoEjecucionException("El paso cambió; actualiza el progreso");
        if(actual.cumplido())throw new ConflictoEjecucionException("El paso ya está completado");
        var detalle=listarPasos(id,email).stream().filter(p->p.ejecucionPaso().id().equals(pasoId)).findFirst().orElseThrow();
        if(listarPasos(id,email).stream().anyMatch(p->p.orden()<detalle.orden() && !p.ejecucionPaso().cumplido()))throw new ConflictoEjecucionException("Primero completa y valida los anteriores");
        if(actual.contrato()!=null && !actual.contrato().modo().esManual() && actual.contrato().modo()!=ModoEjecucionPaso.INFORMATIVO)throw new ConflictoEjecucionException("Este paso debe completar su propia automatización");
        if(detalle.esCritico() && !confirmaCritico)throw new ReglaEjecucionException("El paso crítico requiere confirmación explícita");
        var guardado=ejecucionPasos.guardar(actual.actualizar(true,observacion==null?actual.observacion():observacion,LocalDateTime.now()));
        if(automatizacion!=null){automatizacion.registrarManual(e,guardado,usuario.getId());automatizacion.avanzar(e);}
        return new DetalleEjecucionPaso(guardado,detalle.orden(),detalle.instruccion(),detalle.esCritico());
    }

    @Transactional
    public Ejecucion cancelar(Integer id, String observaciones, String email) {
        ejecuciones.bloquear(id);
        Usuario usuario=usuario(email); Ejecucion actual=obtener(id); autorizar(actual,usuario); exigirEnCurso(actual);
        if(automatizacion!=null)automatizacion.cancelar(actual,usuario.getId());
        return cerrar(actual, EstadoEjecucion.CANCELADA,
                observaciones == null ? actual.observaciones() : observaciones);
    }

    private Ejecucion cerrar(Ejecucion e, EstadoEjecucion estado, String observaciones) {
        return ejecuciones.guardar(new Ejecucion(e.id(),e.procedimientoId(),e.usuario(),e.fechaInicio(),
                LocalDateTime.now(),estado,observaciones));
    }
    private DetalleEjecucionPaso detalle(EjecucionPaso paso) {
        ContratoPaso c = paso.contrato();
        return new DetalleEjecucionPaso(paso, c.orden(), c.instruccion(), c.esCritico());
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
