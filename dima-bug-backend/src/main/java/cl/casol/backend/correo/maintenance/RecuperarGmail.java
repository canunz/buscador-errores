package cl.casol.backend.correo.maintenance;

import cl.casol.backend.correo.infrastructure.gmail.*;
import cl.casol.backend.correo.application.port.out.GoogleGmailPort;
import cl.casol.backend.correo.application.service.GmailOAuthService;
import com.sun.net.httpserver.*;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Utilidad explícita, sin Spring, jobs, JWT ni endpoint público. Nunca se activa con BackendApplication. */
public final class RecuperarGmail {
    static final List<String> TABLAS = List.of("gm_cuenta_oauth", "gm_estado_oauth", "gm_auditoria_oauth", "u_correo_codigo", "u_codigo_acceso");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String url, user, password;
    private final GmailConfig config;
    private final CifradoGmailAes cifrado;
    private final GoogleGmailHttp google;

    RecuperarGmail(Map<String,String> env) {
        url = env.getOrDefault("DB_URL", ""); user = env.get("DB_USERNAME"); password = env.get("DB_PASSWORD");
        validarDestino(url);
        config = new GmailConfig(true, false, env.getOrDefault("GOOGLE_CLIENT_ID", ""), env.getOrDefault("GOOGLE_CLIENT_SECRET", ""),
                env.getOrDefault("GMAIL_OAUTH_REDIRECT_URI", "http://localhost:9090/login/oauth2/code/google"),
                env.getOrDefault("GMAIL_SENDER_EMAIL", ""), env.getOrDefault("GMAIL_TOKEN_ENCRYPTION_KEY", ""), "http://localhost:4200/admin/gmail");
        cifrado = new CifradoGmailAes(config); google = new GoogleGmailHttp(config, Clock.systemUTC());
    }

    static void validarDestino(String url) {
        try {
            var u = URI.create(url.substring("jdbc:".length()));
            if (!url.startsWith("jdbc:mysql:") || !Set.of("127.0.0.1","localhost").contains(u.getHost())
                    || !Set.of(3306,3307).contains(u.getPort()) || !"/dima_bug_staging".equals(u.getPath()) || u.getUserInfo()!=null)
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Solo admite staging por loopback, puerto 3306/3307"); }
    }

    private Connection conexion() throws SQLException {
        var c=DriverManager.getConnection(url,user,password);
        try(var s=c.createStatement()) { s.execute("SET time_zone = '+00:00'"); }
        catch(SQLException ex) {c.close();throw ex;}
        return c;
    }
    static List<Map<String,Object>> filas(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for (int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            try (var r=s.executeQuery()) {
                var rows=new ArrayList<Map<String,Object>>();
                while(r.next()) {
                    var row=new LinkedHashMap<String,Object>();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                        Object v=r.getObject(i);
                        row.put(r.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),v instanceof Timestamp ? v.toString() : v);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    static int actualizar(Connection c, String sql, Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) {
                if(args[i] instanceof Timestamp t) s.setTimestamp(i+1,t,Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                else s.setObject(i+1,args[i]);
            }
            return s.executeUpdate();
        }
    }
    static void exigirAdmin(Connection c, int actor) throws SQLException {
        if(filas(c,"SELECT u.usuario_id FROM u_usuario u JOIN u_rol r ON r.rol_id=u.rol_id WHERE u.usuario_id=? AND u.usuario_estado=TRUE AND r.rol_estado=TRUE AND UPPER(r.rol_nombre)='ADMINISTRADOR'",actor).size()!=1)
            throw new IllegalStateException("Se requiere administrador activo");
    }
    static long numero(Object n) { return ((Number)n).longValue(); }

    private void inspeccionar() throws SQLException {
        try(var c=conexion()) {
            c.setReadOnly(true);
            System.out.println(JSON.writeValueAsString(Map.of("cuenta",filas(c,"SELECT id,version,estado,(access_token_cifrado IS NOT NULL) AS acceso_presente,(refresh_token_cifrado IS NOT NULL) AS refresco_presente,(revocacion_cifrada IS NOT NULL) AS revocacion_pendiente,expira_en FROM gm_cuenta_oauth WHERE id=1"),
                    "cola",filas(c,"SELECT estado,intentos,COUNT(*) AS cantidad FROM u_correo_codigo GROUP BY estado,intentos"),
                    "auditoria_recuperacion",filas(c,"SELECT actor_id,tipo,fecha FROM gm_auditoria_oauth WHERE tipo IN ('RECUPERACION_AES_PERDIDA','GOOGLE_REVOCACION_NO_COMPROBADA') ORDER BY fecha DESC LIMIT 4"),
                    "administradores",filas(c,"SELECT u.usuario_id,u.usuario_nombre AS nombre FROM u_usuario u JOIN u_rol r ON r.rol_id=u.rol_id WHERE u.usuario_estado=TRUE AND r.rol_estado=TRUE AND UPPER(r.rol_nombre)='ADMINISTRADOR'"))));
        }
    }

    private void respaldar(Path archivo, int actor) throws Exception {
        exigirClave();
        Map<String,Object> snapshot=new LinkedHashMap<>();
        try(var c=conexion()) {
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            exigirAdmin(c,actor);
            for(String tabla:TABLAS) snapshot.put(tabla,filas(c,"SELECT * FROM "+tabla));
            c.rollback();
        }
        String id=UUID.randomUUID().toString();
        String serializado=JSON.writeValueAsString(snapshot);
        String protegido=cifrado.cifrar(serializado,"gmail:recovery:"+id);
        // Verificación completa en memoria antes de crear el archivo; no se imprime el contenido.
        if(!serializado.equals(cifrado.descifrar(protegido,"gmail:recovery:"+id)))
            throw new IllegalStateException("No se verificó el respaldo");
        Files.writeString(archivo,JSON.writeValueAsString(Map.of("format","dimabug-gmail-backup-v1","id",id,
                "createdUtc",Instant.now().toString(),"actor",actor,"ciphertext",protegido)),StandardOpenOption.CREATE_NEW);
        System.out.println("RESPALDO_OK: cifrado, verificado y creado sin sobrescritura");
    }
    private Map<String,Object> abrirRespaldo(Path archivo) throws IOException {
        exigirClave();var e=JSON.readTree(Files.readString(archivo));
        if(!"dimabug-gmail-backup-v1".equals(e.path("format").asText())) throw new IllegalArgumentException("Formato de respaldo inválido");
        return JSON.readValue(cifrado.descifrar(e.path("ciphertext").asText(),"gmail:recovery:"+e.path("id").asText()),Map.class);
    }
    private void exigirClave() { if(!cifrado.disponible()) throw new IllegalStateException("Falta clave de custodia válida"); }

    private void verificar(long version, boolean renovar) throws Exception {
        exigirClave();Map<String,Object> row;
        try(var c=conexion()) {
            c.setReadOnly(true);
            var rows=filas(c,"SELECT * FROM gm_cuenta_oauth WHERE id=1");
            if(rows.size()!=1)throw new IllegalStateException("Cuenta ausente");row=rows.getFirst();
        }
        if(numero(row.get("version"))!=version || !"ACTIVA".equals(row.get("estado")) || row.get("revocacion_cifrada")!=null)
            throw new IllegalStateException("La cuenta no corresponde al checkpoint");
        String acceso=cifrado.descifrar((String)row.get("access_token_cifrado"),"gmail:access");
        String refresco=cifrado.descifrar((String)row.get("refresh_token_cifrado"),"gmail:refresh");
        if(acceso.isBlank() || refresco.isBlank())throw new IllegalStateException("Tokens vacíos");
        if(renovar) {
            var t=google.renovar(refresco);
            if(t.acceso==null || t.acceso.isBlank() || t.expira==null || !t.expira.isAfter(Instant.now().plusSeconds(30))
                    || !(t.scopes.isEmpty() || t.scopes.equals(Set.of(GmailOAuthService.SCOPE))))throw new IllegalStateException("Renovación inválida");
            try(var c=conexion()) {
                c.setAutoCommit(false);
                try {
                    String refreshCipher=t.refresco==null || t.refresco.isBlank() ? (String)row.get("refresh_token_cifrado") : cifrado.cifrar(t.refresco,"gmail:refresh");
                    int n=actualizar(c,"UPDATE gm_cuenta_oauth SET access_token_cifrado=?,refresh_token_cifrado=?,expira_en=?,version=version+1 WHERE id=1 AND version=? AND estado='ACTIVA' AND revocacion_cifrada IS NULL",cifrado.cifrar(t.acceso,"gmail:access"),refreshCipher,Timestamp.from(t.expira),version);
                    if(n!=1)throw new IllegalStateException("Cuenta modificada durante la renovación");
                    actualizar(c,"INSERT INTO gm_auditoria_oauth(actor_id,tipo,fecha) VALUES(?,'TOKEN_RENOVADO',CURRENT_TIMESTAMP)",row.get("autorizado_por"));c.commit();
                } catch(Exception ex) {c.rollback();throw ex;}
            }
        }
        System.out.println(renovar?"RENOVACION_OK: tokens verificados y renovados; ningún correo enviado":"DESCIFRADO_OK: ambos tokens se descifran; vigencia en Google no comprobada; ningún correo enviado");
    }

    static String eventoRevocacion(String declaracion) {
        if (!"--google-revocation-unverified".equals(declaracion))
            throw new IllegalArgumentException("Requiere declarar revocación anterior no comprobada");
        return "GOOGLE_REVOCACION_NO_COMPROBADA";
    }

    static void compararCuenta(Map<String,Object> actual,long version,Map<String,Object> snapshot) {
        var previas=(List<Map<String,Object>>)snapshot.get("gm_cuenta_oauth");
        var previa=previas.stream().filter(r->numero(r.get("id"))==1).findFirst().orElseThrow();
        if(numero(actual.get("version"))!=version || numero(previa.get("version"))!=version
                || !Objects.equals(actual.get("estado"),previa.get("estado"))) throw new IllegalStateException("La cuenta cambió; abortado");
        for(String campo:List.of("access_token_cifrado","refresh_token_cifrado","revocacion_cifrada"))
            if(!Objects.equals(actual.get(campo),previa.get(campo))) throw new IllegalStateException("El respaldo no corresponde a la cuenta actual");
    }

    private void preflight(Path archivo,int actor,long version) throws Exception {
        var snapshot=abrirRespaldo(archivo);
        for(String tabla:TABLAS) if(!(snapshot.get(tabla) instanceof List<?>))throw new IllegalStateException("Respaldo incompleto");
        try(var c=conexion()) {
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            exigirAdmin(c,actor);
            var actual=filas(c,"SELECT * FROM gm_cuenta_oauth WHERE id=1").getFirst();
            compararCuenta(actual,version,snapshot);
            var columna=filas(c,"SELECT DATA_TYPE,CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gm_auditoria_oauth' AND column_name='tipo'");
            if(columna.size()!=1 || !"varchar".equals(columna.getFirst().get("data_type")) || numero(columna.getFirst().get("character_maximum_length"))<29)throw new IllegalStateException("Auditoría incompatible");
            System.out.println("PREFLIGHT_OK: respaldo autenticado y completo; cuenta coincide; id=1; version="+numero(actual.get("version"))+"; estado="+actual.get("estado")+"; auditoría compatible; sin escrituras");
            c.rollback();
        }
    }

    static void preparar(Connection c, int actor, long version, Map<String,Object> snapshot, String declaracion) throws SQLException {
        String evento=eventoRevocacion(declaracion);
        exigirAdmin(c,actor);
        var actuales=filas(c,"SELECT * FROM gm_cuenta_oauth WHERE id=1 FOR UPDATE");
        if(actuales.size()!=1) throw new IllegalStateException("Debe existir exactamente la cuenta 1");
        var actual=actuales.getFirst();
        var previas=(List<Map<String,Object>>)snapshot.get("gm_cuenta_oauth");
        var previa=previas.stream().filter(r->numero(r.get("id"))==1).findFirst().orElseThrow();
        if(numero(actual.get("version"))!=version || numero(previa.get("version"))!=version
                || !Objects.equals(actual.get("estado"),previa.get("estado"))) throw new IllegalStateException("La cuenta cambió; abortado");
        for(String campo:List.of("access_token_cifrado","refresh_token_cifrado","revocacion_cifrada"))
            if(!Objects.equals(actual.get(campo),previa.get(campo))) throw new IllegalStateException("El respaldo no corresponde a la cuenta actual");
        if(!Set.of("ACTIVA","REVOCADA").contains(String.valueOf(actual.get("estado")))) throw new IllegalStateException("La cuenta ya no requiere este reset");
        int n=actualizar(c,"UPDATE gm_cuenta_oauth SET estado='REAUTORIZAR',access_token_cifrado=NULL,refresh_token_cifrado=NULL,revocacion_cifrada=NULL,expira_en=NULL,version=version+1 WHERE id=1 AND version=?",version);
        if(n!=1) throw new IllegalStateException("Actualización concurrente");
        actualizar(c,"UPDATE gm_estado_oauth SET consumido=TRUE WHERE consumido=FALSE AND version_cuenta<=?",version);
        actualizar(c,"UPDATE u_codigo_acceso SET usado=TRUE WHERE usado=FALSE AND codigo_id IN (SELECT codigo_id FROM u_correo_codigo WHERE estado<>'ENVIADO')");
        actualizar(c,"UPDATE u_correo_codigo SET contenido_cifrado=NULL,estado=CASE WHEN estado IN ('PENDIENTE','ERROR_TEMPORAL') THEN 'EXPIRADO' WHEN estado='ENVIANDO' THEN 'INCIERTO' ELSE estado END,actualizado_en=CURRENT_TIMESTAMP WHERE contenido_cifrado IS NOT NULL OR estado IN ('PENDIENTE','ERROR_TEMPORAL','ENVIANDO')");
        actualizar(c,"INSERT INTO gm_auditoria_oauth(actor_id,tipo,fecha) VALUES(?,'RECUPERACION_AES_PERDIDA',CURRENT_TIMESTAMP)",actor);
        actualizar(c,"INSERT INTO gm_auditoria_oauth(actor_id,tipo,fecha) VALUES(?,?,CURRENT_TIMESTAMP)",actor,evento);
    }

    private void restablecer(Path archivo,int actor,long version,String declaracion) throws Exception {
        var snapshot=abrirRespaldo(archivo);
        try(var c=conexion()) {
            c.setAutoCommit(false);
            try { preparar(c,actor,version,snapshot,declaracion);c.commit(); }
            catch(Exception ex) { c.rollback();throw ex; }
        }
        System.out.println("RECUPERACION_LOCAL_OK: estado REAUTORIZAR; versión incrementada; sin envíos");
    }

    static String cookie(String headers) {
        if(headers==null)return null;
        for(String s:headers.split(";")) if(s.strip().startsWith("dimabug_gmail_recovery="))return s.strip().substring("dimabug_gmail_recovery=".length());
        return null;
    }
    static Map<String,String> query(String raw) {
        var q=new HashMap<String,String>();
        if(raw!=null)for(String item:raw.split("&")) {
            var p=item.split("=",2);String k=URLDecoder.decode(p[0],StandardCharsets.UTF_8);
            if(q.put(k,p.length==2?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"")!=null)throw new IllegalArgumentException("Parámetro duplicado");
        }
        return q;
    }
    private static String aleatorio() {
        byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    private static String hash(String s) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
    private static void responder(HttpExchange x,int status,String texto) throws IOException {
        byte[] bytes=texto.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("Referrer-Policy","no-referrer");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'none'");
        x.getResponseHeaders().set("Content-Type","text/plain; charset=UTF-8");
        x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();
    }

    private void autorizar(int actor,long version) throws Exception {
        exigirClave();if(!config.configurado())throw new IllegalStateException("Configuración OAuth incompleta");
        URI cb=URI.create(config.callback);
        if(!"http".equals(cb.getScheme()) || !"localhost".equals(cb.getHost()) || cb.getPort()!=9090)
            throw new IllegalArgumentException("Recuperación solo admite callback http://localhost:9090/login/oauth2/code/google previamente registrado");
        try(var c=conexion()) {
            exigirAdmin(c,actor);var r=filas(c,"SELECT estado,version,revocacion_cifrada FROM gm_cuenta_oauth WHERE id=1").getFirst();
            if(!"REAUTORIZAR".equals(r.get("estado")) || numero(r.get("version"))!=version || r.get("revocacion_cifrada")!=null)
                throw new IllegalStateException("Cuenta no preparada para esta versión");
        }
        var server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),9090),0);
        String state=aleatorio(),browser=aleatorio(),verifier=aleatorio();String sh=hash(state);
        try(var c=conexion()) {
            actualizar(c,"INSERT INTO gm_estado_oauth(estado_hash,navegador_hash,pkce_cifrado,actor_id,expira_en,version_cuenta,consumido) VALUES(?,?,?,?,?,?,FALSE)",
                    sh,hash(browser),cifrado.cifrar(verifier,"gmail:pkce:"+sh),actor,Timestamp.from(Instant.now().plusSeconds(300)),version);
        }
        var done=new CountDownLatch(1);var started=new java.util.concurrent.atomic.AtomicBoolean();
        var success=new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext("/recuperacion/iniciar",x->{
            String site=x.getRequestHeaders().getFirst("Sec-Fetch-Site");
            if(!"localhost:9090".equals(x.getRequestHeaders().getFirst("Host")) || !"GET".equals(x.getRequestMethod()) || (site!=null && !Set.of("none","same-origin").contains(site)) || !started.compareAndSet(false,true)) {responder(x,403,"Inicio no permitido");return;}
            x.getResponseHeaders().set("Set-Cookie","dimabug_gmail_recovery="+browser+"; HttpOnly; SameSite=Lax; Path=/login/oauth2/code/google; Max-Age=300");
            x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("Location",google.autorizacion(state,verifier));
            x.sendResponseHeaders(303,-1);x.close();
        });
        server.createContext("/login/oauth2/code/google",x->{
            try {
                var q=query(x.getRequestURI().getRawQuery());
                if(!"localhost:9090".equals(x.getRequestHeaders().getFirst("Host")) || !"GET".equals(x.getRequestMethod()) || q.get("code")==null || q.get("code").isBlank() || q.get("code").length()>4096 || !state.equals(q.get("state")) || !browser.equals(cookie(x.getRequestHeaders().getFirst("Cookie"))) || q.containsKey("error"))throw new IllegalArgumentException();
                try(var c=conexion()) {
                    c.setAutoCommit(false);exigirAdmin(c,actor);
                    int n=actualizar(c,"UPDATE gm_estado_oauth SET consumido=TRUE WHERE estado_hash=? AND navegador_hash=? AND consumido=FALSE AND expira_en>CURRENT_TIMESTAMP AND version_cuenta=?",sh,hash(browser),version);
                    if(n!=1)throw new IllegalStateException();c.commit();
                }
                GoogleGmailPort.Tokens t=google.intercambiar(q.get("code"),state,verifier);
                if(t.refresco==null || t.refresco.isBlank() || !t.scopes.equals(Set.of(GmailOAuthService.SCOPE)) || t.expira==null || !t.expira.isAfter(Instant.now().plusSeconds(30)))throw new IllegalStateException();
                try(var c=conexion()) {
                    c.setAutoCommit(false);exigirAdmin(c,actor);
                    int n=actualizar(c,"UPDATE gm_cuenta_oauth SET estado='ACTIVA',access_token_cifrado=?,refresh_token_cifrado=?,expira_en=?,autorizado_por=?,version=version+1 WHERE id=1 AND version=? AND estado='REAUTORIZAR' AND revocacion_cifrada IS NULL",cifrado.cifrar(t.acceso,"gmail:access"),cifrado.cifrar(t.refresco,"gmail:refresh"),Timestamp.from(t.expira),actor,version);
                    if(n!=1)throw new IllegalStateException();
                    actualizar(c,"INSERT INTO gm_auditoria_oauth(actor_id,tipo,fecha) VALUES(?,'CUENTA_AUTORIZADA',CURRENT_TIMESTAMP)",actor);c.commit();
                }
                success.set(true);responder(x,200,"Autorización guardada. No se envió ningún correo. Puede cerrar esta pestaña.");
            } catch(Exception ex) {responder(x,400,"No se completó la autorización. No se muestran respuestas, códigos ni tokens. Revise el estado con la herramienta.");}
            finally { done.countDown(); }
        });
        server.start();System.out.println("OAUTH_ESPERANDO: abra http://localhost:9090/recuperacion/iniciar; máximo 5 minutos; sin jobs ni envío de correo");
        try { done.await(310,TimeUnit.SECONDS); } finally { server.stop(0); }
        if(!success.get())throw new IllegalStateException("Autorización no completada");
        System.out.println("OAUTH_OK: tokens nuevos cifrados; cuenta ACTIVA; no se envió correo");
    }

    public static void main(String[] args) {
        try {
            var tool=new RecuperarGmail(System.getenv());
            switch(args.length==0?"inspect":args[0]) {
                case "inspect" -> tool.inspeccionar();
                case "backup" -> tool.respaldar(Path.of(args[1]),Integer.parseInt(args[2]));
                case "preflight" -> tool.preflight(Path.of(args[1]),Integer.parseInt(args[2]),Long.parseLong(args[3]));
                case "reset" -> {
                    if(args.length!=6 || !"--maintenance-confirmed".equals(args[5]))throw new IllegalArgumentException("Requiere declaración de revocación y mantenimiento");
                    eventoRevocacion(args[4]);
                    tool.restablecer(Path.of(args[1]),Integer.parseInt(args[2]),Long.parseLong(args[3]),args[4]);
                }
                case "authorize" -> tool.autorizar(Integer.parseInt(args[1]),Long.parseLong(args[2]));
                case "verify" -> tool.verificar(Long.parseLong(args[1]),false);
                case "refresh" -> {
                    if(args.length!=3 || !"--maintenance-confirmed".equals(args[2]))throw new IllegalArgumentException("Requiere mantenimiento");
                    tool.verificar(Long.parseLong(args[1]),true);
                }
                default -> throw new IllegalArgumentException("Operación desconocida");
            }
        } catch(Exception ex) {
            // Nada de stacktraces: excepciones JDBC/OAuth pueden incluir datos de conexión.
            System.err.println("RECUPERACION_NO_COMPLETADA: tipo="+ex.getClass().getSimpleName());System.exit(2);
        }
    }
}
