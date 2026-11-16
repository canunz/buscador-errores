package cl.casol.backend.correo.maintenance;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecuperarGmailTest {
    private Connection datos() throws SQLException {
        var c=DriverManager.getConnection("jdbc:h2:mem:recuperacion_"+UUID.randomUUID()+";MODE=MySQL","sa","");
        var s=c.createStatement();
        s.execute("CREATE TABLE u_rol(rol_id INT,rol_nombre VARCHAR(40),rol_estado BOOLEAN)");
        s.execute("CREATE TABLE u_usuario(usuario_id INT,rol_id INT,usuario_estado BOOLEAN)");
        s.execute("INSERT INTO u_rol VALUES(1,'ADMINISTRADOR',TRUE)");s.execute("INSERT INTO u_usuario VALUES(7,1,TRUE)");
        s.execute("CREATE TABLE gm_cuenta_oauth(id INT PRIMARY KEY,version BIGINT,estado VARCHAR(20),access_token_cifrado VARCHAR(100),refresh_token_cifrado VARCHAR(100),revocacion_cifrada VARCHAR(100),expira_en TIMESTAMP)");
        s.execute("INSERT INTO gm_cuenta_oauth VALUES(1,8,'ACTIVA','cifrado-sintetico-acceso','cifrado-sintetico-refresco',NULL,CURRENT_TIMESTAMP)");
        s.execute("CREATE TABLE gm_estado_oauth(estado_hash VARCHAR(100),version_cuenta BIGINT,consumido BOOLEAN)");
        s.execute("INSERT INTO gm_estado_oauth VALUES('anterior',8,FALSE)");
        s.execute("CREATE TABLE u_codigo_acceso(codigo_id BIGINT,usado BOOLEAN)");
        s.execute("INSERT INTO u_codigo_acceso VALUES(1,FALSE),(2,FALSE)");
        s.execute("CREATE TABLE u_correo_codigo(codigo_id BIGINT,estado VARCHAR(30),contenido_cifrado VARCHAR(100),actualizado_en TIMESTAMP)");
        s.execute("INSERT INTO u_correo_codigo VALUES(1,'PENDIENTE','mime-sintetico',CURRENT_TIMESTAMP),(2,'ENVIADO',NULL,CURRENT_TIMESTAMP)");
        s.execute("CREATE TABLE gm_auditoria_oauth(actor_id INT,tipo VARCHAR(40),fecha TIMESTAMP)");
        c.setAutoCommit(false);return c;
    }
    private Map<String,Object> snapshot(Connection c) throws SQLException {
        return Map.of("gm_cuenta_oauth",RecuperarGmail.filas(c,"SELECT * FROM gm_cuenta_oauth WHERE id=1"));
    }
    @Test void callbackRechazaParametrosDuplicadosYExtraeSoloSuCookie() {
        assertThrows(IllegalArgumentException.class,()->RecuperarGmail.query("state=primero&state=segundo"));
        assertEquals("valor-sintetico",RecuperarGmail.cookie("otra=x; dimabug_gmail_recovery=valor-sintetico; mas=y"));
        assertNull(RecuperarGmail.cookie("otra=valor-sintetico"));
    }
    @Test void rechazaDeclaracionesQueNoExpresanIncertidumbre() throws Exception {
        try(var c=datos()) {
            for(String flag:List.of("", "--google-revoked-confirmed"))
                assertThrows(IllegalArgumentException.class,()->RecuperarGmail.preparar(c,7,8,snapshot(c),flag));
            assertEquals("ACTIVA",RecuperarGmail.filas(c,"SELECT estado FROM gm_cuenta_oauth").getFirst().get("estado"));
            assertEquals(0,RecuperarGmail.filas(c,"SELECT * FROM gm_auditoria_oauth").size());
        }
    }
    @Test void admiteSoloDestinoStagingLoopback() {
        RecuperarGmail.validarDestino("jdbc:mysql://127.0.0.1:3307/dima_bug_staging");
        for(String u:List.of("jdbc:mysql://servidor:3306/dima_bug_staging","jdbc:mysql://localhost:3306/produccion","jdbc:h2:mem:test","jdbc:mysql://user:pass@localhost:3307/dima_bug_staging"))
            assertThrows(IllegalArgumentException.class,()->RecuperarGmail.validarDestino(u));
    }
    @Test void resetAtomicoConservaCuentaYAuditaSinDescifrarTokens() throws Exception {
        try(var c=datos()) {
            RecuperarGmail.preparar(c,7,8,snapshot(c),"--google-revocation-unverified");c.commit();
            var row=RecuperarGmail.filas(c,"SELECT * FROM gm_cuenta_oauth").getFirst();
            assertEquals("REAUTORIZAR",row.get("estado"));assertEquals(9,RecuperarGmail.numero(row.get("version")));
            assertNull(row.get("access_token_cifrado"));assertNull(row.get("refresh_token_cifrado"));assertNull(row.get("revocacion_cifrada"));
            assertEquals(true,RecuperarGmail.filas(c,"SELECT consumido FROM gm_estado_oauth").getFirst().get("consumido"));
            assertEquals("EXPIRADO",RecuperarGmail.filas(c,"SELECT estado FROM u_correo_codigo WHERE codigo_id=1").getFirst().get("estado"));
            assertEquals(false,RecuperarGmail.filas(c,"SELECT usado FROM u_codigo_acceso WHERE codigo_id=2").getFirst().get("usado"));
            assertEquals(2,RecuperarGmail.filas(c,"SELECT * FROM gm_auditoria_oauth").size());
            assertEquals(1,RecuperarGmail.filas(c,"SELECT * FROM gm_auditoria_oauth WHERE tipo='GOOGLE_REVOCACION_NO_COMPROBADA' AND actor_id=7").size());
            assertEquals(0,RecuperarGmail.filas(c,"SELECT * FROM gm_auditoria_oauth WHERE tipo LIKE '%CONFIRMADA%'").size());
        }
    }
    @Test void versionDistintaNoTocaDatos() throws Exception {
        try(var c=datos()) {
            var backup=snapshot(c);
            assertThrows(IllegalStateException.class,()->RecuperarGmail.preparar(c,7,7,backup,"--google-revocation-unverified"));c.rollback();
            assertEquals("ACTIVA",RecuperarGmail.filas(c,"SELECT estado FROM gm_cuenta_oauth").getFirst().get("estado"));
            assertEquals(0,RecuperarGmail.filas(c,"SELECT * FROM gm_auditoria_oauth").size());
        }
    }
    @Test void exigeAdministradorActivo() throws Exception {
        try(var c=datos()) { assertThrows(IllegalStateException.class,()->RecuperarGmail.preparar(c,99,8,snapshot(c),"--google-revocation-unverified"));c.rollback(); }
    }
    @Test void respaldoDeOtraCuentaNoSeAcepta() throws Exception {
        try(var c=datos()) {
            var backup=snapshot(c);
            RecuperarGmail.actualizar(c,"UPDATE gm_cuenta_oauth SET refresh_token_cifrado='otro-cifrado-sintetico'");
            assertThrows(IllegalStateException.class,()->RecuperarGmail.preparar(c,7,8,backup,"--google-revocation-unverified"));c.rollback();
        }
    }
}
