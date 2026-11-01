package cl.casol.backend.identidad.application.port.out;

/** Avisa por correo a una persona que se le acaba de crear una cuenta en DimaBug. */
public interface BienvenidaUsuarioPort {

    /** Sin aviso (pruebas o cuando no hay correo configurado). */
    BienvenidaUsuarioPort NINGUNO = (email, nombre, rol) -> { };

    void enviar(String email, String nombre, String rol);
}
