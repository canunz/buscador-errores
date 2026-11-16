package cl.casol.backend.catalogo.application.service;

import cl.casol.backend.catalogo.application.port.out.FrecuenciaRepository;
import cl.casol.backend.catalogo.domain.Frecuencia;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ConsultarFrecuenciasService {
    private final FrecuenciaRepository frecuencias;

    public ConsultarFrecuenciasService(FrecuenciaRepository frecuencias) { this.frecuencias = frecuencias; }

    public List<Frecuencia> listar() { return frecuencias.buscarActivasOrdenadas(); }
}
