package cl.casol.backend.identidad.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

/** Huella del estado leído: no añade una columna ni sustituye el bloqueo transaccional. */
public record ResponsableAdministrativo(Integer id, Integer departamentoId, String nombre,
        String cargo, String contacto, boolean activo, String revision) {
    public static ResponsableAdministrativo from(Responsable r) {
        String original = Stream.of(r.id(), r.departamentoId(), r.nombre(), r.cargo(), r.contacto(), r.activo())
                .map(v -> v == null ? "-1:" : v.toString().length() + ":" + v).reduce("", String::concat);
        try {
            String revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(original.getBytes(StandardCharsets.UTF_8)));
            return new ResponsableAdministrativo(r.id(), r.departamentoId(), r.nombre(), r.cargo(), r.contacto(), r.activo(), revision);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
