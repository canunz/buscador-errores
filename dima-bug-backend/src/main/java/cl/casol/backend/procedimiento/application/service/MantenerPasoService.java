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
        verificarProcedimiento(procedimientoId);
        if (pasos.existeOrden(procedimientoId, orden)) {
            throw new OrdenPasoDuplicadoException(procedimientoId, orden);
        }
        return pasos.guardar(new Paso(null, procedimientoId, orden, instruccion, esCritico));
    }

    @Transactional
    public Paso modificar(Integer procedimientoId, Integer pasoId, Integer orden,
            String instruccion, boolean esCritico) {
        verificarProcedimiento(procedimientoId);
        Paso actual = pasos.buscarPorId(pasoId)
                .filter(p -> procedimientoId.equals(p.procedimientoId()))
                .orElseThrow(() -> new PasoNoEncontradoException(pasoId));
        if (pasos.existeOrdenExcluyendoPaso(procedimientoId, orden, pasoId)) {
            throw new OrdenPasoDuplicadoException(procedimientoId, orden);
        }
        return pasos.guardar(new Paso(actual.id(), procedimientoId, orden, instruccion, esCritico));
    }

    private void verificarProcedimiento(Integer id) {
        procedimientos.buscarPorId(id).orElseThrow(() -> new ProcedimientoNoEncontradoException(id));
    }
}
