package cl.casol.backend;

import cl.casol.backend.shared.infrastructure.security.LimiteAutenticacionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class LimiteAutenticacionFilterTest {
    @Test void limitaCuerposInclusoSinContentLengthConfiable() throws Exception {
        var r=new MockHttpServletRequest("POST","/api/auth/codigo");
        r.setContent(new byte[4097]);
        var s=new MockHttpServletResponse();
        new LimiteAutenticacionFilter(20).doFilter(r,s,(a,b)->fail("No debe llegar al controlador"));
        assertEquals(413,s.getStatus());
    }
    @Test void limitaSinConfiarEnCabeceraDeIpYNoAfectaEvidencias() throws Exception {
        var filter = new LimiteAutenticacionFilter(2);
        for(int n=0;n<3;n++) {
            var r=new MockHttpServletRequest("POST","/api/auth/codigo");
            r.setRemoteAddr("127.0.0.1"); r.addHeader("X-Forwarded-For","10.0.0."+n);
            var s=new MockHttpServletResponse(); filter.doFilter(r,s,(a,b)->{});
            assertEquals(n<2?200:429,s.getStatus());
        }
        var s=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST","/api/ejecuciones/1/pasos/1/correo"),s,(a,b)->{});
        assertEquals(200,s.getStatus());
    }
}
