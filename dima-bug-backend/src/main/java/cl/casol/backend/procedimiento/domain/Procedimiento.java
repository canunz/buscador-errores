package cl.casol.backend.procedimiento.domain;

import cl.casol.backend.identidad.domain.Usuario;
import java.time.LocalDateTime;

public record Procedimiento(Integer id, String nombre, String descripcion, EstadoProcedimiento estado,
        Usuario creadoPor, LocalDateTime fechaCreacion, Usuario modificadoPor,
        LocalDateTime fechaModificacion,DatosEditorialesProcedimiento datos,Integer hardwareId) {
    public Procedimiento(Integer id,String nombre,String descripcion,EstadoProcedimiento estado,Usuario creado,LocalDateTime fecha,Usuario modificado,LocalDateTime fechaModificado,DatosEditorialesProcedimiento datos){this(id,nombre,descripcion,estado,creado,fecha,modificado,fechaModificado,datos,null);}
    public Procedimiento {if(datos==null)datos=DatosEditorialesProcedimiento.inicial();}
    public Procedimiento(Integer id,String nombre,String descripcion,EstadoProcedimiento estado,Usuario creado,LocalDateTime fecha,Usuario modificado,LocalDateTime fechaModificado){this(id,nombre,descripcion,estado,creado,fecha,modificado,fechaModificado,DatosEditorialesProcedimiento.inicial());}
}
