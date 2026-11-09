package cl.casol.backend.correo.application.port.out;
public interface CifradoGmailPort {
    boolean disponible();
    String cifrar(String texto,String contexto);
    String descifrar(String texto,String contexto);
}
