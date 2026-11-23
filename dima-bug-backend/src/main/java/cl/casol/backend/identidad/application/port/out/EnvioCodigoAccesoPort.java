package cl.casol.backend.identidad.application.port.out;

public interface EnvioCodigoAccesoPort {

    default void encolar(cl.casol.backend.identidad.domain.CodigoAcceso registro,
                         String email, String nombre, String codigo, int minutosVigencia) {
        enviar(email, nombre, codigo, minutosVigencia);
    }

    void enviar(String email, String nombre, String codigo, int minutosVigencia);

    void enviarRecuperacion(String email, String nombre, String codigo, int minutosVigencia);
}
