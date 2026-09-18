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

    public MantenerMaterialApoyoService(ConocimientoRepository conocimientos, MaterialApoyoRepository materiales) {
        this.conocimientos = conocimientos;
        this.materiales = materiales;
    }

    public List<MaterialApoyo> listar(Integer conocimientoId) {
        verificarConocimiento(conocimientoId);
        return materiales.buscarDirectosPorConocimiento(conocimientoId);
    }

    @Transactional
    public MaterialApoyo crear(Integer conocimientoId, String nombre, TipoMaterial tipo, String url) {
        verificarConocimiento(conocimientoId);
        return materiales.guardar(new MaterialApoyo(null, conocimientoId, null, nombre, tipo, url));
    }

    @Transactional
    public MaterialApoyo modificar(Integer conocimientoId, Integer materialId,
            String nombre, TipoMaterial tipo, String url) {
        verificarConocimiento(conocimientoId);
        MaterialApoyo actual = materiales.buscarPorId(materialId)
                .filter(material -> conocimientoId.equals(material.conocimientoId()))
                .filter(material -> material.pasoId() == null)
                .orElseThrow(() -> new MaterialApoyoNoEncontradoException(materialId));
        return materiales.guardar(new MaterialApoyo(actual.id(), conocimientoId, null, nombre, tipo, url));
    }

    private void verificarConocimiento(Integer conocimientoId) {
        conocimientos.buscarPorId(conocimientoId)
                .orElseThrow(() -> new ConocimientoNoEncontradoException(conocimientoId));
    }
}
