package cl.casol.backend.identidad.domain;

/** Directorio administrado; un dominio válido no prueba por sí solo identidad ni pertenencia. */
public record Sucursal(Integer id, String codigo, String nombre, String responsableNombre,
        String responsableEmail, boolean activa, Long version,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios,String ciudad,String responsableCargo) {
    public Sucursal(Integer id,String codigo,String nombre,String responsableNombre,String responsableEmail,boolean activa,Long version,java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> destinatarios){this(id,codigo,nombre,responsableNombre,responsableEmail,activa,version,destinatarios,null,null);}
    public Sucursal { destinatarios=cl.casol.backend.shared.domain.DestinatarioPaso.normalizar(destinatarios); }
    public Sucursal(Integer id,String codigo,String nombre,String responsableNombre,String responsableEmail,boolean activa,Long version){this(id,codigo,nombre,responsableNombre,responsableEmail,activa,version,java.util.List.of());}
    public java.util.List<cl.casol.backend.shared.domain.DestinatarioPaso> contactos(){
        var todos=new java.util.ArrayList<>(destinatarios);if(responsableEmail!=null && !responsableEmail.isBlank())todos.add(new cl.casol.backend.shared.domain.DestinatarioPaso(responsableEmail,cl.casol.backend.shared.domain.DestinatarioPaso.Tipo.PRINCIPAL));
        return cl.casol.backend.shared.domain.DestinatarioPaso.normalizar(todos);
    }
}
