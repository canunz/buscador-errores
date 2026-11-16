package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.ConocimientoRepository;
import cl.casol.backend.conocimiento.application.port.out.MaterialApoyoRepository;
import cl.casol.backend.conocimiento.domain.MaterialApoyo;
import cl.casol.backend.conocimiento.domain.TipoMaterial;
import cl.casol.backend.conocimiento.domain.exception.ConocimientoNoEncontradoException;
import cl.casol.backend.conocimiento.domain.exception.MaterialApoyoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MantenerMaterialApoyoService {
    private final ConocimientoRepository conocimientos;
    private final MaterialApoyoRepository materiales;
    private final IndexarConocimientoService indexador;

    public MantenerMaterialApoyoService(ConocimientoRepository conocimientos, MaterialApoyoRepository materiales,
            IndexarConocimientoService indexador) {
        this.conocimientos = conocimientos;
        this.materiales = materiales;
        this.indexador = indexador;
    }

    public List<MaterialApoyo> listar(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return materiales.buscarDirectosPorConocimiento(conocimientoId);
    }

    @Transactional
    public MaterialApoyo crear(Integer conocimientoId, String nombre, TipoMaterial tipo, String url) {
        verificarConocimiento(conocimientoId);
        MaterialApoyo guardado = materiales.guardar(new MaterialApoyo(null, conocimientoId, null, nombre, tipo, url));
        indexador.indexar(conocimientoId);
        return guardado;
    }

    @Transactional
    public MaterialApoyo modificar(Integer conocimientoId, Integer materialId,
            String nombre, TipoMaterial tipo, String url) {
        verificarConocimiento(conocimientoId);
        MaterialApoyo actual = materiales.buscarPorId(materialId)
                .filter(material -> conocimientoId.equals(material.conocimientoId()))
                .filter(material -> material.pasoId() == null)
                .orElseThrow(() -> new MaterialApoyoNoEncontradoException(materialId));
        MaterialApoyo guardado = materiales.guardar(
                new MaterialApoyo(actual.id(), conocimientoId, null, nombre, tipo, url));
        indexador.indexar(conocimientoId);
        return guardado;
    }

    private void verificarConocimiento(Integer conocimientoId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
    }
}
