import java.sql.*;
import java.net.URI;
import java.util.Set;

/** Solo SELECT; no Spring, códigos, hashes, tokens ni envío. */
class VerificarDestinatarioPrueba {
    public static void main(String[] args) {
        try {
            String url=System.getenv("DB_URL");
            URI target=URI.create(url.substring(5));
            if(!url.startsWith("jdbc:mysql:") || !Set.of("127.0.0.1","localhost").contains(target.getHost())
                || !Set.of(3306,3307).contains(target.getPort()) || !"/dima_bug_staging".equals(target.getPath()))throw new IllegalArgumentException();
            try(var c=DriverManager.getConnection(url,System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"))) {
                c.setReadOnly(true);
                try(var s=c.prepareStatement("SELECT u.usuario_estado,r.rol_estado,(SELECT COUNT(*) FROM u_codigo_acceso c WHERE c.usuario_id=u.usuario_id) FROM u_usuario u JOIN u_rol r ON r.rol_id=u.rol_id WHERE LOWER(TRIM(u.usuario_email))=?")) {
                    s.setString(1,"dimabug507@gmail.com");
                    try(var r=s.executeQuery()) {
                        int n=0;boolean active=false,role=false;long count=0;
                        while(r.next()){n++;active=r.getBoolean(1);role=r.getBoolean(2);count=r.getLong(3);}
                        System.out.println("DESTINATARIO_COMPROBADO: coincidencias="+n+"; usuario_activo="+(n==1&&active)+"; rol_activo="+(n==1&&role)+"; total_codigos="+count+"; solo_lectura=true");
                    }
                }
            }
        } catch(Exception ex) {
            System.err.println("VERIFICACION_NO_COMPLETADA: tipo="+ex.getClass().getSimpleName());System.exit(1);
        }
    }
}
