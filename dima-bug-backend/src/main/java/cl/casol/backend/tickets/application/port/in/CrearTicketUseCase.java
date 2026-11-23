package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.PrioridadTicket;

public interface CrearTicketUseCase {
    TicketVista crear(Comando comando, String emailAutenticado);

    record Comando(String titulo, String descripcion, PrioridadTicket prioridad,
                   Integer hardwareId, Integer sistemaId, Integer moduloId,
                   cl.casol.backend.tickets.domain.DatosSolicitante datosSolicitante) {
        public Comando(String titulo, String descripcion, PrioridadTicket prioridad,
                Integer hardwareId, Integer sistemaId, Integer moduloId) {
            this(titulo,descripcion,prioridad,hardwareId,sistemaId,moduloId,null);
        }
    }
}