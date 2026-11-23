package cl.casol.backend.identidad.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Decide si un correo puede crear su propia cuenta en DimaBug.
 * Solo los correos de la empresa (por ejemplo nombre@dimarsa.cl) pueden registrarse solos.
 * Los demás (Gmail, Hotmail, etc.) necesitan que el administrador les cree la cuenta.
 */
public final class DominioAutorregistro {

    private DominioAutorregistro() {
    }

    /**
     * @param correo   correo que escribió la persona
     * @param dominios lista separada por comas, por ejemplo "dimarsa.cl"
     * @return true si el dominio del correo está en la lista (se compara exacto, sin mayúsculas)
     */
    public static boolean permitido(String correo, String dominios) {
        if (correo == null || dominios == null) return false;
        String valor = correo.strip().toLowerCase(Locale.ROOT);
        int arroba = valor.lastIndexOf('@');
        if (arroba < 1 || arroba == valor.length() - 1) return false;
        String dominio = valor.substring(arroba + 1);
        return Arrays.stream(dominios.split(","))
                .map(d -> d.strip().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .anyMatch(dominio::equals);
    }
}
