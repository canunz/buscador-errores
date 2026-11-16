package cl.casol.backend.ejecucion.domain;

import java.time.Instant;

/** Una ronda pertenece a una instancia de paso, nunca a la definición compartida. */
public record SolicitudEvidencia(String id,Integer ejecucionId,Integer ejecucionPasoId,int ronda,
        Contexto contexto,Politica politica,Estado estado,Instant fechaEnvio,String mensajeEnviadoId,
        int reglaAplicada,Instant proximoSeguimiento,String mensajeRecibidoId,String huellaRecibida,Long version) {
    public enum Estado { PENDIENTE, ESPERANDO_EVIDENCIA, EVIDENCIA_RECIBIDA, APROBADA, RECHAZADA, RECHAZADA_DEFINITIVA, CANCELADA, ERROR,
        ENVIADA, ESPERANDO_CONFIRMACION, CONFIRMACION_RECIBIDA, ENVIADA_PENDIENTE_APROBACION, ACCION_PENDIENTE_APROBACION, ENVIANDO }
    public record Contexto(Integer sucursalId,String sucursalCodigo,String sucursalNombre,String responsableNombre,
            String responsableEmail,Integer actorId,Integer procedimientoId,String claveInicio,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios) {
        public Contexto {destinatarios=cl.casol.backend.shared.domain.DestinatarioPaso.normalizar(destinatarios);}
        public Contexto(Integer s,String codigo,String nombre,String responsable,String email,Integer actor,Integer procedimiento,String clave){this(s,codigo,nombre,responsable,email,actor,procedimiento,clave,java.util.List.of(new cl.casol.backend.shared.domain.DestinatarioPaso(email,cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.PRINCIPAL)));}
    }
    public record Politica(String mensaje,int primerRecordatorioHoras,int segundoRecordatorioHoras,int escalamientoHoras,cl.casol.backend.procedimiento.domain.ConfiguracionPaso configuracion) {
        public Politica {
            if(primerRecordatorioHoras<1 || segundoRecordatorioHoras<=primerRecordatorioHoras || escalamientoHoras<=segundoRecordatorioHoras || escalamientoHoras>8760)
                throw new IllegalArgumentException("Plazos inválidos: deben ser positivos y crecientes");
            if(configuracion==null)configuracion=cl.casol.backend.procedimiento.domain.ConfiguracionPaso.resolver(new cl.casol.backend.procedimiento.domain.ConfiguracionPaso(java.util.List.of(),true,mensaje,null,true,java.util.List.of(primerRecordatorioHoras,segundoRecordatorioHoras),escalamientoHoras,null,null),cl.casol.backend.procedimiento.domain.ModoEjecucionPaso.VALIDACION_EXTERNA,true,mensaje);
        }
        public Politica(String mensaje,int h1,int h2,int h3){this(mensaje,h1,h2,h3,null);}
    }
    public SolicitudEvidencia estado(Estado nuevo) {
        return copiar(nuevo,fechaEnvio,mensajeEnviadoId,reglaAplicada,null,mensajeRecibidoId,huellaRecibida);
    }
    public SolicitudEvidencia enviada(Instant fecha,String mensajeId) {
        return enviada(fecha,mensajeId,Estado.ESPERANDO_EVIDENCIA);
    }
    public SolicitudEvidencia enviada(Instant fecha,String mensajeId,Estado estado){var reglas=politica.configuracion().recordatoriosHoras();int siguiente=reglas.isEmpty()?politica.configuracion().plazoRespuestaHoras():reglas.getFirst();return copiar(estado,fecha,mensajeId,0,estado==Estado.ESPERANDO_EVIDENCIA || estado==Estado.ESPERANDO_CONFIRMACION?fecha.plusSeconds(siguiente*3600L):null,null,null);}
    public SolicitudEvidencia recibida(String mensajeId,String huella) {
        return copiar(Estado.EVIDENCIA_RECIBIDA,fechaEnvio,mensajeEnviadoId,reglaAplicada,null,mensajeId,huella);
    }
    public SolicitudEvidencia confirmacion(String mensajeId,String huella){return copiar(Estado.CONFIRMACION_RECIBIDA,fechaEnvio,mensajeEnviadoId,reglaAplicada,null,mensajeId,huella);}
    public SolicitudEvidencia seguimiento(int regla,Instant proximo) {
        return copiar(estado,fechaEnvio,mensajeEnviadoId,regla,proximo,mensajeRecibidoId,huellaRecibida);
    }
    private SolicitudEvidencia copiar(Estado e,Instant fecha,String enviado,int regla,Instant proximo,String recibido,String huella) {
        return new SolicitudEvidencia(id,ejecucionId,ejecucionPasoId,ronda,contexto,politica,e,fecha,enviado,regla,proximo,recibido,huella,version);
    }
}
