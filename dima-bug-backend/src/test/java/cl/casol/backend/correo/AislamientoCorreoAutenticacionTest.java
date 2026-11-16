package cl.casol.backend.correo;

import cl.casol.backend.correo.application.port.out.*;
import cl.casol.backend.correo.application.service.*;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AislamientoCorreoAutenticacionTest {
    private final GmailSettings config = mock(GmailSettings.class);
    private final GmailTokenStore store = mock(GmailTokenStore.class);
    private final GoogleGmailPort google = mock(GoogleGmailPort.class);
    private final CifradoGmailPort cifrado = mock(CifradoGmailPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"),ZoneOffset.UTC);
    private GmailOAuthService servicio(boolean expira) {
        when(config.configurado()).thenReturn(true); when(config.configuradoParaEnvio()).thenReturn(true); when(config.envioHabilitado()).thenReturn(true);
        when(cifrado.disponible()).thenReturn(true);
        when(store.cuenta()).thenReturn(Optional.of(new GmailTokenStore.Cuenta(1,"ACTIVA","cifrado-qa","refresco-qa",null,
                clock.instant().plusSeconds(expira?-10:3600),1)));
        when(cifrado.descifrar(anyString(),anyString())).thenReturn("token-sintetico");
        return new GmailOAuthService(config,store,google,cifrado,mock(UsuarioRepository.class),clock);
    }
    @Test void rechazoDeCodigosNoBorraCuentaCompartida() {
        var s=servicio(false);
        when(google.enviar(anyString(),anyString())).thenThrow(new GmailException(GmailException.Tipo.AUTORIZACION));
        assertThrows(GmailException.class,()->s.enviarAutenticacion("mime-sintetico"));
        verify(store,never()).invalidar(anyLong());
    }
    @Test void renovacionRechazadaDeCodigosTampocoBorraTokens() {
        var s=servicio(true);
        when(google.renovar(anyString())).thenThrow(new GmailException(GmailException.Tipo.AUTORIZACION));
        assertThrows(GmailException.class,()->s.enviarAutenticacion("mime-sintetico"));
        verify(store,never()).invalidar(anyLong()); verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void evidenciasConservaInvalidacionAnterior() {
        var s=servicio(false);
        when(google.enviar(anyString(),anyString())).thenThrow(new GmailException(GmailException.Tipo.AUTORIZACION));
        assertThrows(GmailException.class,()->s.enviar("mime-sintetico")); verify(store).invalidar(1);
    }
    @Test void cuentaActivaSinConfiguracionNoEsDisponible() {
        var s=servicio(false); when(config.configurado()).thenReturn(false); when(config.configuradoParaEnvio()).thenReturn(false);
        assertFalse(s.disponible()); assertThrows(GmailException.class,()->s.enviarAutenticacion("mime-sintetico"));
        verifyNoInteractions(google);
    }
}
