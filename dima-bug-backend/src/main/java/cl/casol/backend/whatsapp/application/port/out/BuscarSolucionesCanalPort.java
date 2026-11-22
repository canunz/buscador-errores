package cl.casol.backend.whatsapp.application.port.out;
import cl.casol.backend.whatsapp.domain.RespuestaCanal.Solucion;
import java.util.List;
public interface BuscarSolucionesCanalPort { List<Solucion> buscar(String consulta); }
