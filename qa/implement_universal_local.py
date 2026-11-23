"""Narrow local source edits. Never connects to a database or remote host."""
from pathlib import Path
R=Path(__file__).resolve().parents[1]
B=R/'dima-bug-backend/src/main/java/cl/casol/backend'
def read(p): return (B/p).read_text(encoding='utf-8')
def write(p,s):
    f=B/p; f.parent.mkdir(parents=True,exist_ok=True); f.write_text(s,encoding='utf-8')
def edit(p,a,b):
    s=read(p); assert a in s,p; write(p,s.replace(a,b))

write('identidad/domain/EstadoRegistro.java','''package cl.casol.backend.identidad.domain;
public enum EstadoRegistro { ADMINISTRADO, AUTO_PENDIENTE, AUTO_CONFIRMADO, AUTO_CANCELADO }
''')
for p,kind,role in [('identidad/domain/Usuario.java','Usuario','Rol'),('identidad/infrastructure/persistence/entity/UsuarioEntity.java','UsuarioEntity','RolEntity')]:
    s=read(p)
    if kind=='UsuarioEntity':
        s=s.replace('import jakarta.persistence.*;','import jakarta.persistence.*;\nimport cl.casol.backend.identidad.domain.EstadoRegistro;')
        declaration='    @Enumerated(EnumType.STRING)\n    @Column(name="registro_estado", nullable=false, length=24)\n'
    else: declaration=''
    s=s.replace('    private Integer id;', '    private Integer id;') if kind=='Usuario' else s
    # Insert before constructors, avoiding annotation placement on other fields.
    at='    public Usuario(' if kind=='Usuario' else '    protected UsuarioEntity()'
    extra=declaration+'    private EstadoRegistro estadoRegistro = EstadoRegistro.ADMINISTRADO;\n\n'
    extra+=f'''    public {kind}(Integer id, {role} rol, String nombre, String email, String passwordHash,
            boolean activo, LocalDateTime creada, LocalDateTime actualizada, EstadoRegistro estado) {{
        this(id, rol, nombre, email, passwordHash, activo, creada, actualizada);
        this.estadoRegistro = java.util.Objects.requireNonNull(estado);
    }}
    public EstadoRegistro getEstadoRegistro() {{ return estadoRegistro; }}
'''
    if kind=='Usuario':
        extra+='''    public boolean isAccesoBasico() { return estadoRegistro != EstadoRegistro.ADMINISTRADO; }
    public boolean puedeIngresar() {
        return activo && rol != null && rol.isActivo()
            && (estadoRegistro == EstadoRegistro.ADMINISTRADO
                || estadoRegistro == EstadoRegistro.AUTO_CONFIRMADO && "USUARIO".equals(rol.getNombre()));
    }
    public boolean puedeRecibirCodigo() {
        return puedeIngresar() || !activo && estadoRegistro == EstadoRegistro.AUTO_PENDIENTE
            && rol != null && rol.isActivo() && "USUARIO".equals(rol.getNombre());
    }
    public Usuario confirmarRegistro() {
        if (estadoRegistro != EstadoRegistro.AUTO_PENDIENTE || !puedeRecibirCodigo())
            throw new IllegalStateException("REGISTRO_NO_CONFIRMABLE");
        return new Usuario(id, rol, nombre, email, passwordHash, true, fechaCreacion,
            LocalDateTime.now(), EstadoRegistro.AUTO_CONFIRMADO);
    }
'''
    s=s.replace(at,extra+'\n'+at,1); write(p,s)
edit('identidad/infrastructure/persistence/mapper/UsuarioMapper.java','entity.getFechaActualizacion()','entity.getFechaActualizacion(), entity.getEstadoRegistro()')
edit('identidad/infrastructure/persistence/mapper/UsuarioMapper.java','usuario.getFechaActualizacion()','usuario.getFechaActualizacion(), usuario.getEstadoRegistro()')
edit('identidad/application/port/out/UsuarioRepository.java','public interface UsuarioRepository {','''public interface UsuarioRepository {
    default java.util.Optional<Usuario> buscarFresco(String email) { return buscarPorEmail(email); }
''')
edit('identidad/infrastructure/persistence/adapter/UsuarioRepositoryAdapter.java','    @Override\n    public Optional<Usuario> buscarPorEmail', '''    @Override
    public Optional<Usuario> buscarFresco(String email) {
        return usuarioJpaRepository.findByEmailIgnoreCase(email).map(UsuarioMapper::toDomain);
    }
    @Override
    public Optional<Usuario> buscarPorEmail''')
write('identidad/application/port/out/RegistroUniversalPort.java','''package cl.casol.backend.identidad.application.port.out;
import cl.casol.backend.identidad.domain.Usuario;
import java.util.Optional;
/** Runs in the caller transaction; locks precede eligibility/rate-limit checks. */
public interface RegistroUniversalPort {
    Optional<Usuario> resolver(String email, boolean crear, String hashAleatorio);
}
''')
write('identidad/infrastructure/persistence/adapter/RegistroUniversalAdapter.java','''package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.RegistroUniversalPort;
import cl.casol.backend.identidad.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class RegistroUniversalAdapter implements RegistroUniversalPort {
    private final JdbcTemplate jdbc;
    private final boolean habilitado;
    private final int hora, dia, pendientes;
    public RegistroUniversalAdapter(JdbcTemplate jdbc,
            @Value("${dimabug.auth.registro-universal.habilitado:false}") boolean habilitado,
            @Value("${dimabug.auth.registro-universal.maximo-hora:20}") int hora,
            @Value("${dimabug.auth.registro-universal.maximo-dia:100}") int dia,
            @Value("${dimabug.auth.registro-universal.maximo-pendientes:200}") int pendientes) {
        this.jdbc=jdbc; this.habilitado=habilitado; this.hora=hora; this.dia=dia; this.pendientes=pendientes;
    }
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<Usuario> resolver(String email, boolean crear, String hashAleatorio) {
        String e=email==null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (e.length()>120 || !e.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+"))
            return Optional.empty();
        Optional<Usuario> actual=buscar(e);
        if (actual.isPresent() || !crear || !habilitado) return actual;
        // A database lock serializes unknown-email admissions across JVMs. Never accept a client role.
        List<Integer> roles=jdbc.queryForList("SELECT rol_id FROM u_rol WHERE rol_nombre='USUARIO' AND rol_estado=1 FOR UPDATE", Integer.class);
        if (roles.size()!=1) return Optional.empty();
        actual=buscar(e); // Recheck after waiting for the admission lock.
        if (actual.isPresent()) return actual;
        LocalDateTime ahora=LocalDateTime.now();
        if (cuenta("registro_estado<>'ADMINISTRADO' AND usuario_fecha_creacion>=?",ahora.minusHours(1))>=hora
                || cuenta("registro_estado<>'ADMINISTRADO' AND usuario_fecha_creacion>=?",ahora.minusDays(1))>=dia
                || cuenta("registro_estado='AUTO_PENDIENTE'")>=pendientes) return Optional.empty();
        jdbc.update("INSERT INTO u_usuario (rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion,registro_estado) VALUES (?,?,?,?,false,?,?,'AUTO_PENDIENTE')",
            roles.getFirst(),"Solicitante",e,hashAleatorio,ahora,ahora);
        return buscar(e);
    }
    private long cuenta(String condicion,Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario WHERE "+condicion,Long.class,args);
    }
    private Optional<Usuario> buscar(String e) {
        // Lock only the user row, then read fresh role metadata. No authentication cache.
        List<Integer> ids=jdbc.queryForList("SELECT usuario_id FROM u_usuario WHERE LOWER(usuario_email)=? FOR UPDATE",Integer.class,e);
        if (ids.isEmpty()) return Optional.empty();
        if (ids.size()!=1) throw new IllegalStateException("EMAIL_AMBIGUO");
        return jdbc.query("SELECT u.*,r.rol_nombre,r.rol_descripcion,r.rol_estado FROM u_usuario u JOIN u_rol r ON r.rol_id=u.rol_id WHERE u.usuario_id=?",
            (rs,n)->new Usuario(rs.getInt("usuario_id"),new Rol(rs.getInt("rol_id"),rs.getString("rol_nombre"),rs.getString("rol_descripcion"),rs.getBoolean("rol_estado"),null,null),
                rs.getString("usuario_nombre"),rs.getString("usuario_email"),rs.getString("usuario_password_hash"),rs.getBoolean("usuario_estado"),
                rs.getTimestamp("usuario_fecha_creacion").toLocalDateTime(),rs.getTimestamp("usuario_fecha_actualizacion").toLocalDateTime(),EstadoRegistro.valueOf(rs.getString("registro_estado"))),ids.getFirst()).stream().findFirst();
    }
}
''')
p='identidad/application/service/AutenticarUsuarioService.java'
edit(p,'    private final SecureRandom random', '''    @Autowired(required=false)
    private cl.casol.backend.identidad.application.port.out.RegistroUniversalPort registroUniversal;
    private final SecureRandom random''')
edit(p,'@Transactional\n    public SolicitudCodigo solicitarCodigo','@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)\n    public SolicitudCodigo solicitarCodigo')
edit(p,'Optional<Usuario> encontrado = buscar(correo).filter(this::autorizado);','''Optional<Usuario> encontrado = (registroUniversal == null ? buscar(correo)
            : registroUniversal.resolver(correo, true, encoder.codificar(java.util.UUID.randomUUID().toString())))
            .filter(Usuario::puedeRecibirCodigo);''')
edit(p,'return u.isActivo() && u.getRol() != null && u.getRol().isActivo();','return u.puedeRecibirCodigo();')
edit(p,'@Transactional(noRollbackFor = CodigoAccesoInvalidoException.class)','@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED, noRollbackFor = CodigoAccesoInvalidoException.class)')
edit(p,'Usuario usuario = buscar(identificador).orElseThrow(CodigoAccesoInvalidoException::new);','''Usuario usuario = (registroUniversal == null ? buscar(identificador)
            : registroUniversal.resolver(identificador, false, null)).orElseThrow(CodigoAccesoInvalidoException::new);''')
edit(p,'        usuarioRepository.registrarIngreso', '''        if (usuario.getEstadoRegistro() == cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_PENDIENTE)
            usuario = usuarioRepository.guardar(usuario.confirmarRegistro());
        usuarioRepository.registrarIngreso''')
# Preserve origin on every administrative copy; pending cancellation must be explicit.
p='identidad/application/service/AdministrarUsuariosService.java'
edit(p,'actual.getFechaCreacion(), LocalDateTime.now()','actual.getFechaCreacion(), LocalDateTime.now(), actual.getEstadoRegistro()')
edit(p,'        protegerUltimoAdministrador(actual, actual.isActivo(), rol, administradores);','''        if (actual.isAccesoBasico() && (!"USUARIO".equals(rol.getNombre()) || !correo.equalsIgnoreCase(actual.getEmail())))
            throw new IllegalArgumentException("El autorregistro conserva su correo verificado y su rol básico");
        protegerUltimoAdministrador(actual, actual.isActivo(), rol, administradores);''')
edit(p,'        protegerUltimoAdministrador(actual, activo, actual.getRol(), administradores);','''        if (activo && actual.isAccesoBasico() && actual.getEstadoRegistro() != cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_CONFIRMADO)
            throw new IllegalArgumentException("La cuenta requiere verificación de correo");
        protegerUltimoAdministrador(actual, activo, actual.getRol(), administradores);''')
edit(p,'actual.getPasswordHash(), activo, actual.getFechaCreacion(), LocalDateTime.now(), actual.getEstadoRegistro()', '''actual.getPasswordHash(), activo, actual.getFechaCreacion(), LocalDateTime.now(),
                !activo && actual.getEstadoRegistro() == cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_PENDIENTE
                    ? cl.casol.backend.identidad.domain.EstadoRegistro.AUTO_CANCELADO : actual.getEstadoRegistro()''')
# Security derives scope from the database, never a JWT claim or browser role.
p='identidad/infrastructure/security/JwtAuthenticationFilter.java'
s=read(p); start=s.index('        Usuario usuario = usuariosAutenticados.obtener'); end=s.index('        log.debug("JWT: usuario encontrado',start)
s=s[:start]+'''        Usuario usuario = usuarioRepository.buscarFresco(email).orElse(null);

'''+s[end:]; s=s.replace('usuario != null && usuario.isActivo()', 'usuario != null && usuario.puedeIngresar()')
s=s.replace('List.of(authority)','usuario.isAccesoBasico() ? List.of(authority, new SimpleGrantedAuthority("SCOPE_TICKETS_PROPIOS")) : List.of(authority)')
s=s.replace('log.debug("JWT: email extraido={}", email);','log.debug("JWT: identidad extraída");'); write(p,s)
write('identidad/infrastructure/security/AlcanceBasicoFilter.java','''package cl.casol.backend.identidad.infrastructure.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
/** Fail-closed allowlist applied before legacy role rules. Not a servlet bean. */
public final class AlcanceBasicoFilter extends OncePerRequestFilter {
    public static boolean permitido(String method,String path) {
        return "GET".equals(method) && (path.equals("/api/auth/me") || path.equals("/api/health")
            || path.equals("/api/tickets") || path.equals("/api/tickets/conteo") || path.matches("/api/tickets/[1-9][0-9]*"))
            || "POST".equals(method) && path.equals("/api/tickets");
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws IOException,ServletException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("SCOPE_TICKETS_PROPIOS"))
                && !permitido(req.getMethod(),req.getServletPath())) {
            res.sendError(403); return;
        }
        chain.doFilter(req,res);
    }
}
''')
edit('shared/infrastructure/security/SecurityConfig.java','                .addFilterBefore(','''                .addFilterAfter(new cl.casol.backend.identidad.infrastructure.security.AlcanceBasicoFilter(),
                        cl.casol.backend.identidad.infrastructure.security.JwtAuthenticationFilter.class)
                .addFilterBefore(''')
# Additive contracts preserve the old constructor for existing tests/consumers.
for p,old,args,defaults in [
 ('identidad/api/UsuarioPublico.java','Integer id, String nombre, String email, String rol, boolean activo','id,nombre,email,rol,activo',''),
 ('tickets/application/port/out/UsuarioTicket.java','Integer id, String nombre, String email, RolTicket rol, boolean activo','id,nombre,email,rol,activo',''),
 ('tickets/domain/ActorTicket.java','Integer id, String nombre, RolTicket rol','id,nombre,rol','')]:
    name=Path(p).stem
    edit(p,f'{name}({old}) {{',f'{name}({old}, boolean accesoBasico) {{\n    public {name}({old}) {{ this({args},false); }}')
edit('identidad/api/IdentidadFachada.java','rol, usuario.isActivo())','rol, usuario.puedeIngresar(), usuario.isAccesoBasico())')
edit('tickets/infrastructure/integracion/UsuarioConsultaAdapter.java','usuario.activo())','usuario.activo(), usuario.accesoBasico())')
edit('tickets/application/service/ContextoTicketService.java','usuario.nombre(), usuario.rol())','usuario.nombre(), usuario.rol(), usuario.accesoBasico())')
p='identidad/infrastructure/web/dto/LoginResponse.java'
edit(p,'        String rol','        String rol, boolean accesoBasico')
edit(p,') {\n}', ''') {
    public LoginResponse(String token,Integer usuarioId,String nombre,String email,String rol) {
        this(token,usuarioId,nombre,email,rol,false);
    }
}''')
edit('identidad/infrastructure/web/AuthController.java','usuario.getRol().getNombre()','usuario.getRol().getNombre(), usuario.isAccesoBasico()')
edit('identidad/infrastructure/web/AuthController.java','"estado", "AUTENTICADO"','''"estado", "AUTENTICADO",
                        "accesoBasico", String.valueOf(authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("SCOPE_TICKETS_PROPIOS")))''')
# Project a safe domain copy before any response serializer is reached (including JDBC fast path).
edit('tickets/application/port/in/TicketVista.java','    public UsuarioTicket usuario(','''    public TicketVista paraBasico() {
        Ticket t=ticket;
        Ticket seguro=new Ticket(t.getId(),t.getCodigo(),t.getTitulo(),t.getDescripcion(),t.getEstado(),t.getPrioridad(),
            t.getSolicitanteId(),null,null,null,null,null,null,t.getFechaCreacion(),t.getFechaActualizacion(),
            t.getFechaResolucion(),t.getFechaCierre(),java.util.Set.of(),List.of(),List.of());
        UsuarioTicket propio=usuarios.get(t.getSolicitanteId());
        return new TicketVista(seguro,List.of(),propio==null?Map.of():Map.of(t.getSolicitanteId(),propio),
            ClasificacionTicket.VACIA,null,List.of(),List.of());
    }

    public UsuarioTicket usuario(''')
edit('tickets/application/service/ContextoTicketService.java','        List<ComentarioTicket> comentarios = ticket.comentariosVisiblesPara(actor);','''        if (actor.accesoBasico()) return new TicketVista(ticket,List.of(),Map.of(),
            ClasificacionTicket.VACIA,null,List.of()).paraBasico();
        List<ComentarioTicket> comentarios = ticket.comentariosVisiblesPara(actor);''')
p='tickets/application/service/ConsultarTicketsService.java'
edit(p,'        String texto = base.texto()', '''        if (actor.accesoBasico() && (base.etiquetaId()!=null || base.asignadoId()!=null
                || base.texto()!=null && !base.texto().isBlank()))
            throw new cl.casol.backend.tickets.domain.exception.AccesoTicketDenegadoException("Filtro no disponible");
        String texto = base.texto()''')
edit(p,'.map(ConsultarTicketsService::vistaLigera).toList()', '.map(ConsultarTicketsService::vistaLigera).map(v -> actor.accesoBasico()?v.paraBasico():v).toList()')
edit(p,'contexto.resumenes(resultado.contenido()), resultado.pagina()', 'contexto.resumenes(resultado.contenido()).stream().map(v -> actor.accesoBasico()?v.paraBasico():v).toList(), resultado.pagina()')
edit(p,'        List<ComentarioTicket> comentarios = ticket.comentariosVisiblesPara(actor);','''        if (actor.accesoBasico()) return new TicketVista(ticket,List.of(),Map.of(),null,null,List.of()).paraBasico();
        List<ComentarioTicket> comentarios = ticket.comentariosVisiblesPara(actor);''')
edit('tickets/application/service/CrearTicketService.java','        if (comando.hardwareId() != null', '''        if (actor.accesoBasico() && (comando.hardwareId()!=null || comando.sistemaId()!=null || comando.moduloId()!=null))
            throw new AccesoTicketDenegadoException("Clasificación interna no disponible");
        if (comando.hardwareId() != null''')
print('LOCAL_SOURCE_EDITS_PREPARED')
