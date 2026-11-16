package cl.casol.backend;

import cl.casol.backend.correo.application.port.out.CorreoTransportePort;
import cl.casol.backend.identidad.infrastructure.correo.EnvioBienvenidaCorreoAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificacionesCorreoCentralTest {
    @Test void bienvenidaUsaTransporteCentralSoloTrasCommit() throws Exception {
        var correo=mock(CorreoTransportePort.class);
        when(correo.disponible()).thenReturn(true); when(correo.remitente()).thenReturn("qa@example.invalid");
        var adaptador=new EnvioBienvenidaCorreoAdapter(correo,"DimaBug","http://localhost:4200",true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            adaptador.enviar("persona@gmail.com","Persona","ADMINISTRADOR");
            verify(correo,never()).enviarAutenticacion(anyString());
            for(var sincronizacion:TransactionSynchronizationManager.getSynchronizations()) sincronizacion.afterCommit();
            var raw=ArgumentCaptor.forClass(String.class);
            verify(correo,timeout(3000)).enviarAutenticacion(raw.capture());
            String mime=new String(Base64.getUrlDecoder().decode(raw.getValue()),StandardCharsets.UTF_8);
            assertTrue(mime.contains("persona@gmail.com")); assertTrue(mime.contains("multipart/"));
            verify(correo,never()).enviar(anyString());
        } finally { TransactionSynchronizationManager.clearSynchronization(); adaptador.destroy(); }
    }
    @Test void bienvenidaNoConfigNoIntentaEnviar() {
        var correo=mock(CorreoTransportePort.class);
        var adaptador=new EnvioBienvenidaCorreoAdapter(correo,"DimaBug","http://localhost:4200",true);
        try { adaptador.enviar("persona@gmail.com","Persona","USUARIO"); verify(correo,never()).enviarAutenticacion(anyString()); }
        finally { adaptador.destroy(); }
    }
}
