package cl.casol.backend.correo.application.port.out;
/**
 * Puerto de salida de cifrado gmail.
 * Dice qué necesita el módulo desde afuera, sin depender de cómo se hace (base de datos, correo, etc.).
 */
public interface CifradoGmailPort {
    // Indica si está disponible.
    boolean disponible();
    // Cifra el texto.
    String cifrar(String texto,String contexto);
    // Descifra el texto.
    String descifrar(String texto,String contexto);
}
