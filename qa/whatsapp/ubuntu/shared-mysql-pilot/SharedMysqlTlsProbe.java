package dimabug.qa;
import java.nio.file.*;
import java.util.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;

/** Cliente QA de lectura: sin contraseñas en argumentos, mensajes de excepción ni stdout. */
public final class SharedMysqlTlsProbe {
    public static void main(String[] args)throws Exception{
        if(args.length!=1||!Set.of("--expect-locked","--query").contains(args[0]))throw new IllegalArgumentException("MODE_REQUIRED");
        Path base=Path.of("/etc/dimabug-wa-pilot");
        var props=new Properties();try(var in=Files.newInputStream(base.resolve("application.properties"))){props.load(in);}
        String url=props.getProperty("spring.datasource.url").replace("${CREDENTIALS_DIRECTORY}",base.toString());
        if(!url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/wa_durable_[a-f0-9]+\\?.*"))throw new IllegalArgumentException("QA_ENDPOINT_REQUIRED");
        String database=url.substring(url.indexOf(":3306/")+6,url.indexOf('?'));
        var auth=new Properties();auth.setProperty("user","wa_qa_pilot");
        auth.setProperty("password",Files.readString(base.resolve("secrets/spring.datasource.password")));
        try(var connection=DriverManager.getConnection(url,auth)){
            if(args[0].equals("--expect-locked"))throw new IllegalStateException("QA_ACCOUNT_MUST_REMAIN_LOCKED");
            String input=new String(System.in.readNBytes(16385),StandardCharsets.UTF_8);
            if(input.length()>16384)throw new IllegalArgumentException("QUERY_LIMIT");
            for(String part:input.split(";")){
                String sql=part.strip();if(sql.isEmpty())continue;
                if(sql.equals("USE `"+database+"`")){connection.setCatalog(database);continue;}
                if(!sql.matches("(?is)^(SELECT|SHOW)\\s+.*"))throw new IllegalArgumentException("READ_ONLY_QUERY_REQUIRED");
                try(var statement=connection.createStatement()){statement.setQueryTimeout(10);
                    try(var rs=statement.executeQuery(sql)){
                        while(rs.next()){
                            var row=new ArrayList<String>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.add(Objects.toString(rs.getString(i),"NULL"));
                            System.out.println(String.join("\t",row));
                        }
                    }
                }
            }
        }catch(SQLException e){
            if(args[0].equals("--expect-locked")&&e.getErrorCode()==3118&&SharedMysqlPinnedSocketFactory.tlsVerified()){
                System.out.println("TLS_CA_PIN_VERIFIED_ACCOUNT_STILL_LOCKED");return;
            }
            System.err.println("QA_MYSQL_ERROR_CODE="+e.getErrorCode());
            Throwable cause=e;
            for(int i=0;i<10&&cause!=null;i++,cause=cause.getCause()){
                String message=cause.getMessage();
                if(message!=null&&message.matches("QA_[A-Z_]+"))System.err.println(message);
            }
            System.exit(1);
        }
    }
}
