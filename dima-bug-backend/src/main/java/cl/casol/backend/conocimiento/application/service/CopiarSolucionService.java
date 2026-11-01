package cl.casol.backend.conocimiento.application.service;
import cl.casol.backend.conocimiento.application.port.out.*;
import cl.casol.backend.conocimiento.domain.*;
import cl.casol.backend.conocimiento.domain.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class CopiarSolucionService {
    private final ConocimientoRepository conocimientos;
    private final SolucionRepository soluciones;
    private final IndexarConocimientoService indexador;
    public CopiarSolucionService(ConocimientoRepository conocimientos, SolucionRepository soluciones,
            IndexarConocimientoService indexador) {
        this.conocimientos=conocimientos; this.soluciones=soluciones; this.indexador=indexador;
    }
    @Transactional
    public Solucion copiar(Integer origenId, Integer solucionId, Integer destinoId, boolean confirmarAutoservicio) {
        if (Objects.equals(origenId,destinoId)) throw new CopiaSolucionInvalidaException("Origen y destino deben ser diferentes");
        // Orden estable también para copias cruzadas: evita invertir el orden de bloqueo de padres.
        conocimientos.bloquear(Math.min(origenId,destinoId));
        conocimientos.bloquear(Math.max(origenId,destinoId));
        var origen=conocimientos.buscarPorId(origenId).orElseThrow(()->new ConocimientoNoEncontradoException(origenId));
        var destino=conocimientos.buscarPorId(destinoId).orElseThrow(()->new ConocimientoNoEncontradoException(destinoId));
        if (origen.getEstado()!=EstadoConocimiento.PUBLICADO)
            throw new CopiaSolucionInvalidaException("El conocimiento de origen debe estar PUBLICADO");
        if (destino.getEstado()!=EstadoConocimiento.BORRADOR && destino.getEstado()!=EstadoConocimiento.PUBLICADO)
            throw new CopiaSolucionInvalidaException("El conocimiento destino no admite soluciones");
        var original=soluciones.bloquearPorConocimiento(origenId).stream().filter(s->Objects.equals(s.id(),solucionId))
                .findFirst().orElseThrow(()->new SolucionNoEncontradaException(solucionId));
        var actuales=soluciones.bloquearPorConocimiento(destinoId);
        if (destino.getEstado()==EstadoConocimiento.PUBLICADO && !original.activo()
                && actuales.stream().noneMatch(Solucion::activo)) throw new UltimaSolucionActivaException();
        IdentidadSolucion.exigirNueva(actuales,original.titulo(),original.descripcion(),original.tipo());
        int maximo=actuales.stream().mapToInt(Solucion::orden).max().orElse(0);
        if (maximo==Integer.MAX_VALUE) throw new CopiaSolucionInvalidaException("No hay un orden disponible para otra solución");
        var copia=soluciones.guardar(new Solucion(null,destinoId,original.descripcion(),original.tipo(),maximo+1,
                original.titulo(),original.activo(),confirmarAutoservicio));
        // Mismo mecanismo transaccional que la creación actual: si falla, se revierte la copia.
        indexador.indexar(destinoId);
        return copia;
    }
}
