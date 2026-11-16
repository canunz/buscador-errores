package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.Paso;
import cl.casol.backend.procedimiento.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerPasoService {
    private final ProcedimientoRepository procedimientos;
    private final PasoRepository pasos;

    public MantenerPasoService(ProcedimientoRepository procedimientos, PasoRepository pasos) {
        this.procedimientos = procedimientos; this.pasos = pasos;
    }

    public List<Paso> listar(Integer procedimientoId) {
        verificarProcedimiento(procedimientoId);
        return pasos.buscarPorProcedimientoOrdenados(procedimientoId);
    }

    @Transactional
    public Paso crear(Integer procedimientoId, Integer orden, String instruccion, boolean esCritico) {
        return crear(procedimientoId,orden,instruccion,esCritico,cl.casol.backend.procedimiento.domain.ModoEjecucionPaso.MANUAL,null);
    }
    @Transactional
    public Paso crear(Integer procedimientoId,Integer orden,String instruccion,boolean esCritico,
            cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modo,String mensaje) {
        return crear(procedimientoId,orden,instruccion,esCritico,modo,mensaje,null);
    }
    @Transactional public Paso crear(Integer procedimientoId,Integer orden,String instruccion,boolean esCritico,
            cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modo,String mensaje,cl.casol.backend.procedimiento.domain.ConfiguracionPaso config) {
        procedimientos.bloquear(procedimientoId);
        EdicionProcedimiento.exigirEditable(procedimientos.buscarPorId(procedimientoId).orElseThrow(() -> new ProcedimientoNoEncontradoException(procedimientoId)),procedimientos);
        if (pasos.existeOrden(procedimientoId, orden)) {
            throw new OrdenPasoDuplicadoException(procedimientoId, orden);
        }
        return pasos.guardar(new Paso(null, procedimientoId, orden, instruccion, esCritico,modo,mensaje,config));
    }

    @Transactional
    public Paso modificar(Integer procedimientoId, Integer pasoId, Integer orden,
            String instruccion, boolean esCritico) {
        return modificar(procedimientoId,pasoId,orden,instruccion,esCritico,null,null);
    }
    @Transactional
    public Paso modificar(Integer procedimientoId,Integer pasoId,Integer orden,String instruccion,boolean esCritico,
            cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modo,String mensaje) {
        return modificar(procedimientoId,pasoId,orden,instruccion,esCritico,modo,mensaje,null);
    }
    @Transactional public Paso modificar(Integer procedimientoId,Integer pasoId,Integer orden,String instruccion,boolean esCritico,
            cl.casol.backend.procedimiento.domain.ModoEjecucionPaso modo,String mensaje,cl.casol.backend.procedimiento.domain.ConfiguracionPaso config) {
        procedimientos.bloquear(procedimientoId);
        EdicionProcedimiento.exigirEditable(procedimientos.buscarPorId(procedimientoId).orElseThrow(() -> new ProcedimientoNoEncontradoException(procedimientoId)),procedimientos);
        Paso actual = pasos.buscarPorId(pasoId)
                .filter(p -> procedimientoId.equals(p.procedimientoId()))
                .orElseThrow(() -> new PasoNoEncontradoException(pasoId));
        if (pasos.existeOrdenExcluyendoPaso(procedimientoId, orden, pasoId)) {
            throw new OrdenPasoDuplicadoException(procedimientoId, orden);
        }
        var conservada=actual.configuracion();
        if(modo==null && esCritico && !actual.esCritico()) {
            conservada=new cl.casol.backend.procedimiento.domain.ConfiguracionPaso(conservada.destinatarios(),
                conservada.usarDestinatariosSucursal(),conservada.mensaje(),conservada.evidenciasRequeridas(),true,
                conservada.recordatoriosHoras(),conservada.plazoRespuestaHoras(),conservada.condicionFinalizacion(),conservada.accionAutomatica(),conservada.asunto());
        }
        return pasos.guardar(new Paso(actual.id(), procedimientoId, orden, instruccion, esCritico,
                modo==null?actual.modoEjecucion():modo,modo==null?actual.mensajeEvidencia():mensaje,modo==null?conservada:config));
    }

    @Transactional public void eliminar(Integer procedimientoId,Integer pasoId){procedimientos.bloquear(procedimientoId);EdicionProcedimiento.exigirEditable(procedimientos.buscarPorId(procedimientoId).orElseThrow(()->new ProcedimientoNoEncontradoException(procedimientoId)),procedimientos);pasos.buscarPorId(pasoId).filter(p->procedimientoId.equals(p.procedimientoId())).orElseThrow(()->new PasoNoEncontradoException(pasoId));pasos.eliminar(pasoId);var restantes=pasos.buscarPorProcedimientoOrdenados(procedimientoId);if(!restantes.isEmpty())pasos.reordenar(restantes);}
    @Transactional public List<Paso> mover(Integer id,Integer pasoId,boolean arriba,List<Integer> esperado){
        procedimientos.bloquear(id);EdicionProcedimiento.exigirEditable(procedimientos.buscarPorId(id).orElseThrow(()->new ProcedimientoNoEncontradoException(id)),procedimientos);
        var lista=new java.util.ArrayList<>(pasos.buscarPorProcedimientoOrdenados(id));
        if(!lista.stream().map(Paso::id).toList().equals(esperado))throw new cl.casol.backend.ejecucion.domain.exception.ConflictoEjecucionException("El orden cambió en otra sesión. Actualiza antes de mover");
        int indice=-1;for(int i=0;i<lista.size();i++)if(lista.get(i).id().equals(pasoId))indice=i;
        if(indice<0)throw new PasoNoEncontradoException(pasoId);int destino=indice+(arriba?-1:1);
        if(destino<0 || destino>=lista.size())return lista;
        java.util.Collections.swap(lista,indice,destino);pasos.reordenar(lista);return pasos.buscarPorProcedimientoOrdenados(id);
    }
    private void verificarProcedimiento(Integer id) {
        procedimientos.buscarPorId(id).orElseThrow(() -> new ProcedimientoNoEncontradoException(id));
    }
}
