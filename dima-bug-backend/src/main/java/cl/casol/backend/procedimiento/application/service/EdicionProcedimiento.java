package cl.casol.backend.procedimiento.application.service;
import cl.casol.backend.procedimiento.domain.*;
import cl.casol.backend.procedimiento.domain.exception.EstadoProcedimientoInvalidoException;
public final class EdicionProcedimiento {
    private EdicionProcedimiento() { }
    public static void exigirBorrador(Procedimiento p) {
        if (p.estado()!=EstadoProcedimiento.BORRADOR)
            throw new EstadoProcedimientoInvalidoException("El procedimiento publicado no puede modificarse: se preserva el historial de ejecuciones");
    }
}
