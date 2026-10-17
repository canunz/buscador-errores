package cl.casol.backend.tickets.infrastructure.persistence.adapter;

import cl.casol.backend.tickets.application.port.out.ClasificacionTicket;
import cl.casol.backend.tickets.application.port.out.ConocimientoReferencia;
import cl.casol.backend.tickets.application.port.out.DetalleTicketConsultaPort;
import cl.casol.backend.tickets.application.port.out.DetalleTicketPlano;
import cl.casol.backend.tickets.application.port.out.UsuarioTicket;
import cl.casol.backend.tickets.domain.AdjuntoTicket;
import cl.casol.backend.tickets.domain.ComentarioTicket;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.HistorialEstadoTicket;
import cl.casol.backend.tickets.domain.NivelEtiqueta;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.RolTicket;
import cl.casol.backend.tickets.domain.Ticket;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Una sola lectura del ticket: ficha, nombres, mensajes, historial y archivos. */
@Component
public class DetalleTicketJdbcAdapter implements DetalleTicketConsultaPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SQL = """
            SELECT t.id, t.codigo, t.titulo, t.descripcion, t.estado, t.prioridad,
                   t.solicitante_id, sol.usuario_nombre, t.asignado_id, asig.usuario_nombre,
                   t.hardware_id, hw.hardware_nombre, t.sistema_id, sis.sistema_nombre,
                   t.modulo_id, mo.modulo_nombre, t.conocimiento_id, co.conocimiento_titulo, co.conocimiento_estado,
                   t.solucion, t.fecha_creacion, t.fecha_actualizacion, t.fecha_resolucion, t.fecha_cierre,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT('id', etq.id, 'nombre', etq.nombre, 'color', etq.color, 'nivel', IFNULL(etq.nivel, 'NORMAL')))
                      FROM ticket_ticket_etiqueta rel
                      JOIN ticket_etiqueta etq ON etq.id = rel.etiqueta_id
                     WHERE rel.ticket_id = t.id),
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT('id', c.id, 'autorId', c.autor_id, 'autor', au.usuario_nombre,
                          'texto', c.texto, 'interno', IF(c.interno, 1, 0), 'fecha', DATE_FORMAT(c.fecha, '%Y-%m-%dT%H:%i:%s')))
                      FROM ticket_comentario c
                      LEFT JOIN u_usuario au ON au.usuario_id = c.autor_id
                     WHERE c.ticket_id = t.id),
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT('id', h.id, 'anterior', h.estado_anterior, 'nuevo', h.estado_nuevo,
                          'usuarioId', h.usuario_id, 'usuario', hu.usuario_nombre, 'motivo', h.motivo,
                          'fecha', DATE_FORMAT(h.fecha, '%Y-%m-%dT%H:%i:%s')))
                      FROM ticket_historial_estado h
                      LEFT JOIN u_usuario hu ON hu.usuario_id = h.usuario_id
                     WHERE h.ticket_id = t.id),
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT('id', a.id, 'nombre', a.nombre, 'mime', a.mime, 'tamano', a.tamano,
                          'comentarioId', a.comentario_id, 'autorId', a.autor_id, 'autor', aa.usuario_nombre,
                          'fecha', DATE_FORMAT(a.fecha, '%Y-%m-%dT%H:%i:%s')))
                      FROM ticket_adjunto a
                      LEFT JOIN u_usuario aa ON aa.usuario_id = a.autor_id
                     WHERE a.ticket_id = t.id)
              FROM ticket t
              LEFT JOIN u_usuario sol ON sol.usuario_id = t.solicitante_id
              LEFT JOIN u_usuario asig ON asig.usuario_id = t.asignado_id
              LEFT JOIN cl_hardware hw ON hw.hardware_id = t.hardware_id
              LEFT JOIN cl_sistema sis ON sis.sistema_id = t.sistema_id
              LEFT JOIN cl_modulo mo ON mo.modulo_id = t.modulo_id
              LEFT JOIN co_conocimiento co ON co.conocimiento_id = t.conocimiento_id
             WHERE t.id = ?
            """;

    private final DataSource dataSource;

    public DetalleTicketJdbcAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<DetalleTicketPlano> buscar(Long id) {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement consulta = conexion.prepareStatement(SQL)) {
            consulta.setLong(1, id);
            try (ResultSet fila = consulta.executeQuery()) {
                if (!fila.next()) return Optional.empty();
                return Optional.of(armar(fila));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No fue posible leer el ticket", ex);
        }
    }

    private static DetalleTicketPlano armar(ResultSet fila) throws SQLException {
        Map<Integer, UsuarioTicket> usuarios = new HashMap<>();
        Integer solicitanteId = entero(fila, 7);
        Integer asignadoId = entero(fila, 9);
        recordar(usuarios, solicitanteId, fila.getString(8));
        recordar(usuarios, asignadoId, fila.getString(10));

        List<Etiqueta> etiquetas = etiquetas(json(fila, 25));
        Set<Long> etiquetaIds = new LinkedHashSet<>();
        etiquetas.forEach(etiqueta -> etiquetaIds.add(etiqueta.getId()));
        List<ComentarioTicket> comentarios = comentarios(json(fila, 26), usuarios);
        List<HistorialEstadoTicket> historial = historial(json(fila, 27), usuarios);
        long ticketId = fila.getLong(1);
        List<AdjuntoTicket> adjuntos = adjuntos(json(fila, 28), ticketId, usuarios);

        Integer conocimientoId = entero(fila, 17);
        String estadoConocimiento = fila.getString(19);
        ConocimientoReferencia conocimiento = conocimientoId == null || "ELIMINADO".equals(estadoConocimiento)
                ? null
                : new ConocimientoReferencia(conocimientoId, fila.getString(18), estadoConocimiento);

        Ticket ticket = new Ticket(ticketId, fila.getString(2), fila.getString(3), fila.getString(4),
                EstadoTicket.valueOf(fila.getString(5)), PrioridadTicket.valueOf(fila.getString(6)),
                solicitanteId, asignadoId, entero(fila, 11), entero(fila, 13), entero(fila, 15),
                conocimiento == null ? null : conocimientoId, fila.getString(20),
                fecha(fila, 21), fecha(fila, 22), fecha(fila, 23), fecha(fila, 24),
                etiquetaIds, comentarios, historial);
        return new DetalleTicketPlano(ticket, etiquetas, usuarios,
                new ClasificacionTicket(fila.getString(12), fila.getString(14), fila.getString(16)),
                conocimiento, adjuntos);
    }

    private static void recordar(Map<Integer, UsuarioTicket> usuarios, Integer id, String nombre) {
        if (id == null) return;
        usuarios.putIfAbsent(id, new UsuarioTicket(id, nombre == null || nombre.isBlank() ? "Usuario " + id : nombre,
                null, RolTicket.USUARIO, true));
    }

    private static List<Etiqueta> etiquetas(JsonNode nodos) {
        List<Etiqueta> etiquetas = new ArrayList<>();
        if (!nodos.isArray()) return etiquetas;
        nodos.forEach(nodo -> {
            NivelEtiqueta nivel = NivelEtiqueta.NORMAL;
            if (nodo.hasNonNull("nivel")) {
                try {
                    nivel = NivelEtiqueta.valueOf(nodo.get("nivel").asText());
                } catch (IllegalArgumentException ignored) {
                    nivel = NivelEtiqueta.NORMAL;
                }
            }
            String color = nodo.path("color").asText("#64748B");
            if (!color.matches("#[0-9A-Fa-f]{6}")) color = "#64748B";
            String nombre = nodo.path("nombre").asText("").strip();
            etiquetas.add(new Etiqueta(nodo.get("id").asLong(), nombre.isEmpty() ? "etiqueta" : nombre, color, true, nivel));
        });
        etiquetas.sort(Comparator.comparing(Etiqueta::getNombre, String.CASE_INSENSITIVE_ORDER));
        return etiquetas;
    }

    private static List<ComentarioTicket> comentarios(JsonNode nodos, Map<Integer, UsuarioTicket> usuarios) {
        List<ComentarioTicket> comentarios = new ArrayList<>();
        if (!nodos.isArray()) return comentarios;
        nodos.forEach(nodo -> {
            Integer autorId = nodo.hasNonNull("autorId") ? nodo.get("autorId").asInt() : null;
            recordar(usuarios, autorId, nodo.path("autor").asText(null));
            comentarios.add(new ComentarioTicket(nodo.get("id").asLong(), autorId, nodo.path("texto").asText(""),
                    nodo.path("interno").asInt(0) == 1, LocalDateTime.parse(nodo.get("fecha").asText())));
        });
        comentarios.sort(Comparator.comparing(ComentarioTicket::fecha).thenComparing(ComentarioTicket::id));
        return comentarios;
    }

    private static List<HistorialEstadoTicket> historial(JsonNode nodos, Map<Integer, UsuarioTicket> usuarios) {
        List<HistorialEstadoTicket> historial = new ArrayList<>();
        if (!nodos.isArray()) return historial;
        nodos.forEach(nodo -> {
            Integer usuarioId = nodo.hasNonNull("usuarioId") ? nodo.get("usuarioId").asInt() : null;
            recordar(usuarios, usuarioId, nodo.path("usuario").asText(null));
            EstadoTicket anterior = nodo.hasNonNull("anterior") ? EstadoTicket.valueOf(nodo.get("anterior").asText()) : null;
            historial.add(new HistorialEstadoTicket(nodo.get("id").asLong(), anterior,
                    EstadoTicket.valueOf(nodo.get("nuevo").asText()), usuarioId,
                    nodo.hasNonNull("motivo") ? nodo.get("motivo").asText() : null,
                    LocalDateTime.parse(nodo.get("fecha").asText())));
        });
        historial.sort(Comparator.comparing(HistorialEstadoTicket::fecha).thenComparing(HistorialEstadoTicket::id));
        return historial;
    }

    private static List<AdjuntoTicket> adjuntos(JsonNode nodos, long ticketId, Map<Integer, UsuarioTicket> usuarios) {
        List<AdjuntoTicket> adjuntos = new ArrayList<>();
        if (!nodos.isArray()) return adjuntos;
        nodos.forEach(nodo -> {
            Integer autorId = nodo.hasNonNull("autorId") ? nodo.get("autorId").asInt() : null;
            recordar(usuarios, autorId, nodo.path("autor").asText(null));
            Long comentarioId = nodo.hasNonNull("comentarioId") ? nodo.get("comentarioId").asLong() : null;
            adjuntos.add(new AdjuntoTicket(nodo.get("id").asLong(), ticketId, comentarioId, nodo.path("nombre").asText(""),
                    nodo.path("mime").asText("application/octet-stream"), nodo.path("tamano").asLong(0), "",
                    autorId, LocalDateTime.parse(nodo.get("fecha").asText())));
        });
        adjuntos.sort(Comparator.comparing(AdjuntoTicket::id));
        return adjuntos;
    }

    private static JsonNode json(ResultSet fila, int columna) throws SQLException {
        String texto = fila.getString(columna);
        if (texto == null || texto.isBlank()) return JSON.missingNode();
        try {
            return JSON.readTree(texto);
        } catch (Exception ex) {
            return JSON.missingNode();
        }
    }

    private static Integer entero(ResultSet fila, int columna) throws SQLException {
        int valor = fila.getInt(columna);
        return fila.wasNull() ? null : valor;
    }

    private static LocalDateTime fecha(ResultSet fila, int columna) throws SQLException {
        Timestamp marca = fila.getTimestamp(columna);
        return marca == null ? null : marca.toLocalDateTime();
    }
}
