import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Reparación aditiva acotada. Credenciales solo desde variables de entorno del proceso hijo. */
public class RepairExecutionSchema {
    static final LinkedHashMap<String,String> COLUMNS=new LinkedHashMap<>();
    static {
        COLUMNS.put("contrato_version","INT NULL");
        COLUMNS.put("contrato_orden","INT NULL");
        COLUMNS.put("contrato_instruccion","TEXT NULL");
        COLUMNS.put("contrato_critico","BOOLEAN NULL");
        COLUMNS.put("contrato_modo","VARCHAR(30) NULL");
        COLUMNS.put("estado_detallado","VARCHAR(30) NULL");
        COLUMNS.put("revision","BIGINT NOT NULL DEFAULT 0");
    }
    static Set<String> columns(Connection c) throws SQLException {
        var result=new TreeSet<String>();
        try(var s=c.prepareStatement("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='se_ejecucion_paso'")) {
            try(var r=s.executeQuery()) { while(r.next())result.add(r.getString(1)); }
        }
        return result;
    }
    static List<String> orderedColumns(Connection c) throws SQLException {
        var result=new ArrayList<String>();
        try(var s=c.prepareStatement("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='se_ejecucion_paso' ORDER BY ORDINAL_POSITION");var r=s.executeQuery()) {
            while(r.next()) {
                String name=r.getString(1);
                if(!name.matches("[A-Za-z0-9_]+"))throw new IllegalStateException("Nombre de columna no esperado");
                result.add(name);
            }
        }
        return result;
    }
    static String contentDigest(Connection c,List<String> originalColumns) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        // Todos los campos anteriores a la operación, incluidos snapshots legados presentes en el esquema físico.
        String projection=String.join(",",originalColumns.stream().map(name->"`"+name+"`").toList());
        try(var s=c.createStatement();var r=s.executeQuery("SELECT "+projection+" FROM se_ejecucion_paso ORDER BY ejecucion_paso_id")) {
            while(r.next()) for(int col=1;col<=originalColumns.size();col++) {
                String value=r.getString(col);
                if(value==null)digest.update((byte)0);
                else {
                    digest.update((byte)1);byte[] data=value.getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());digest.update(data);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static long count(Connection c) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM se_ejecucion_paso")) { r.next();return r.getLong(1); }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !Set.of("inspect","apply","verify").contains(args[0]))throw new IllegalArgumentException("Modo esperado: inspect, apply o verify");
        try(var c=DriverManager.getConnection(System.getenv("REPAIR_DB_URL"),System.getenv("REPAIR_DB_USER"),System.getenv("REPAIR_DB_PASSWORD"))) {
            String database;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT DATABASE(),VERSION()")) {
                r.next();database=r.getString(1);System.out.println("schema="+database);System.out.println("mysql_version="+r.getString(2));
            }
            if(!Objects.equals(database,System.getenv("REPAIR_EXPECTED_SCHEMA")))throw new IllegalStateException("El esquema conectado no coincide con el autorizado");
            var existing=columns(c);
            if(existing.isEmpty())throw new IllegalStateException("No existe se_ejecucion_paso en el esquema autorizado");
            var missing=new ArrayList<String>();
            for(String name:COLUMNS.keySet())if(!existing.contains(name))missing.add(name);
            System.out.println("missing_columns="+String.join(",",missing));
            var originalColumns=orderedColumns(c);
            System.out.println("original_columns="+String.join(",",originalColumns));
            long beforeCount=count(c);String beforeDigest=contentDigest(c,originalColumns);
            var legacyColumns=originalColumns.stream().filter(name->!COLUMNS.containsKey(name)).toList();
            System.out.println("pre_migration_fields_sha256="+contentDigest(c,legacyColumns));
            System.out.println("rows_before="+beforeCount);System.out.println("original_fields_sha256_before="+beforeDigest);
            try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE se_ejecucion_paso")) { r.next();System.out.println("ddl_before="+r.getString(2).replace('\n',' ')); }
            if(args[0].equals("apply") && !missing.isEmpty()) {
                var additions=new ArrayList<String>();
                for(String name:missing)additions.add("ADD COLUMN `"+name+"` "+COLUMNS.get(name));
                try(var s=c.createStatement()) { s.setQueryTimeout(60);s.execute("ALTER TABLE se_ejecucion_paso "+String.join(", ",additions)); }
                System.out.println("added_columns="+String.join(",",missing));
            } else System.out.println("added_columns=");
            var after=columns(c);
            if(!args[0].equals("inspect") && !after.containsAll(COLUMNS.keySet()))throw new IllegalStateException("El esquema aún está incompleto");
            long afterCount=count(c);String afterDigest=contentDigest(c,originalColumns);
            System.out.println("rows_after="+afterCount);System.out.println("original_fields_sha256_after="+afterDigest);
            if(args[0].equals("apply") && (beforeCount!=afterCount || !beforeDigest.equals(afterDigest)))
                throw new IllegalStateException("El contenido anterior cambió durante la operación; revisar actividad concurrente antes de atribuir una causa");
            if(after.containsAll(COLUMNS.keySet())) {
                try(var s=c.createStatement();var r=s.executeQuery("SELECT contrato_version,contrato_orden,contrato_instruccion,contrato_critico,contrato_modo,estado_detallado,revision FROM se_ejecucion_paso LIMIT 0")) {
                    System.out.println("projection_query=OK");
                }
            }
            try(var s=c.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='u_sucursal'" );var r=s.executeQuery()) {
                r.next();System.out.println("u_sucursal_exists="+(r.getInt(1)==1));
            }
            System.out.println("result=OK");
        } catch(SQLException e) {
            // Nunca imprimir una URL de conexión, credenciales o datos de filas desde una excepción.
            System.err.println("database_error_state="+e.getSQLState()+";code="+e.getErrorCode());System.exit(2);
        }
    }
}
