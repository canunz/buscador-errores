package cl.casol.backend.tickets.infrastructure.persistence.adapter;

import cl.casol.backend.tickets.application.port.out.TicketRepositoryPort;
import cl.casol.backend.tickets.application.port.out.ResumenTicketFila;
import cl.casol.backend.tickets.domain.ConteoTickets;
import cl.casol.backend.tickets.domain.TicketsPorUsuario;
import cl.casol.backend.tickets.domain.EstadoTicket;
import cl.casol.backend.tickets.domain.Etiqueta;
import cl.casol.backend.tickets.domain.FiltroTickets;
import cl.casol.backend.tickets.domain.NivelEtiqueta;
import cl.casol.backend.tickets.domain.PrioridadTicket;
import cl.casol.backend.tickets.domain.PaginaTickets;
import cl.casol.backend.tickets.domain.Ticket;
import cl.casol.backend.tickets.domain.exception.TicketNoEncontradoException;
import cl.casol.backend.tickets.infrastructure.persistence.entity.TicketEntity;
import cl.casol.backend.tickets.infrastructure.persistence.mapper.TicketMapper;
import cl.casol.backend.tickets.infrastructure.persistence.repository.ComentarioTicketJpaRepository;
import cl.casol.backend.tickets.infrastructure.persistence.repository.HistorialEstadoTicketJpaRepository;
import cl.casol.backend.tickets.infrastructure.persistence.repository.TicketJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adaptador de base de datos: implementa el puerto del repositorio usando JPA y MySQL.
 * Convierte entre el objeto Ticket del dominio y la tabla ticket (con TicketMapper).
 */
@Component
public class TicketRepositoryAdapter implements TicketRepositoryPort {
    private final TicketJpaRepository tickets;
    private final ComentarioTicketJpaRepository comentarios;
    private final HistorialEstadoTicketJpaRepository historial;

    @PersistenceContext
    private EntityManager entityManager;

    public TicketRepositoryAdapter(TicketJpaRepository tickets, ComentarioTicketJpaRepository comentarios,
            HistorialEstadoTicketJpaRepository historial) {
        this.tickets = tickets;
        this.comentarios = comentarios;
        this.historial = historial;
    }

    /** Guarda el ticket y sus comentarios e historial nuevos. */
    @Override
    public Ticket guardar(Ticket ticket) {
        TicketEntity guardado = tickets.save(TicketMapper.toEntity(ticket));
        Long ticketId = guardado.getId();
        ticket.getComentarios().stream().filter(c -> c.id() == null)
                .forEach(c -> comentarios.save(TicketMapper.toEntity(ticketId, c)));
        ticket.getHistorial().stream().filter(h -> h.id() == null)
                .forEach(h -> historial.save(TicketMapper.toEntity(ticketId, h)));
        return buscarPorId(ticketId).orElseThrow(() -> new TicketNoEncontradoException(ticketId));
    }

    // Devuelve solicitante de.
    @Override
    public Optional<Integer> solicitanteDe(Long ticketId) {
        return tickets.solicitanteDe(ticketId);
    }

    // Devuelve comentario interno.
    @Override
    public Optional<Boolean> comentarioInterno(Long ticketId, Long comentarioId) {
        return comentarios.internoDe(ticketId, comentarioId);
    }

    /** Busca un ticket con sus comentarios e historial. */
    @Override
    public Optional<Ticket> buscarPorId(Long id) {
        return tickets.findById(id).map(entity -> TicketMapper.toDomain(entity,
                comentarios.findAllByTicketIdOrderByFechaAscIdAsc(id).stream().map(TicketMapper::toDomain).toList(),
                historial.findAllByTicketIdOrderByFechaAscIdAsc(id).stream().map(TicketMapper::toDomain).toList()));
    }

    /** Lista paginada con los filtros, del más nuevo al más antiguo. */
    @Override
    public PaginaTickets<Ticket> buscar(FiltroTickets filtro, int pagina, int tamano) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TicketEntity> consulta = cb.createQuery(TicketEntity.class);
        Root<TicketEntity> root = consulta.from(TicketEntity.class);
        Predicate filtroSql = especificacion(filtro).toPredicate(root, consulta, cb);
        if (filtroSql != null) consulta.where(filtroSql);
        consulta.orderBy(cb.desc(root.get("fechaCreacion")), cb.desc(root.get("id")));
        List<TicketEntity> filas = entityManager.createQuery(consulta)
                .setFirstResult(pagina * tamano)
                .setMaxResults(tamano)
                .getResultList();
        long total = filas.size() < tamano
                ? (long) pagina * tamano + filas.size()
                : contarFiltrado(filtro);
        List<Ticket> contenido = filas.stream()
                .map(entity -> TicketMapper.toDomain(entity, List.of(), List.of())).toList();
        return new PaginaTickets<>(contenido, pagina, tamano, total);
    }

    // Lista ligero.
    @Override
    public List<ResumenTicketFila> listarLigero(Integer solicitanteId, int limite) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = entityManager.createNativeQuery("""
                SELECT t.id, t.codigo, t.titulo, t.estado, t.prioridad,
                       t.solicitante_id, sol.usuario_nombre,
                       t.asignado_id, asig.usuario_nombre,
                       t.fecha_creacion, t.fecha_actualizacion,
                       GROUP_CONCAT(DISTINCT CASE WHEN etq.id IS NULL THEN NULL
                            ELSE CONCAT_WS('|', etq.id, REPLACE(etq.nombre, '|', '/'), etq.color, IFNULL(etq.nivel, 'NORMAL')) END SEPARATOR ';;')
                FROM ticket t
                LEFT JOIN u_usuario sol ON sol.usuario_id = t.solicitante_id
                LEFT JOIN u_usuario asig ON asig.usuario_id = t.asignado_id
                LEFT JOIN ticket_ticket_etiqueta rel ON rel.ticket_id = t.id
                LEFT JOIN ticket_etiqueta etq ON etq.id = rel.etiqueta_id
                WHERE (:solicitante = 0 OR t.solicitante_id = :solicitante)
                GROUP BY t.id, t.codigo, t.titulo, t.estado, t.prioridad,
                         t.solicitante_id, sol.usuario_nombre, t.asignado_id, asig.usuario_nombre,
                         t.fecha_creacion, t.fecha_actualizacion
                ORDER BY t.fecha_creacion DESC, t.id DESC
                """)
                .setParameter("solicitante", solicitanteId == null ? 0 : solicitanteId)
                .setMaxResults(limite)
                .getResultList();
        return filas.stream().map(TicketRepositoryAdapter::filaResumen).toList();
    }

    // Devuelve fila resumen.
    private static ResumenTicketFila filaResumen(Object[] fila) {
        return new ResumenTicketFila(
                numero(fila[0]).longValue(),
                texto(fila[1]),
                texto(fila[2]),
                EstadoTicket.valueOf(texto(fila[3])),
                PrioridadTicket.valueOf(texto(fila[4])),
                fila[5] == null ? null : numero(fila[5]).intValue(),
                texto(fila[6]),
                fila[7] == null ? null : numero(fila[7]).intValue(),
                texto(fila[8]),
                fecha(fila[9]),
                fecha(fila[10]),
                etiquetas(texto(fila[11])));
    }

    // Devuelve etiquetas.
    private static List<Etiqueta> etiquetas(String crudo) {
        if (crudo == null || crudo.isBlank()) return List.of();
        List<Etiqueta> etiquetas = new ArrayList<>();
        for (String parte : crudo.split(";;", -1)) {
            String[] datos = parte.split("\\|", -1);
            if (datos.length < 3 || !datos[0].chars().allMatch(Character::isDigit)) continue;
            NivelEtiqueta nivel = NivelEtiqueta.NORMAL;
            if (datos.length > 3 && datos[3] != null && !datos[3].isBlank()) {
                try {
                    nivel = NivelEtiqueta.valueOf(datos[3]);
                } catch (IllegalArgumentException ignored) {
                    nivel = NivelEtiqueta.NORMAL;
                }
            }
            etiquetas.add(new Etiqueta(Long.valueOf(datos[0]), datos[1], datos[2], true, nivel));
        }
        return etiquetas;
    }

    // Devuelve número.
    private static Number numero(Object valor) {
        return (Number) valor;
    }

    // Devuelve texto.
    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    // Devuelve fecha.
    private static java.time.LocalDateTime fecha(Object valor) {
        if (valor instanceof java.time.LocalDateTime fecha) return fecha;
        if (valor instanceof java.sql.Timestamp marca) return marca.toLocalDateTime();
        return null;
    }

    /** Solo cuenta cuando la página vino llena; si no, el total ya se sabe. */
    private long contarFiltrado(FiltroTickets filtro) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> consulta = cb.createQuery(Long.class);
        Root<TicketEntity> root = consulta.from(TicketEntity.class);
        Predicate filtroSql = especificacion(filtro).toPredicate(root, consulta, cb);
        if (filtroSql != null) consulta.where(filtroSql);
        consulta.select(cb.count(root));
        return entityManager.createQuery(consulta).getSingleResult();
    }

    /** Cuenta tickets por estado, críticos activos y sin técnico. */
    @Override
    public ConteoTickets contar(Integer solicitanteId) {
        java.util.Map<EstadoTicket, Long> porEstado = new java.util.EnumMap<>(EstadoTicket.class);
        for (Object[] fila : tickets.contarPorEstado(solicitanteId)) {
            porEstado.put((EstadoTicket) fila[0], ((Number) fila[1]).longValue());
        }
        long criticos = tickets.contarPrioridadEnEstados(PrioridadTicket.CRITICA,
                List.of(EstadoTicket.ABIERTO, EstadoTicket.EN_PROGRESO, EstadoTicket.EN_ESPERA), solicitanteId);
        long sinTecnico = tickets.contarSinAsignar(EstadoTicket.ABIERTO, solicitanteId);
        return new ConteoTickets(porEstado, criticos, sinTecnico);
    }

    /**
     * Agrupa los tickets por la persona que los creó.
     * Se cuentan todos, los que siguen activos y los que ya se resolvieron o cerraron.
     */
    @Override
    public List<TicketsPorUsuario> contarPorSolicitante() {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = entityManager.createNativeQuery("""
                SELECT u.usuario_id, u.usuario_nombre, u.usuario_email,
                       COUNT(t.id),
                       SUM(CASE WHEN t.estado IN ('ABIERTO', 'EN_PROGRESO', 'EN_ESPERA') THEN 1 ELSE 0 END),
                       SUM(CASE WHEN t.estado IN ('RESUELTO', 'CERRADO') THEN 1 ELSE 0 END)
                FROM ticket t
                JOIN u_usuario u ON u.usuario_id = t.solicitante_id
                GROUP BY u.usuario_id, u.usuario_nombre, u.usuario_email
                ORDER BY COUNT(t.id) DESC, u.usuario_nombre ASC
                """).getResultList();
        return filas.stream()
                .map(fila -> new TicketsPorUsuario(
                        ((Number) fila[0]).intValue(),
                        (String) fila[1],
                        (String) fila[2],
                        cantidad(fila[3]),
                        cantidad(fila[4]),
                        cantidad(fila[5])))
                .toList();
    }

    // Convierte el valor de la consulta a número; si viene vacío cuenta como 0.
    private static long cantidad(Object valor) {
        return valor == null ? 0L : ((Number) valor).longValue();
    }

    /** Siguiente número del año para el código del ticket. */
    @Override
    public int siguienteCorrelativo(int anio) {
        String prefijo = Ticket.prefijoCodigo(anio);
        Number maximo = tickets.maximoCorrelativo(prefijo, prefijo.length() + 1);
        return (maximo == null ? 0 : maximo.intValue()) + 1;
    }

    /** Arma las condiciones del filtro (estado, prioridad, texto, etc.). */
    private static Specification<TicketEntity> especificacion(FiltroTickets filtro) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.estado() != null) condiciones.add(cb.equal(root.get("estado"), filtro.estado()));
            if (filtro.prioridad() != null) condiciones.add(cb.equal(root.get("prioridad"), filtro.prioridad()));
            if (filtro.asignadoId() != null) condiciones.add(cb.equal(root.get("asignadoId"), filtro.asignadoId()));
            if (filtro.solicitanteId() != null)
                condiciones.add(cb.equal(root.get("solicitanteId"), filtro.solicitanteId()));
            if (filtro.etiquetaId() != null) {
                condiciones.add(cb.isMember(filtro.etiquetaId(), root.<java.util.Set<Long>>get("etiquetaIds")));
            }
            if (filtro.texto() != null) {
                String patron = "%" + filtro.texto().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("titulo")), patron),
                        cb.like(cb.lower(root.get("codigo")), patron),
                        cb.like(cb.lower(root.get("descripcion")), patron)));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}
