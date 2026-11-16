package cl.casol.backend.conocimiento.application.service;

import cl.casol.backend.conocimiento.application.port.out.DocumentoBusquedaConocimientoRepository;
import cl.casol.backend.conocimiento.domain.DocumentoBusquedaConocimiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

@Service
@Transactional
public class IndexarConocimientoService {
    private final DocumentoBusquedaConocimientoRepository documentos;

    public IndexarConocimientoService(DocumentoBusquedaConocimientoRepository documentos) {
        this.documentos = documentos;
    }

    public void indexar(Integer conocimientoId) {
        documentos.obtener(conocimientoId).ifPresent(documento ->
                documentos.guardar(conocimientoId, construirContenido(documento)));
    }

    public void eliminar(Integer conocimientoId) {
        documentos.eliminar(conocimientoId);
    }

    String construirContenido(DocumentoBusquedaConocimiento documento) {
        return Stream.of(documento.conocimientoYClasificacion(), documento.sintomas(), documento.causas(),
                        documento.pruebas(), documento.soluciones(), documento.asignaciones(), documento.materiales())
                .flatMap(java.util.Collection::stream)
                .filter(valor -> valor != null && !valor.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
