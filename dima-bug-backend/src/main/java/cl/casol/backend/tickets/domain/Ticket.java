package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;
import cl.casol.backend.tickets.domain.exception.TransicionEstadoInvalidaException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agregado Ticket. Concentra las reglas de estado: si una transición no está permitida
 * lanza TransicionEstadoInvalidaException (409). Los permisos por rol se revisan en los casos de uso.
 */
public class Ticket {
    public static final int MAX_TITULO = 150;
    private static final String PREFIJO_CODIGO = "TK-";

    private final Long id;
    private final String codigo;
    private final String titulo;
    private final String descripcion;
    private EstadoTicket estado;
    private PrioridadTicket prioridad;
    private final Integer solicitanteId;
    private Integer asignadoId;
    private final Integer hardwareId;
    private final Integer sistemaId;
    private final Integer moduloId;
    private Integer conocimientoId;
    private String solucion;
    private final LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private LocalDateTime fechaResolucion;
    private LocalDateTime fechaCierre;
    private final Set<Long> etiquetaIds;
    private final List<ComentarioTicket> comentarios;
    private final List<HistorialEstadoTicket> historial;

    public Ticket(Long id, String codigo, String titulo, String descripcion, EstadoTicket estado,
                  PrioridadTicket prioridad, Integer solicitanteId, Integer asignadoId, Integer hardwareId,
                  Integer sistemaId, Integer moduloId, Integer conocimientoId, String solucion,
                  LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion, LocalDateTime fechaResolucion,
                  LocalDateTime fechaCierre, Set<Long> etiquetaIds, List<ComentarioTicket> comentarios,
                  List<HistorialEstadoTicket> historial) {
        this.id = id;
        this.codigo = codigo;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.estado = estado;
        this.prioridad = prioridad;
        this.solicitanteId = solicitanteId;
        this.asignadoId = asignadoId;
        this.hardwareId = hardwareId;
        this.sistemaId = sistemaId;
        this.moduloId = moduloId;
        this.conocimientoId = conocimientoId;
        this.solucion = solucion;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaActualizacion;
        this.fechaResolucion = fechaResolucion;
        this.fechaCierre = fechaCierre;
        this.etiquetaIds = etiquetaIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(etiquetaIds);
        this.comentarios = comentarios == null ? new ArrayList<>() : new ArrayList<>(comentarios);
        this.historial = historial == null ? new ArrayList<>() : new ArrayList<>(historial);
    }

    /** Crea un ticket nuevo en estado ABIERTO y deja registrado el primer movimiento del historial. */
    public static Ticket crear(String codigo, String titulo, String descripcion, PrioridadTicket prioridad,
                               Integer solicitanteId, Integer hardwareId, Integer sistemaId, Integer moduloId,
                               LocalDateTime ahora) {
        String tituloLimpio = titulo == null ? "" : titulo.strip();
        String descripcionLimpia = descripcion == null ? "" : descripcion.strip();
        if (tituloLimpio.isEmpty()) throw new TicketInvalidoException("El título es obligatorio");
        if (tituloLimpio.length() > MAX_TITULO)
            throw new TicketInvalidoException("El título no puede superar " + MAX_TITULO + " caracteres");
        if (descripcionLimpia.isEmpty()) throw new TicketInvalidoException("La descripción es obligatoria");
        if (solicitanteId == null) throw new TicketInvalidoException("El ticket necesita un solicitante");
        Ticket ticket = new Ticket(null, codigo, tituloLimpio, descripcionLimpia, EstadoTicket.ABIERTO,
                prioridad == null ? PrioridadTicket.MEDIA : prioridad, solicitanteId, null, hardwareId, sistemaId,
                moduloId, null, null, ahora, ahora, null, null, Set.of(), List.of(), List.of());
        ticket.historial.add(new HistorialEstadoTicket(null, null, EstadoTicket.ABIERTO, solicitanteId, null, ahora));
        return ticket;
    }

    /** Formato TK-2026-0001. */
    public static String generarCodigo(int anio, int correlativo) {
        return PREFIJO_CODIGO + anio + "-" + String.format("%04d", correlativo);
    }

    public static String prefijoCodigo(int anio) {
        return PREFIJO_CODIGO + anio + "-";
    }

    // ----------------------------------------------------------------- transiciones

    /** Texto con que empieza el registro de un cambio de prioridad en el historial. */
    public static final String PREFIJO_PRIORIDAD = "Prioridad: ";
    public static final int MAX_MOTIVO_PRIORIDAD = 180;

    /**
     * Soporte o el administrador corrigen la prioridad que eligió el usuario.
     * Queda en el historial (sin cambio de estado) con el antes, el después y el motivo.
     */
    public void cambiarPrioridad(PrioridadTicket nueva, Integer actorId, String motivo, LocalDateTime ahora) {
        exigirAbierto("cambios");
        if (nueva == null) throw new TicketInvalidoException("Debe indicar la prioridad nueva");
        if (nueva == prioridad) throw new TicketInvalidoException("El ticket ya tiene prioridad " + nueva);
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.length() > MAX_MOTIVO_PRIORIDAD) {
            throw new TicketInvalidoException("El motivo no puede superar " + MAX_MOTIVO_PRIORIDAD + " caracteres");
        }
        String registro = PREFIJO_PRIORIDAD + prioridad + " -> " + nueva + (limpio.isEmpty() ? "" : ". " + limpio);
        historial.add(new HistorialEstadoTicket(null, estado, estado, actorId, registro, ahora));
        this.prioridad = nueva;
        this.fechaActualizacion = ahora;
    }

    /** Asigna un técnico. Si el ticket estaba ABIERTO pasa a EN_PROGRESO. */
    public void asignar(Integer tecnicoId, Integer actorId, LocalDateTime ahora) {
        if (tecnicoId == null) throw new TicketInvalidoException("Debe indicar el técnico asignado");
        if (estado != EstadoTicket.ABIERTO && estado != EstadoTicket.EN_PROGRESO && estado != EstadoTicket.EN_ESPERA) {
            throw new TransicionEstadoInvalidaException("No se puede asignar un ticket en estado " + estado);
        }
        this.asignadoId = tecnicoId;
        if (estado == EstadoTicket.ABIERTO) {
            cambiarEstado(EstadoTicket.EN_PROGRESO, actorId, null, ahora);
        } else {
            this.fechaActualizacion = ahora;
        }
    }

    /** ABIERTO → EN_PROGRESO o EN_ESPERA → EN_PROGRESO. */
    public void iniciar(Integer actorId, LocalDateTime ahora) {
        exigirEstado(EstadoTicket.EN_PROGRESO, EstadoTicket.ABIERTO, EstadoTicket.EN_ESPERA);
        cambiarEstado(EstadoTicket.EN_PROGRESO, actorId, null, ahora);
    }

    /** EN_PROGRESO → EN_ESPERA (soporte espera respuesta del usuario). */
    public void ponerEnEspera(Integer actorId, String motivo, LocalDateTime ahora) {
        exigirEstado(EstadoTicket.EN_ESPERA, EstadoTicket.EN_PROGRESO);
        cambiarEstado(EstadoTicket.EN_ESPERA, actorId, motivo, ahora);
    }

    /** EN_PROGRESO → RESUELTO. Exige la solución aplicada. */
    public void resolver(String solucion, Integer conocimientoId, Integer actorId, LocalDateTime ahora) {
        exigirEstado(EstadoTicket.RESUELTO, EstadoTicket.EN_PROGRESO);
        String limpia = solucion == null ? "" : solucion.strip();
        if (limpia.isEmpty()) throw new TicketInvalidoException("Debe describir la solución aplicada");
        this.solucion = limpia;
        if (conocimientoId != null) this.conocimientoId = conocimientoId;
        this.fechaResolucion = ahora;
        cambiarEstado(EstadoTicket.RESUELTO, actorId, null, ahora);
    }

    /** RESUELTO → CERRADO (el solicitante confirma o el administrador cierra). */
    public void cerrar(Integer actorId, String motivo, LocalDateTime ahora) {
        exigirEstado(EstadoTicket.CERRADO, EstadoTicket.RESUELTO);
        this.fechaCierre = ahora;
        cambiarEstado(EstadoTicket.CERRADO, actorId, motivo, ahora);
    }

    /** RESUELTO → EN_PROGRESO (no se solucionó). */
    public void reabrir(Integer actorId, String motivo, LocalDateTime ahora) {
        exigirEstado(EstadoTicket.EN_PROGRESO, EstadoTicket.RESUELTO);
        this.fechaResolucion = null;
        cambiarEstado(EstadoTicket.EN_PROGRESO, actorId, motivo, ahora);
    }

    /** ABIERTO → CANCELADO. */
    public void cancelar(Integer actorId, String motivo, LocalDateTime ahora) {
        exigirEstado(EstadoTicket.CANCELADO, EstadoTicket.ABIERTO);
        this.fechaCierre = ahora;
        cambiarEstado(EstadoTicket.CANCELADO, actorId, motivo, ahora);
    }

    // ----------------------------------------------------------------- otras acciones

    public ComentarioTicket comentar(Integer autorId, String texto, boolean interno, LocalDateTime ahora) {
        exigirAbierto("comentarios");
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty()) throw new TicketInvalidoException("El comentario no puede estar vacío");
        if (limpio.length() > ComentarioTicket.MAX_TEXTO)
            throw new TicketInvalidoException("El comentario no puede superar " + ComentarioTicket.MAX_TEXTO + " caracteres");
        ComentarioTicket comentario = new ComentarioTicket(null, autorId, limpio, interno, ahora);
        comentarios.add(comentario);
        this.fechaActualizacion = ahora;
        return comentario;
    }

    public void etiquetar(Set<Long> nuevasEtiquetas, LocalDateTime ahora) {
        exigirAbierto("cambios");
        etiquetaIds.clear();
        if (nuevasEtiquetas != null) etiquetaIds.addAll(nuevasEtiquetas);
        this.fechaActualizacion = ahora;
    }

    public void vincularConocimiento(Integer conocimientoId, LocalDateTime ahora) {
        exigirAbierto("cambios");
        if (conocimientoId == null) throw new TicketInvalidoException("Debe indicar el conocimiento");
        this.conocimientoId = conocimientoId;
        this.fechaActualizacion = ahora;
    }

    /** Lanza 409 si el ticket está CERRADO o CANCELADO. */
    public void validarAdmiteCambios() {
        exigirAbierto("cambios");
    }

    // ----------------------------------------------------------------- consultas

    public boolean puedeVer(ActorTicket actor) {
        return actor.esStaff() || Objects.equals(actor.id(), solicitanteId);
    }

    public List<ComentarioTicket> comentariosVisiblesPara(ActorTicket actor) {
        return comentarios.stream().filter(c -> c.visiblePara(actor)).toList();
    }

    // ----------------------------------------------------------------- internos

    private void exigirEstado(EstadoTicket destino, EstadoTicket... permitidos) {
        for (EstadoTicket permitido : permitidos) {
            if (estado == permitido) return;
        }
        throw new TransicionEstadoInvalidaException(estado, destino);
    }

    private void exigirAbierto(String que) {
        if (estado.esFinal()) {
            throw new TransicionEstadoInvalidaException("El ticket está " + estado + " y no admite " + que);
        }
    }

    private void cambiarEstado(EstadoTicket nuevo, Integer actorId, String motivo, LocalDateTime ahora) {
        String motivoLimpio = motivo == null || motivo.isBlank() ? null : motivo.strip();
        historial.add(new HistorialEstadoTicket(null, estado, nuevo, actorId, motivoLimpio, ahora));
        this.estado = nuevo;
        this.fechaActualizacion = ahora;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public EstadoTicket getEstado() { return estado; }
    public PrioridadTicket getPrioridad() { return prioridad; }
    public Integer getSolicitanteId() { return solicitanteId; }
    public Integer getAsignadoId() { return asignadoId; }
    public Integer getHardwareId() { return hardwareId; }
    public Integer getSistemaId() { return sistemaId; }
    public Integer getModuloId() { return moduloId; }
    public Integer getConocimientoId() { return conocimientoId; }
    public String getSolucion() { return solucion; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public Set<Long> getEtiquetaIds() { return Collections.unmodifiableSet(etiquetaIds); }
    public List<ComentarioTicket> getComentarios() { return Collections.unmodifiableList(comentarios); }
    public List<HistorialEstadoTicket> getHistorial() { return Collections.unmodifiableList(historial); }
}
