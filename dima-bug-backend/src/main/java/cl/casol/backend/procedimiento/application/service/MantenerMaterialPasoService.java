package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.MaterialApoyoRepository;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.MaterialApoyoNoEncontradoException;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerMaterialPasoService {
    private final ProcedimientoRepository procedimientos;
    private final PasoRepository pasos;
    private final MaterialApoyoRepository materiales;
    public MantenerMaterialPasoService(ProcedimientoRepository procedimientos, PasoRepository pasos,
            MaterialApoyoRepository materiales) {
        this.procedimientos=procedimientos; this.pasos=pasos; this.materiales=materiales;
    }
    public List<MaterialApoyo> listar(Integer procedimientoId,Integer pasoId){
        verificarPaso(procedimientoId,pasoId); return materiales.buscarPorPaso(pasoId);
    }
    @Transactional
    public MaterialApoyo crear(Integer procedimientoId,Integer pasoId,String nombre,TipoMaterial tipo,String url){
        verificarPaso(procedimientoId,pasoId);
        return materiales.guardar(new MaterialApoyo(null,null,pasoId,nombre,tipo,url));
    }
    @Transactional
    public MaterialApoyo modificar(Integer procedimientoId,Integer pasoId,Integer materialId,
            String nombre,TipoMaterial tipo,String url){
        verificarPaso(procedimientoId,pasoId);
        MaterialApoyo actual=materiales.buscarPorId(materialId)
                .filter(m -> m.conocimientoId()==null && pasoId.equals(m.pasoId()))
                .orElseThrow(() -> new MaterialApoyoNoEncontradoException(materialId));
        return materiales.guardar(new MaterialApoyo(actual.id(),null,pasoId,nombre,tipo,url));
    }
    private void verificarPaso(Integer procedimientoId,Integer pasoId){
        procedimientos.buscarPorId(procedimientoId)
                .orElseThrow(() -> new ProcedimientoNoEncontradoException(procedimientoId));
        pasos.buscarPorId(pasoId).filter(p -> procedimientoId.equals(p.procedimientoId()))
                .orElseThrow(() -> new PasoNoEncontradoException(pasoId));
    }
}
