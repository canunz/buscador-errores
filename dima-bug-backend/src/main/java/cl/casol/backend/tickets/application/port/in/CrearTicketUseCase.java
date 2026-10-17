package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.PrioridadTicket;

public interface CrearTicketUseCase {
    TicketVista crear(Comando comando, String emailAutenticado);

    record Comando(String titulo, String descripcion, PrioridadTicket prioridad,
                   Integer hardwareId, Integer sistemaId, Integer moduloId) { }
}