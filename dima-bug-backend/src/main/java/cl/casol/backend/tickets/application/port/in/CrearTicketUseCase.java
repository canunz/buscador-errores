package cl.casol.backend.tickets.application.port.in;

import cl.casol.backend.tickets.domain.PrioridadTicket;

/** Puerto de entrada: lo que el sistema ofrece para crear un ticket. Lo implementa CrearTicketService. */
public interface CrearTicketUseCase {
    // Crea con los datos recibidos.
    TicketVista crear(Comando comando, String emailAutenticado);

    /** Datos que llegan desde el formulario. */
    record Comando(String titulo, String descripcion, PrioridadTicket prioridad,
                   Integer hardwareId, Integer sistemaId, Integer moduloId,
                   cl.casol.backend.tickets.domain.DatosSolicitante datosSolicitante) {
        // Devuelve comando.
        public Comando(String titulo, String descripcion, PrioridadTicket prioridad,
                Integer hardwareId, Integer sistemaId, Integer moduloId) {
            this(titulo,descripcion,prioridad,hardwareId,sistemaId,moduloId,null);
        }
    }
}