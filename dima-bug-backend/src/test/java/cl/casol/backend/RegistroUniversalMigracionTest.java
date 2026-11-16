package cl.casol.backend;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** ALTER clauses tested on disposable H2; combined DDL and functional index need native MySQL validation. */
class RegistroUniversalMigracionTest {
 @Test void migraLegadosSinAlterarRolEstadoNiHashYRechazaEstadosInseguros() throws Exception {
  String sql=Files.readString(Path.of("src/main/resources/db/migration/V20261115_01__registro_universal.sql"));
  sql=sql.replaceAll("(?m)^--.*$","").strip();
  try(var c=DriverManager.getConnection("jdbc:h2:mem:migracion_universal;MODE=MySQL","sa","");var s=c.createStatement()) {
   s.execute("CREATE TABLE u_usuario(usuario_id INT PRIMARY KEY,rol_id INT,usuario_email VARCHAR(120),usuario_password_hash VARCHAR(255),usuario_estado INT,usuario_fecha_creacion TIMESTAMP)");
   s.execute("INSERT INTO u_usuario VALUES(1,1,'legacy@example.invalid','hash-intacto',1,CURRENT_TIMESTAMP),(2,4,'blocked@example.invalid','hash-bloqueado',0,CURRENT_TIMESTAMP)");
   String alter=sql.substring(0,sql.indexOf(';'));
   String[] clauses=alter.substring("ALTER TABLE u_usuario".length()).split(",\\s*ADD ");
   s.execute("ALTER TABLE u_usuario "+clauses[0]);
   for(int i=1;i<clauses.length;i++)s.execute("ALTER TABLE u_usuario ADD "+clauses[i]);
   try(var rs=s.executeQuery("SELECT * FROM u_usuario ORDER BY usuario_id")) {
    assertTrue(rs.next());assertEquals(1,rs.getInt("rol_id"));assertEquals(1,rs.getInt("usuario_estado"));assertEquals("hash-intacto",rs.getString("usuario_password_hash"));assertEquals("ADMINISTRADO",rs.getString("registro_estado"));
    assertTrue(rs.next());assertEquals(4,rs.getInt("rol_id"));assertEquals(0,rs.getInt("usuario_estado"));assertEquals("hash-bloqueado",rs.getString("usuario_password_hash"));assertEquals("ADMINISTRADO",rs.getString("registro_estado"));
   }
   assertThrows(SQLException.class,()->s.execute("UPDATE u_usuario SET registro_estado='AUTO_PENDIENTE' WHERE usuario_id=1"));
   assertThrows(SQLException.class,()->s.execute("UPDATE u_usuario SET registro_estado='OTRO' WHERE usuario_id=2"));
   s.execute("UPDATE u_usuario SET registro_estado='AUTO_CANCELADO' WHERE usuario_id=2");
  }
 }
 @Test void claveCanonicaRechazaDuplicadosEnModeloEquivalenteH2() throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:canonico;MODE=MySQL","sa","");var s=c.createStatement()) {
   s.execute("CREATE TABLE cuentas(email VARCHAR(120), normalizado VARCHAR(120) GENERATED ALWAYS AS (LOWER(TRIM(email))), CONSTRAINT uq_normalizado UNIQUE(normalizado))");
   s.execute("INSERT INTO cuentas(email) VALUES('Persona@Gmail.com')");
   assertThrows(SQLException.class,()->s.execute("INSERT INTO cuentas(email) VALUES(' persona@gmail.com ' )"));
  }
 }
}
