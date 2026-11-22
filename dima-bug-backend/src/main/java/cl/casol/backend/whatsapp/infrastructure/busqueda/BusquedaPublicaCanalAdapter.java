package cl.casol.backend.whatsapp.infrastructure.busqueda;
import cl.casol.backend.conocimiento.application.service.ConsultarChatbotService;
import cl.casol.backend.whatsapp.application.port.out.BuscarSolucionesCanalPort;
import cl.casol.backend.whatsapp.domain.RespuestaCanal.Solucion;
import java.util.List;
/** Proyecta exclusivamente los campos que publica /api/publico/chatbot/consultas. */
public final class BusquedaPublicaCanalAdapter implements BuscarSolucionesCanalPort {
    private final ConsultarChatbotService servicio;
    public BusquedaPublicaCanalAdapter(ConsultarChatbotService servicio) { this.servicio=servicio; }
    public List<Solucion> buscar(String consulta) {
        if (consulta==null || consulta.isBlank() || consulta.length()>300) throw new IllegalArgumentException("Consulta inválida");
        return servicio.consultar(consulta.strip()).stream().map(s -> new Solucion(s.id(),s.titulo(),s.solucion())).toList();
    }
}
