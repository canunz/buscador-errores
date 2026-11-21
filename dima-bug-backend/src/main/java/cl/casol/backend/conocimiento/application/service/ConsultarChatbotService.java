package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.SolucionRepository;
import cl.casol.backend.conocimiento.domain.ConsultaError;
import cl.casol.backend.conocimiento.domain.Solucion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/** Lógica del asistente DimaBug: busca soluciones de autoservicio para lo que escribió el usuario. */
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

    /** Busca conocimientos parecidos, ordena sus soluciones por relevancia y devuelve hasta 5. */
    public List<SolucionChatbot> consultar(String consulta) {
        List<SolucionChatbot> resultado = new ArrayList<>(MAX_RESULTADOS);
        var normalizada = new ConsultaError(consulta);
        for (var conocimiento : buscador.buscarAutoservicio(consulta)) {
            var asociadas = soluciones.buscarActivasAutoservicioPorConocimiento(conocimiento.id()).stream()
                    .sorted(Comparator.comparingDouble((Solucion s) -> normalizada.puntuar(conocimiento.titulo(), s.titulo(), s.descripcion()))
                            .reversed().thenComparing(Solucion::orden, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Solucion::id, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            for (var solucion : asociadas) {
                resultado.add(new SolucionChatbot(solucion.id(), conocimiento.titulo(), solucion.titulo(), solucion.descripcion()));
                if (resultado.size() == MAX_RESULTADOS) return List.copyOf(resultado);
            }
        }
        return List.copyOf(resultado);
    }

    // Devuelve solución chatbot.
    public record SolucionChatbot(Integer id, String conocimiento, String titulo, String solucion) { }
}
