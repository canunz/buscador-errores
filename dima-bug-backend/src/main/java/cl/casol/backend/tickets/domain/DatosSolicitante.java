package cl.casol.backend.tickets.domain;

import cl.casol.backend.tickets.domain.exception.TicketInvalidoException;

/** Snapshot declared for this incident; never an update of the worker/account profile. */
public record DatosSolicitante(String nombreCompleto, Integer sucursalId, String sucursalNombre,
        Integer departamentoId, String departamentoNombre, String contacto, String equipo, String codigoUsuario) {
    public DatosSolicitante {
        nombreCompleto = obligatorio(nombreCompleto, 150, "El nombre completo es obligatorio (máximo 150 caracteres)");
        sucursalNombre = obligatorio(sucursalNombre, 100, "Selecciona una sucursal activa");
        departamentoNombre = obligatorio(departamentoNombre, 100, "Selecciona un área o departamento activo");
        if (sucursalId == null || sucursalId < 1 || departamentoId == null || departamentoId < 1)
            throw new TicketInvalidoException("Selecciona sucursal y departamento");
        contacto = obligatorio(contacto, 50, "Indica un teléfono o anexo válido");
        if (!contactoValido(contacto)) throw new TicketInvalidoException("Indica un teléfono o anexo válido; por ejemplo +56 9 1234 5678 o anexo 1234");
        equipo = opcional(equipo, 100, "La IP o AnyDesk no puede superar 100 caracteres");
        if (equipo != null && !equipoValido(equipo))
            throw new TicketInvalidoException("Indica una IP válida o un ID AnyDesk de 9 a 10 dígitos");
        codigoUsuario = opcional(codigoUsuario, 80, "El código de usuario no puede superar 80 caracteres");
    }
    public static boolean contactoValido(String valor) {
        if (valor == null) return false;
        String v = valor.strip();
        if (v.length() > 50 || !v.matches("(?i)(?:(?:anexo|ext\\.?|int\\.?)\\s*)?[+0-9(][0-9+() .-]*(?:\\s+(?:anexo|ext\\.?|int\\.?)\\s*[0-9]{2,6})?")) return false;
        int digits = v.replaceAll("[^0-9]", "").length();
        return digits >= 2 && digits <= 18 && v.chars().filter(c -> c == '+').count() <= 1;
    }
    public static boolean equipoValido(String valor) {
        String v = valor.strip();
        if (v.matches("[0-9 ]+") && v.replace(" ", "").matches("[0-9]{9,10}")) return true;
        if (v.contains(":")) {
            // Literal parsing only: no DNS/network calls.
            if (!v.matches("[0-9A-Fa-f:]+") || v.contains(":::")
                    || v.startsWith(":") && !v.startsWith("::") || v.endsWith(":") && !v.endsWith("::")) return false;
            int compressed = v.indexOf("::");
            if (compressed >= 0 && compressed != v.lastIndexOf("::")) return false;
            String[] groups = v.split(":", -1);
            int count = 0;
            for (String group : groups) {
                if (group.isEmpty()) continue;
                if (!group.matches("[0-9A-Fa-f]{1,4}")) return false;
                count++;
            }
            return compressed >= 0 ? count < 8 : count == 8 && !v.startsWith(":") && !v.endsWith(":");
        }
        String[] parts = v.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
    private static String obligatorio(String v, int max, String mensaje) {
        String s = opcional(v, max, mensaje);
        if (s == null) throw new TicketInvalidoException(mensaje);
        return s;
    }
    private static String opcional(String v, int max, String mensaje) {
        if (v == null || v.isBlank()) return null;
        String s = v.strip();
        if (s.length() > max || s.codePoints().anyMatch(Character::isISOControl)) throw new TicketInvalidoException(mensaje);
        return s;
    }
}
