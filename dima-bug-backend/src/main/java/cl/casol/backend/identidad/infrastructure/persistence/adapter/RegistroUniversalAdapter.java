package cl.casol.backend.identidad.infrastructure.persistence.adapter;

import cl.casol.backend.identidad.application.port.out.RegistroUniversalPort;
import cl.casol.backend.identidad.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Adaptador de base de datos para registro universal.
 * Implementa el puerto de salida usando JPA y convierte entre entidades y objetos del dominio.
 */
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
    // Resuelve el email.
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<Usuario> resolver(String email, boolean crear, String hashAleatorio) {
        String e=email==null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (e.length()>120 || !correoValido(e)) return Optional.empty();
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
    // Devuelve cuenta.
    private long cuenta(String condicion,Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM u_usuario WHERE "+condicion,Long.class,args);
    }
    // Indica si correo valido.
    private boolean correoValido(String email) {
        if (!email.contains("@") || email.chars().anyMatch(Character::isISOControl)) return false;
        try { new jakarta.mail.internet.InternetAddress(email,true).validate(); return true; }
        catch (jakarta.mail.internet.AddressException ex) { return false; }
    }
    // Busca con los datos recibidos.
    private Optional<Usuario> buscar(String e) {
        // Lock only the user row, then read fresh role metadata. No authentication cache.
        List<Integer> ids=jdbc.queryForList("SELECT usuario_id FROM u_usuario WHERE LOWER(usuario_email)=? FOR UPDATE",Integer.class,e);
        if (ids.isEmpty()) return Optional.empty();
        if (ids.size()!=1) throw new IllegalStateException("EMAIL_AMBIGUO");
        return jdbc.query("SELECT u.*,r.rol_nombre,r.rol_descripcion,r.rol_estado FROM u_usuario u JOIN u_rol r ON r.rol_id=u.rol_id WHERE u.usuario_id=?",
            (rs,n)->{
                Usuario usuario = new Usuario(rs.getInt("usuario_id"),new Rol(rs.getInt("rol_id"),rs.getString("rol_nombre"),rs.getString("rol_descripcion"),rs.getBoolean("rol_estado"),null,null),
                    rs.getString("usuario_nombre"),rs.getString("usuario_email"),rs.getString("usuario_password_hash"),rs.getBoolean("usuario_estado"),
                    rs.getTimestamp("usuario_fecha_creacion").toLocalDateTime(),rs.getTimestamp("usuario_fecha_actualizacion").toLocalDateTime(),EstadoRegistro.valueOf(rs.getString("registro_estado")));
                java.sql.Timestamp ingreso = rs.getTimestamp("usuario_ultimo_ingreso");
                return ingreso == null ? usuario : usuario.conUltimoIngreso(ingreso.toLocalDateTime());
            },ids.getFirst()).stream().findFirst();
    }
}
