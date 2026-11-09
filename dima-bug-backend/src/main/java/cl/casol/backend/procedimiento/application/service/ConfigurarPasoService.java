package cl.casol.backend.procedimiento.application.service;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ConfigurarPasoService {
    private final ProcedimientoRepository procedimientos;private final PasoRepository pasos;private final UsuarioRepository usuarios;
    public ConfigurarPasoService(ProcedimientoRepository procedimientos,PasoRepository pasos,UsuarioRepository usuarios){this.procedimientos=procedimientos;this.pasos=pasos;this.usuarios=usuarios;}
    @Transactional public Paso configurar(Integer procedimiento,Integer id,ModoEjecucionPaso modo,ConfiguracionPaso config,String revision,String email){
        var actor=usuarios.buscarPorEmail(email).filter(u->u.isActivo() && u.getRol()!=null && "ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre().strip())).orElseThrow(()->new AccessDeniedException("Usuario no autorizado"));
        procedimientos.bloquear(procedimiento);var p=procedimientos.buscarPorId(procedimiento).orElseThrow(()->new ProcedimientoNoEncontradoException(procedimiento));
        EdicionProcedimiento.exigirEditable(p,procedimientos);
        var actual=pasos.buscarPorId(id).filter(s->s.procedimientoId().equals(procedimiento)).orElseThrow(()->new PasoNoEncontradoException(id));
        if(!actual.revisionConfiguracion().equals(revision))throw new cl.casol.backend.ejecucion.domain.exception.ConflictoEjecucionException("La configuración cambió; recarga el paso antes de guardar");
        var nueva=new Paso(id,procedimiento,actual.orden(),actual.instruccion(),actual.esCritico(),modo,config==null?null:config.mensaje(),config);
        return pasos.configurar(nueva,actor.getId());
    }
}
