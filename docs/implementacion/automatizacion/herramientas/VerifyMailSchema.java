import java.sql.*;
public class VerifyMailSchema {
    public static void main(String[] args)throws Exception{
        try(var c=DriverManager.getConnection(System.getenv("REPAIR_DB_URL"),System.getenv("REPAIR_DB_USER"),System.getenv("REPAIR_DB_PASSWORD"))){
            c.setReadOnly(true);
            int columnas=0;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COLUMN_NAME,IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='se_solicitud_evidencia' AND COLUMN_NAME IN ('sucursal_id','sucursal_codigo','sucursal_nombre','responsable_nombre')")){
                while(r.next()){columnas++;System.out.println("mail_column="+r.getString(1)+";nullable="+r.getString(2));if(!"YES".equals(r.getString(2)))throw new SQLException("Migración pendiente","SCHEMA");}
            }
            if(columnas!=4)throw new SQLException("Esquema incompleto","SCHEMA");
            System.out.println("mail_schema=OK");
        }catch(SQLException e){System.err.println("sql_state="+e.getSQLState()+";code="+e.getErrorCode());System.exit(2);}
    }
}
