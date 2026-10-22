package cl.casol.backend;
import cl.casol.backend.conocimiento.infrastructure.web.dto.GuardarConocimientoRequest;
import cl.casol.backend.identidad.infrastructure.web.dto.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LimitesMvpP0Test {
    @Test void tituloConocimientoAcepta150YRechaza151() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var v=factory.getValidator();
            assertTrue(v.validate(new GuardarConocimientoRequest("T".repeat(150),"D",null,null,null,null,null)).isEmpty());
            assertFalse(v.validate(new GuardarConocimientoRequest("T".repeat(151),"D",null,null,null,null,null)).isEmpty());
        }
    }
    @Test void departamentoCrearYEditarAceptan100Rechazan101() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var v=factory.getValidator();
            assertTrue(v.validate(new CrearDepartamentoRequest("D".repeat(100))).isEmpty());
            assertTrue(v.validate(new EditarDepartamentoRequest("D".repeat(100),0L)).isEmpty());
            assertFalse(v.validate(new CrearDepartamentoRequest("D".repeat(101))).isEmpty());
            assertFalse(v.validate(new EditarDepartamentoRequest("D".repeat(101),0L)).isEmpty());
        }
    }
}
