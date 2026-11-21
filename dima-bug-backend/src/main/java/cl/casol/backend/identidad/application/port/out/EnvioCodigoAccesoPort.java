package cl.casol.backend.identidad.application.port.out;

/**
 * Puerto de salida de envío código acceso.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface EnvioCodigoAccesoPort {

    // Encola el registro.
    default void encolar(cl.casol.backend.identidad.domain.CodigoAcceso registro,
                         String email, String nombre, String codigo, int minutosVigencia) {
        enviar(email, nombre, codigo, minutosVigencia);
    }

    // Envía el email.
    void enviar(String email, String nombre, String codigo, int minutosVigencia);

    // Envía recuperación.
    void enviarRecuperacion(String email, String nombre, String codigo, int minutosVigencia);
}
