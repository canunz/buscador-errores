package cl.casol.backend.identidad.application.port.out;

public interface EnvioCodigoAccesoPort {

    void enviar(String email, String nombre, String codigo, int minutosVigencia);

    void enviarRecuperacion(String email, String nombre, String codigo, int minutosVigencia);
}
