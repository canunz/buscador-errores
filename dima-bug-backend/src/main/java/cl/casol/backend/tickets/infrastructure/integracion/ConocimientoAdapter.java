package cl.casol.backend.tickets.infrastructure.integracion;

import cl.casol.backend.conocimiento.api.ConocimientoFachada;
import cl.casol.backend.tickets.application.port.out.BorradorConocimiento;
import cl.casol.backend.tickets.application.port.out.ConocimientoPort;
import cl.casol.backend.tickets.application.port.out.ConocimientoReferencia;
import cl.casol.backend.tickets.application.port.out.ConocimientoSugerido;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adaptador hacia la API pública del módulo de conocimiento. */
@Component
public class ConocimientoAdapter implements ConocimientoPort {
    private final ConocimientoFachada conocimiento;

    public ConocimientoAdapter(ConocimientoFachada conocimiento) {
        this.conocimiento = conocimiento;
    }

    @Override
    public List<ConocimientoSugerido> sugerir(String texto, int limite) {
        return conocimiento.buscarPublicados(texto, limite).stream()
                .map(c -> new ConocimientoSugerido(c.id(), c.titulo(), c.hardware(), c.sistema(), c.modulo()))
                .toList();
    }

    @Override
    public Optional<ConocimientoReferencia> buscar(Integer conocimientoId) {
        return conocimiento.buscarVigente(conocimientoId)
                .map(c -> new ConocimientoReferencia(c.id(), c.titulo(), c.estado()));
    }

    @Override
    public Integer crearBorrador(BorradorConocimiento datos, String emailAutor) {
        return conocimiento.crearBorrador(datos.titulo(), datos.descripcion(), datos.hardwareId(), datos.sistemaId(),
                datos.moduloId(), "Creado desde el ticket " + datos.codigoTicket(), datos.solucion(),
                "Solución del ticket " + datos.codigoTicket(), emailAutor);
    }
}