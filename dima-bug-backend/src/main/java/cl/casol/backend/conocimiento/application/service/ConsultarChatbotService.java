package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.SolucionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsultarChatbotService {
    public static final int MAX_RESULTADOS = 5;
    private final BuscarConocimientoService buscador;
    private final SolucionRepository soluciones;

    public ConsultarChatbotService(BuscarConocimientoService buscador, SolucionRepository soluciones) {
        this.buscador = buscador;
        this.soluciones = soluciones;
    }

    public List<SolucionChatbot> consultar(String consulta) {
        List<SolucionChatbot> resultado = new ArrayList<>(MAX_RESULTADOS);
        for (var conocimiento : buscador.buscarAutoservicio(consulta)) {
            for (var solucion : soluciones.buscarActivasAutoservicioPorConocimiento(conocimiento.id())) {
                resultado.add(new SolucionChatbot(conocimiento.titulo(), solucion.titulo(), solucion.descripcion()));
                if (resultado.size() == MAX_RESULTADOS) return List.copyOf(resultado);
            }
        }
        return List.copyOf(resultado);
    }

    public record SolucionChatbot(String conocimiento, String titulo, String solucion) { }
}
