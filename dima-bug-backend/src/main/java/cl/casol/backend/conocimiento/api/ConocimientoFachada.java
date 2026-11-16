package cl.casol.backend.conocimiento.api;

import cl.casol.backend.conocimiento.application.service.BuscarConocimientoService;
import cl.casol.backend.conocimiento.application.service.MantenerConocimientoService;
import cl.casol.backend.conocimiento.application.service.MantenerSolucionService;
import cl.casol.backend.conocimiento.domain.Conocimiento;
import cl.casol.backend.conocimiento.domain.EstadoConocimiento;
import cl.casol.backend.conocimiento.domain.TipoSolucion;
import cl.casol.backend.conocimiento.infrastructure.persistence.repository.ConocimientoJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** API pública mínima del módulo de conocimiento: buscar publicados, consultar uno y crear un borrador. */
@Service
@Transactional(readOnly = true)
public class ConocimientoFachada {
    private static final int MAX_TITULO_SOLUCION = 150;

    private final BuscarConocimientoService buscador;
    private final MantenerConocimientoService mantenedor;
    private final MantenerSolucionService soluciones;
    private final ConocimientoJpaRepository jpa;

    public ConocimientoFachada(BuscarConocimientoService buscador, MantenerConocimientoService mantenedor,
            MantenerSolucionService soluciones, ConocimientoJpaRepository jpa) {
        this.buscador = buscador;
        this.mantenedor = mantenedor;
        this.soluciones = soluciones;
        this.jpa = jpa;
    }

    /** Conocimientos PUBLICADOS parecidos al texto, usando el buscador FULLTEXT existente. */
    public List<ConocimientoPublico> buscarPublicados(String texto, int limite) {
        return buscador.buscar(texto, null, null, null, null).stream()
                .limit(Math.max(limite, 0))
                .map(r -> new ConocimientoPublico(r.id(), r.titulo(), "PUBLICADO", r.hardwareNombre(),
                        r.sistemaNombre(), r.moduloNombre()))
                .toList();
    }

    /** Conocimiento existente que no esté eliminado. */
    public Optional<ConocimientoPublico> buscarVigente(Integer id) {
        if (id == null) return Optional.empty();
        return jpa.resumenPorId(id, EstadoConocimiento.ELIMINADO).stream().findFirst().map(fila -> new ConocimientoPublico(
                fila[0] instanceof Number numero ? numero.intValue() : Integer.valueOf(String.valueOf(fila[0])),
                fila[1] == null ? null : String.valueOf(fila[1]),
                fila[2] == null ? null : String.valueOf(fila[2]), null, null, null));
    }

    /** Crea un conocimiento en BORRADOR y, si viene, su solución. El administrador lo publica con el flujo normal. */
    @Transactional
    public Integer crearBorrador(String titulo, String descripcion, Integer hardwareId, Integer sistemaId,
            Integer moduloId, String comentario, String solucion, String tituloSolucion, String emailAutor) {
        Conocimiento creado = mantenedor.crear(titulo, descripcion, hardwareId, sistemaId, moduloId, null,
                comentario, emailAutor);
        if (solucion != null && !solucion.isBlank()) {
            String tituloUtil = tituloSolucion == null || tituloSolucion.isBlank() ? "Solución aplicada" : tituloSolucion.strip();
            if (tituloUtil.length() > MAX_TITULO_SOLUCION) tituloUtil = tituloUtil.substring(0, MAX_TITULO_SOLUCION);
            soluciones.crear(creado.getId(), solucion.strip(), TipoSolucion.PASOS, 1, tituloUtil);
        }
        return creado.getId();
    }
}