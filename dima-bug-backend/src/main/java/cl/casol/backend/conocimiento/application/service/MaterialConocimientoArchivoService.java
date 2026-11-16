package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import cl.casol.backend.shared.application.archivo.*;
import org.springframework.stereotype.Service;

@Service
public class MaterialConocimientoArchivoService {
    private final ConocimientoRepository conocimientos;
    private final MaterialApoyoRepository materiales;
    private final MantenerMaterialApoyoService mantener;
    private final ArchivoMaterialService archivos;
    private final IndexarConocimientoService indexador;

    public MaterialConocimientoArchivoService(ConocimientoRepository conocimientos, MaterialApoyoRepository materiales,
            MantenerMaterialApoyoService mantener, ArchivoMaterialService archivos, IndexarConocimientoService indexador) {
        this.conocimientos = conocimientos; this.materiales = materiales;
        this.mantener = mantener; this.archivos = archivos;
        this.indexador = indexador;
    }
    public void eliminar(Integer conocimientoId, Integer materialId) {
        archivos.eliminar(() -> {
            verificar(conocimientoId);
            return materiales.buscarPorId(materialId)
                    .filter(m -> conocimientoId.equals(m.conocimientoId()) && m.pasoId() == null)
                    .orElseThrow(() -> new MaterialApoyoNoEncontradoException(materialId));
        }, material -> {
            materiales.eliminar(material.id());
            indexador.indexar(conocimientoId);
        });
    }
    public MaterialApoyo subir(Integer conocimientoId, String nombre, TipoMaterial tipo, ArchivoSubido archivo) {
        verificar(conocimientoId);
        return archivos.crear(nombre, tipo, archivo, referencia -> mantener.crear(conocimientoId, nombre, tipo, referencia));
    }
    public ArchivoDescarga descargar(Integer conocimientoId, Integer materialId) {
        verificar(conocimientoId);
        MaterialApoyo material = materiales.buscarPorId(materialId)
                .filter(m -> conocimientoId.equals(m.conocimientoId()) && m.pasoId() == null)
                .orElseThrow(() -> new MaterialApoyoNoEncontradoException(materialId));
        return archivos.descargar(material);
    }
    private void verificar(Integer id) {
        conocimientos.buscarPorId(id).orElseThrow(() -> new ConocimientoNoEncontradoException(id));
    }
}
