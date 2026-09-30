package cl.casol.backend.identidad.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptPasswordEncoderAdapterTest {

    @Test
    void codificaPasswordConBCrypt() {
        var adapter = new BCryptPasswordEncoderAdapter(new BCryptPasswordEncoder());

        String hash = adapter.codificar("ClaveInicialSegura");

        assertNotEquals("ClaveInicialSegura", hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(adapter.coincide("ClaveInicialSegura", hash));
    }
}
