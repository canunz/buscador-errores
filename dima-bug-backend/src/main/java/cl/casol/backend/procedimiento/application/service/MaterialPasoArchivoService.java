package cl.casol.backend.procedimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.MaterialApoyoRepository;
import cl.casol.backend.conocimiento.application.service.ArchivoMaterialService;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.MaterialApoyoNoEncontradoException;
import cl.casol.backend.procedimiento.application.port.out.*;
import cl.casol.backend.procedimiento.domain.exception.*;
import cl.casol.backend.shared.application.archivo.*;
import org.springframework.stereotype.Service;

@Service
public class MaterialPasoArchivoService {
    private final ProcedimientoRepository procedimientos;
    private final PasoRepository pasos;
    private final MaterialApoyoRepository materiales;
    private final MantenerMaterialPasoService mantener;
    private final ArchivoMaterialService archivos;

    public MaterialPasoArchivoService(ProcedimientoRepository procedimientos, PasoRepository pasos,
            MaterialApoyoRepository materiales, MantenerMaterialPasoService mantener, ArchivoMaterialService archivos) {
        this.procedimientos = procedimientos; this.pasos = pasos; this.materiales = materiales;
        this.mantener = mantener; this.archivos = archivos;
    }
    public MaterialApoyo subir(Integer procedimientoId, Integer pasoId, String nombre, TipoMaterial tipo, ArchivoSubido archivo) {
        verificar(procedimientoId, pasoId);
        return archivos.crear(nombre, tipo, archivo, referencia -> mantener.crear(procedimientoId, pasoId, nombre, tipo, referencia));
    }
    public ArchivoDescarga descargar(Integer procedimientoId, Integer pasoId, Integer materialId) {
        verificar(procedimientoId, pasoId);
        MaterialApoyo material = materiales.buscarPorId(materialId)
                .filter(m -> m.conocimientoId() == null && pasoId.equals(m.pasoId()))
                .orElseThrow(() -> new MaterialApoyoNoEncontradoException(materialId));
        return archivos.descargar(material);
    }
    private void verificar(Integer procedimientoId, Integer pasoId) {
        procedimientos.buscarPorId(procedimientoId).orElseThrow(() -> new ProcedimientoNoEncontradoException(procedimientoId));
        pasos.buscarPorId(pasoId).filter(p -> procedimientoId.equals(p.procedimientoId()))
                .orElseThrow(() -> new PasoNoEncontradoException(pasoId));
    }
}
