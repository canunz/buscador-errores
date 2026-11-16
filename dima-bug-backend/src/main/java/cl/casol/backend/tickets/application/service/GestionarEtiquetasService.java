package cl.casol.backend.tickets.application.service;

import cl.casol.backend.tickets.application.port.in.GestionarEtiquetasUseCase;
import cl.casol.backend.tickets.application.port.out.EtiquetaRepositoryPort;
import cl.casol.backend.tickets.domain.ActorTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.NivelEtiqueta;
import cl.casol.backend.tickets.domain.exception.EtiquetaDuplicadaException;
import cl.casol.backend.tickets.domain.exception.EtiquetaNoEncontradaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GestionarEtiquetasService implements GestionarEtiquetasUseCase {
    private final EtiquetaRepositoryPort etiquetas;
    private final ContextoTicketService contexto;

    public GestionarEtiquetasService(EtiquetaRepositoryPort etiquetas, ContextoTicketService contexto) {
        this.etiquetas = etiquetas;
        this.contexto = contexto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Etiqueta> listar(boolean incluirInactivas, String emailAutenticado) {
        ActorTicket actor = contexto.actor(emailAutenticado);
        return etiquetas.listar(incluirInactivas && actor.esSoporte());
    }

    @Override
    public Etiqueta crear(String nombre, String color, NivelEtiqueta nivel, String emailAutenticado) {
        contexto.exigirSoporte(contexto.actor(emailAutenticado), "crear etiquetas");
        Etiqueta nueva = Etiqueta.nueva(nombre, color, nivel);
        if (etiquetas.existeNombre(nueva.getNombre(), null)) throw new EtiquetaDuplicadaException(nueva.getNombre());
        return etiquetas.guardar(nueva);
    }

    @Override
    public Etiqueta editar(Long etiquetaId, String nombre, String color, NivelEtiqueta nivel, String emailAutenticado) {
        contexto.exigirSoporte(contexto.actor(emailAutenticado), "editar etiquetas");
        Etiqueta etiqueta = buscar(etiquetaId);
        etiqueta.editar(nombre, color, nivel);
        if (etiquetas.existeNombre(etiqueta.getNombre(), etiquetaId)) {
            throw new EtiquetaDuplicadaException(etiqueta.getNombre());
        }
        return etiquetas.guardar(etiqueta);
    }

    @Override
    public Etiqueta cambiarEstado(Long etiquetaId, boolean activa, String emailAutenticado) {
        contexto.exigirSoporte(contexto.actor(emailAutenticado), "activar o desactivar etiquetas");
        Etiqueta etiqueta = buscar(etiquetaId);
        etiqueta.cambiarEstado(activa);
        return etiquetas.guardar(etiqueta);
    }

    private Etiqueta buscar(Long etiquetaId) {
        return etiquetas.buscarPorId(etiquetaId).orElseThrow(() -> new EtiquetaNoEncontradaException(etiquetaId));
    }
}
