import java.sql.*;
public class InspectGuidedContext {
    public static void main(String[] args)throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("REPAIR_DB_URL"),System.getenv("REPAIR_DB_USER"),System.getenv("REPAIR_DB_PASSWORD"))) {
            try(var s=c.createStatement();var r=s.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='pr_paso' AND COLUMN_NAME IN ('paso_configuracion_json','paso_config_historial')) OR (TABLE_NAME='se_ejecucion_paso' AND COLUMN_NAME='contrato_configuracion_json') OR (TABLE_NAME='u_sucursal' AND COLUMN_NAME='destinatarios_json') OR (TABLE_NAME='se_solicitud_evidencia' AND COLUMN_NAME IN ('destinatarios_json','configuracion_json')) OR (TABLE_NAME='se_evento_ejecucion' AND COLUMN_NAME='solicitud_id'))")) {
                int count=0;while(r.next()){count++;System.out.println("schema04="+r.getString(1)+"."+r.getString(2)+";nullable="+r.getString(3));}
                if(count!=7)throw new SQLException("Esquema04 incompleto","SCHEMA",0);
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT procedimiento_id,procedimiento_nombre,procedimiento_estado FROM pr_procedimiento WHERE LOWER(procedimiento_nombre) LIKE '%pos%' ORDER BY procedimiento_id LIMIT 20")){
                while(r.next()){
                    int id=r.getInt(1);System.out.println("procedure="+id+";name="+r.getString(2)+";state="+r.getString(3));
                    try(var p=c.prepareStatement("SELECT paso_id,paso_orden,paso_es_critico,paso_modo_ejecucion,LOWER(paso_instruccion) LIKE '%mueble%',LOWER(paso_instruccion) LIKE '%debian%',LOWER(paso_instruccion) LIKE '%comercial%',LOWER(paso_instruccion) LIKE '%transbank%',LOWER(paso_instruccion) LIKE '%folio%' FROM pr_paso WHERE procedimiento_id=? ORDER BY paso_orden")){
                        p.setInt(1,id);try(var rows=p.executeQuery()){while(rows.next())System.out.println("step="+rows.getInt(1)+";order="+rows.getInt(2)+";critical="+rows.getBoolean(3)+";mode="+rows.getString(4)+";installations="+rows.getBoolean(5)+";debian="+rows.getBoolean(6)+";comercial="+rows.getBoolean(7)+";transbank="+rows.getBoolean(8)+";folios="+rows.getBoolean(9));}
                    }
                }
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT TABLE_NAME,COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME LIKE '%contacto%'")){while(r.next())System.out.println("contact_column="+r.getString(1)+"."+r.getString(2));}
        }catch(SQLException e){System.err.println("sql_state="+e.getSQLState()+";code="+e.getErrorCode());System.exit(2);}
    }
}
