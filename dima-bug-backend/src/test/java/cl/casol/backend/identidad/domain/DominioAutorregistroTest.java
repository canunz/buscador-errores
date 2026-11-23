package cl.casol.backend.identidad.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Solo los correos de la empresa pueden crear su propia cuenta. */
class DominioAutorregistroTest {

    @Test
    void correoDeLaEmpresaPuedeRegistrarse() {
        assertTrue(DominioAutorregistro.permitido("Catalina@DIMARSA.cl", "dimarsa.cl"));
    }

    @Test
    void correosPersonalesNoPuedenRegistrarse() {
        assertFalse(DominioAutorregistro.permitido("persona@gmail.com", "dimarsa.cl"));
        assertFalse(DominioAutorregistro.permitido("persona@hotmail.com", "dimarsa.cl"));
    }

    @Test
    void dominiosParecidosNoPasan() {
        assertFalse(DominioAutorregistro.permitido("persona@dimarsa.cl.falso.com", "dimarsa.cl"));
        assertFalse(DominioAutorregistro.permitido("persona@otrodimarsa.cl", "dimarsa.cl"));
    }

    @Test
    void correosIncompletosNoPasan() {
        assertFalse(DominioAutorregistro.permitido("dimarsa.cl", "dimarsa.cl"));
        assertFalse(DominioAutorregistro.permitido("persona@", "dimarsa.cl"));
        assertFalse(DominioAutorregistro.permitido(null, "dimarsa.cl"));
    }
}
