package cl.casol.backend.procedimiento.application.service;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service
public class PrepararVersionEvidenciaService {
    private final ProcedimientoRepository procedimientos;private final PasoRepository pasos;private final UsuarioRepository usuarios;
    public PrepararVersionEvidenciaService(ProcedimientoRepository procedimientos,PasoRepository pasos,UsuarioRepository usuarios){this.procedimientos=procedimientos;this.pasos=pasos;this.usuarios=usuarios;}
    /** Crea un BORRADOR revisable. No modifica el PUBLICADO ni sus ejecuciones o archivos. */
    @Transactional public Procedimiento crear(Integer originalId,String email) {
        var actor=usuarios.buscarPorEmail(email).filter(u->u.isActivo() && u.getRol()!=null && "ADMINISTRADOR".equalsIgnoreCase(u.getRol().getNombre().strip())).orElseThrow(()->new AccessDeniedException("Solo un administrador puede preparar una versión automatizada"));
        procedimientos.bloquear(originalId);var original=procedimientos.buscarPorId(originalId).orElseThrow(()->new ProcedimientoNoEncontradoException(originalId));
        var actuales=pasos.buscarPorProcedimientoOrdenados(originalId);
        if(original.estado()!=EstadoProcedimiento.PUBLICADO || actuales.isEmpty())throw new EstadoProcedimientoInvalidoException("Selecciona un PUBLICADO con pasos para preparar una versión");
        String sufijo=" · evidencias";String nombre=original.nombre();if(nombre.length()>150-sufijo.length())nombre=nombre.substring(0,150-sufijo.length());
        var nuevo=procedimientos.guardar(new Procedimiento(null,nombre+sufijo,original.descripcion(),EstadoProcedimiento.BORRADOR,actor,LocalDateTime.now(),null,null,new DatosEditorialesProcedimiento(original.datos().sistemaId(),original.datos().moduloId(),original.datos().informacionComplementaria(),null,1,true),original.hardwareId()));
        for(int i=0;i<actuales.size();i++){var p=actuales.get(i);pasos.guardar(new Paso(null,nuevo.id(),p.orden(),p.instruccion(),i==0 || p.esCritico(),i==0?ModoEjecucionPaso.VALIDACION_EXTERNA:p.modoEjecucion(),p.mensajeEvidencia()));}
        return nuevo;
    }
}
