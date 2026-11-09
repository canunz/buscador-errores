package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.CodigoAccesoRepository;
import cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort;
import cl.casol.backend.identidad.application.port.out.PasswordEncoderPort;
import cl.casol.backend.identidad.application.port.out.UsuarioRepository;
import cl.casol.backend.identidad.domain.CodigoAcceso;
import cl.casol.backend.identidad.domain.Rol;
import cl.casol.backend.identidad.domain.Usuario;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import cl.casol.backend.identidad.domain.exception.CorreoNoRegistradoException;
import cl.casol.backend.identidad.domain.exception.CredencialesInvalidasException;
import cl.casol.backend.identidad.domain.exception.ReenvioCodigoAnticipadoException;
import cl.casol.backend.identidad.domain.exception.UsuarioInactivoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutenticarUsuarioServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CodigoAccesoRepository codigos = mock(CodigoAccesoRepository.class);
    private final EnvioCodigoAccesoPort envio = mock(EnvioCodigoAccesoPort.class);
    private final PasswordEncoderPort encoder = mock(PasswordEncoderPort.class);
    private final AutenticarUsuarioService service =
            new AutenticarUsuarioService(usuarios, codigos, envio, encoder, RELOJ, 5, 60);

    @Test
    void solicitarGeneraCodigoDeSeisDigitosQueVenceEnCincoMinutosYLoEnvia() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarUltimo(1)).thenReturn(Optional.empty());
        when(encoder.codificar(anyString())).thenReturn("hash-codigo");

        SolicitudCodigo solicitud = service.solicitarCodigo("user@example.com");

        assertEquals(300, solicitud.segundosVigencia());
        assertEquals(60, solicitud.segundosReenvio());
        ArgumentCaptor<String> codigoEnviado = ArgumentCaptor.forClass(String.class);
        verify(envio).enviar(eq("user@example.com"), eq("Usuario"), codigoEnviado.capture(), eq(5));
        assertTrue(codigoEnviado.getValue().matches("\\d{6}"));
        verify(encoder).codificar(codigoEnviado.getValue());
        verify(codigos).invalidarPendientes(1);
        ArgumentCaptor<CodigoAcceso> guardado = ArgumentCaptor.forClass(CodigoAcceso.class);
        verify(codigos).guardar(guardado.capture());
        assertEquals("hash-codigo", guardado.getValue().codigoHash());
        assertEquals(AHORA.plusMinutes(5), guardado.getValue().expiraEn());
        assertFalse(guardado.getValue().usado());
    }

    @Test
    void solicitarConCorreoNoRegistradoSeRechazaSinEnviarNada() {
        when(usuarios.buscarPorEmail("nadie@example.com")).thenReturn(Optional.empty());

        assertThrows(CorreoNoRegistradoException.class, () -> service.solicitarCodigo("nadie@example.com"));
        verifyNoInteractions(envio, codigos);
    }

    @Test
    void soloSeAceptaElCorreoCompletoNoElNombreDeUsuario() {
        assertThrows(CorreoNoRegistradoException.class, () -> service.solicitarCodigo("user"));
        verifyNoInteractions(usuarios, envio, codigos);
    }

    @Test
    void solicitarParaUsuarioInactivoNoEnviaCodigo() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(false)));

        assertThrows(UsuarioInactivoException.class, () -> service.solicitarCodigo("user@example.com"));
        verifyNoInteractions(envio, codigos);
    }

    @Test
    void reenviarAntesDeUnMinutoSeRechaza() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarUltimo(1)).thenReturn(Optional.of(codigo(AHORA.minusSeconds(20), 0, false)));

        ReenvioCodigoAnticipadoException ex = assertThrows(ReenvioCodigoAnticipadoException.class,
                () -> service.solicitarCodigo("user@example.com"));

        assertEquals(40, ex.getSegundosRestantes());
        verify(envio, never()).enviar(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void reenviarDespuesDeUnMinutoGeneraUnCodigoNuevo() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarUltimo(1)).thenReturn(Optional.of(codigo(AHORA.minusSeconds(61), 0, false)));
        when(encoder.codificar(anyString())).thenReturn("hash-nuevo");

        service.solicitarCodigo("user@example.com");

        verify(codigos).invalidarPendientes(1);
        verify(envio).enviar(eq("user@example.com"), anyString(), anyString(), eq(5));
    }

    @Test
    void verificarCodigoCorrectoAutenticaYLoMarcaComoUsado() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarPendiente(1)).thenReturn(Optional.of(codigo(AHORA.minusMinutes(1), 0, false)));
        when(encoder.coincide("123456", "hash-codigo")).thenReturn(true);

        Usuario resultado = service.verificarCodigo("user@example.com", " 123 456 ");

        assertEquals(1, resultado.getId());
        ArgumentCaptor<CodigoAcceso> guardado = ArgumentCaptor.forClass(CodigoAcceso.class);
        verify(codigos).guardar(guardado.capture());
        assertTrue(guardado.getValue().usado());
    }

    @Test
    void verificarCodigoIncorrectoSumaUnIntentoYRechaza() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarPendiente(1)).thenReturn(Optional.of(codigo(AHORA.minusMinutes(1), 2, false)));
        when(encoder.coincide("000000", "hash-codigo")).thenReturn(false);

        assertThrows(CodigoAccesoInvalidoException.class,
                () -> service.verificarCodigo("user@example.com", "000000"));

        ArgumentCaptor<CodigoAcceso> guardado = ArgumentCaptor.forClass(CodigoAcceso.class);
        verify(codigos).guardar(guardado.capture());
        assertEquals(3, guardado.getValue().intentos());
        assertFalse(guardado.getValue().usado());
    }

    @Test
    void codigoVencidoSeRechazaAunqueSeaCorrecto() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarPendiente(1)).thenReturn(Optional.of(codigo(AHORA.minusMinutes(6), 0, false)));

        assertThrows(CodigoAccesoInvalidoException.class,
                () -> service.verificarCodigo("user@example.com", "123456"));
        verify(encoder, never()).coincide(anyString(), anyString());
    }

    @Test
    void codigoConDemasiadosIntentosSeBloquea() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarPendiente(1)).thenReturn(Optional.of(
                codigo(AHORA.minusMinutes(1), AutenticarUsuarioService.MAX_INTENTOS, false)));

        assertThrows(CodigoAccesoInvalidoException.class,
                () -> service.verificarCodigo("user@example.com", "123456"));
    }

    @Test
    void sinCodigoPendienteSeRechaza() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarPendiente(1)).thenReturn(Optional.empty());

        assertThrows(CodigoAccesoInvalidoException.class,
                () -> service.verificarCodigo("user@example.com", "123456"));
    }

    @Test
    void usuarioInexistenteRecibeElMismoErrorQueUnCodigoMalo() {
        when(usuarios.buscarPorEmail("nadie@example.com")).thenReturn(Optional.empty());

        assertThrows(CodigoAccesoInvalidoException.class,
                () -> service.verificarCodigo("nadie@example.com", "123456"));
        verifyNoInteractions(codigos);
    }

    @Test
    void usuarioInactivoNoPuedeIngresar() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(false)));

        assertThrows(UsuarioInactivoException.class,
                () -> service.verificarCodigo("user@example.com", "123456"));
        verify(codigos, never()).guardar(any());
    }

    @Test
    void administradorPideIngresoConPasswordSinEnviarCodigo() {
        when(usuarios.buscarPorEmail("admin@example.com")).thenReturn(Optional.of(usuario(true, "ADMINISTRADOR", "admin@example.com")));

        SolicitudCodigo solicitud = service.solicitarCodigo("admin@example.com");

        assertEquals(SolicitudCodigo.PASSWORD, solicitud.modo());
        verifyNoInteractions(envio, codigos);
    }

    @Test
    void soporteIngresaConPassword() {
        when(usuarios.buscarPorEmail("soporte@example.com")).thenReturn(Optional.of(usuario(true, "SOPORTE", "soporte@example.com")));
        when(encoder.coincide("ClaveSegura", "hash-interno")).thenReturn(true);

        Usuario resultado = service.autenticar("soporte@example.com", "ClaveSegura");

        assertEquals(1, resultado.getId());
        verify(usuarios).registrarIngreso(eq(1), any());
    }

    @Test
    void usuarioDeCatalogoNoIngresaConPassword() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));

        assertThrows(CredencialesInvalidasException.class, () -> service.autenticar("user@example.com", "hash-interno"));
        verify(encoder, never()).coincide(anyString(), anyString());
    }

    @Test
    void recuperarEnviaCodigoAlAdministrador() {
        when(usuarios.buscarPorEmail("admin@example.com")).thenReturn(Optional.of(usuario(true, "ADMINISTRADOR", "admin@example.com")));
        when(codigos.buscarUltimo(1)).thenReturn(Optional.empty());
        when(encoder.codificar(anyString())).thenReturn("hash-codigo");

        SolicitudCodigo solicitud = service.solicitarRecuperacion("admin@example.com");

        assertEquals(SolicitudCodigo.CODIGO, solicitud.modo());
        verify(envio).enviarRecuperacion(eq("admin@example.com"), anyString(), anyString(), eq(5));
    }

    @Test
    void recuperarEnviaCodigoACualquierCuentaActiva() {
        when(usuarios.buscarPorEmail("user@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codigos.buscarUltimo(1)).thenReturn(Optional.empty());
        when(encoder.codificar(anyString())).thenReturn("hash-codigo");

        SolicitudCodigo solicitud = service.solicitarRecuperacion("user@example.com");

        assertEquals(SolicitudCodigo.CODIGO, solicitud.modo());
        verify(envio).enviarRecuperacion(eq("user@example.com"), anyString(), anyString(), eq(5));
    }

    @Test
    void completarRecuperacionCambiaElHashYConsumeElCodigo() {
        when(usuarios.buscarPorEmail("admin@example.com")).thenReturn(Optional.of(usuario(true, "ADMINISTRADOR", "admin@example.com")));
        when(codigos.buscarPendiente(1)).thenReturn(Optional.of(codigo(AHORA.minusMinutes(1), 0, false)));
        when(encoder.coincide("123456", "hash-codigo")).thenReturn(true);
        when(encoder.codificar("NuevaClave1")).thenReturn("hash-nuevo");
        when(usuarios.guardar(any())).thenAnswer(i -> i.getArgument(0));

        service.completarRecuperacion("admin@example.com", "123456", "NuevaClave1");

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        assertEquals("hash-nuevo", guardado.getValue().getPasswordHash());
        ArgumentCaptor<CodigoAcceso> codigoUsado = ArgumentCaptor.forClass(CodigoAcceso.class);
        verify(codigos).guardar(codigoUsado.capture());
        assertTrue(codigoUsado.getValue().usado());
    }

    private CodigoAcceso codigo(LocalDateTime creadoEn, int intentos, boolean usado) {
        return new CodigoAcceso(10L, 1, "hash-codigo", creadoEn, creadoEn.plusMinutes(5), intentos, usado);
    }

    private Usuario usuario(boolean activo) {
        return usuario(activo, "USUARIO", "user@example.com");
    }

    private Usuario usuario(boolean activo, String rolNombre, String email) {
        Rol rol = new Rol(1, rolNombre, null, true, null, null);
        return new Usuario(1, rol, "Usuario", email, "hash-interno", activo, null, null);
    }
}
