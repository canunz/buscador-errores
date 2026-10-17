package cl.casol.backend.tickets.infrastructure.integracion;

import cl.casol.backend.catalogo.api.CatalogoFachada;
import cl.casol.backend.catalogo.api.NombresClasificacion;
import cl.casol.backend.tickets.application.port.out.CatalogoConsultaPort;
import cl.casol.backend.tickets.application.port.out.ClasificacionTicket;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adaptador hacia la API pública del módulo de catálogos. */
@Component
public class CatalogoConsultaAdapter implements CatalogoConsultaPort {
    private final CatalogoFachada catalogo;

    public CatalogoConsultaAdapter(CatalogoFachada catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public Optional<String> validarClasificacion(Integer hardwareId, Integer sistemaId, Integer moduloId) {
        return catalogo.validarClasificacion(hardwareId, sistemaId, moduloId);
    }

    @Override
    public ClasificacionTicket nombres(Integer hardwareId, Integer sistemaId, Integer moduloId) {
        NombresClasificacion nombres = catalogo.nombres(hardwareId, sistemaId, moduloId);
        return new ClasificacionTicket(nombres.hardware(), nombres.sistema(), nombres.modulo());
    }
}