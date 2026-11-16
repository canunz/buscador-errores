import java.sql.*;
public class VerifyGmailSchema {
    public static void main(String[] args)throws Exception{
        try(var c=DriverManager.getConnection(System.getenv("REPAIR_DB_URL"),System.getenv("REPAIR_DB_USER"),System.getenv("REPAIR_DB_PASSWORD"))){
            c.setReadOnly(true);int tablas=0;boolean prueba=args.length>0 && args[0].equals("gmail-test-inspect");
            String sql=prueba?"SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('gm_cuenta_oauth','gm_estado_oauth','gm_auditoria_oauth','gm_prueba_correo','gm_prueba_evento')":"SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('gm_cuenta_oauth','gm_estado_oauth','gm_auditoria_oauth')";
            try(var s=c.createStatement();var r=s.executeQuery(sql)){
                while(r.next()){tablas++;System.out.println("gmail_table="+r.getString(1));}
            }
            if(tablas!=(prueba?5:3))throw new SQLException("Migración pendiente","SCHEMA");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT estado FROM gm_cuenta_oauth WHERE id=1")){if(!r.next())throw new SQLException("Falta cuenta inicial","SCHEMA");System.out.println("gmail_account_status="+r.getString(1));}
            System.out.println("gmail_schema=OK");
            if(prueba){try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM gm_prueba_correo")){r.next();System.out.println("gmail_test_records="+r.getInt(1));}}
        }catch(SQLException e){System.err.println("sql_state="+e.getSQLState()+";code="+e.getErrorCode());System.exit(2);}
    }
}
