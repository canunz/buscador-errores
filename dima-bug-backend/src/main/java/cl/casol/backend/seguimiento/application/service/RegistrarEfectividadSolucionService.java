package cl.casol.backend.seguimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.UsuarioNoEncontradoException;
import cl.casol.backend.seguimiento.application.port.out.ResultadoSolucionRepository;
import cl.casol.backend.seguimiento.domain.*;
import cl.casol.backend.seguimiento.domain.exception.EstadoConocimientoNoEvaluableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly=true)
public class RegistrarEfectividadSolucionService {
    private final ConocimientoRepository conocimientos;
    private final SolucionRepository soluciones;
    private final UsuarioRepository usuarios;
    private final ResultadoSolucionRepository resultados;

    public RegistrarEfectividadSolucionService(ConocimientoRepository conocimientos,
            SolucionRepository soluciones, UsuarioRepository usuarios, ResultadoSolucionRepository resultados) {
        this.conocimientos=conocimientos; this.soluciones=soluciones;
        this.usuarios=usuarios; this.resultados=resultados;
    }

    @Transactional
    public DetalleResultadoSolucion registrar(Integer conocimientoId, Integer solucionId,
            boolean funciono, String comentario, String emailAutenticado) {
        validarPertenencia(conocimientoId,solucionId,true);
        Usuario usuario=usuarios.buscarPorEmail(emailAutenticado)
                .orElseThrow(UsuarioNoEncontradoException::new);
        ResultadoSolucion guardado=resultados.guardar(new ResultadoSolucion(null,solucionId,
                usuario.getId(),funciono,comentario,LocalDateTime.now()));
        return new DetalleResultadoSolucion(guardado,usuario);
    }

    public List<DetalleResultadoSolucion> listar(Integer conocimientoId,Integer solucionId) {
        validarPertenencia(conocimientoId,solucionId,false);
        return resultados.buscarPorSolucionOrdenados(solucionId).stream()
                .map(r -> new DetalleResultadoSolucion(r,usuarios.buscarPorId(r.usuarioId())
                        .orElseThrow(UsuarioNoEncontradoException::new))).toList();
    }

    public EfectividadSolucion obtenerEfectividad(Integer conocimientoId,Integer solucionId) {
        validarPertenencia(conocimientoId,solucionId,false);
        return resultados.calcularEfectividad(solucionId);
    }

    private void validarPertenencia(Integer conocimientoId,Integer solucionId,boolean exigirPublicado) {
        Conocimiento conocimiento=conocimientos.buscarPorIdIncluidoEliminado(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
        soluciones.buscarPorId(solucionId).filter(s -> conocimientoId.equals(s.conocimientoId()))
                .orElseThrow(() -> new SolucionNoEncontradaException(solucionId));
        if(exigirPublicado && conocimiento.getEstado()!=EstadoConocimiento.PUBLICADO)
            throw new EstadoConocimientoNoEvaluableException();
    }
}
