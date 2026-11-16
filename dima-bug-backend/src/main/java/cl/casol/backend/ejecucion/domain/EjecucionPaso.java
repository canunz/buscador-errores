package cl.casol.backend.ejecucion.domain;

import java.time.LocalDateTime;

public record EjecucionPaso(Integer id, Integer ejecucionId, Integer pasoId,
        boolean cumplido, String observacion, LocalDateTime fecha,
        ContratoPaso contrato, EstadoEjecucionPaso estado, long version) {
    public EjecucionPaso {
        if (estado == null) estado = cumplido ? EstadoEjecucionPaso.COMPLETADO : EstadoEjecucionPaso.PENDIENTE;
        if (cumplido != (estado == EstadoEjecucionPaso.COMPLETADO))
            throw new IllegalArgumentException("El estado y el cumplimiento del paso deben coincidir");
        if (version < 0) throw new IllegalArgumentException("Versión inválida");
    }

    /** Compatibilidad de lectura; no inventa un snapshot de la definición histórica. */
    public EjecucionPaso(Integer id, Integer ejecucionId, Integer pasoId,
            boolean cumplido, String observacion, LocalDateTime fecha) {
        this(id, ejecucionId, pasoId, cumplido, observacion, fecha, null, null, 0);
    }

    public EjecucionPaso actualizar(boolean nuevoCumplido, String nota, LocalDateTime momento) {
        if (contrato != null && !contrato.modo().esManual() && contrato.modo()!=cl.casol.backend.procedimiento.domain.ModoEjecucionPaso.INFORMATIVO)
            throw new cl.casol.backend.ejecucion.domain.exception.ConflictoEjecucionException("El paso no admite marcado manual");
        LocalDateTime nuevaFecha = nuevoCumplido ? (cumplido && fecha != null ? fecha : momento) : null;
        return new EjecucionPaso(id, ejecucionId, pasoId, nuevoCumplido, nota, nuevaFecha,
                contrato, nuevoCumplido ? EstadoEjecucionPaso.COMPLETADO : EstadoEjecucionPaso.PENDIENTE, version + 1);
    }
}
