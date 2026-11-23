package cl.casol.backend.identidad.application.service;

import cl.casol.backend.identidad.application.port.out.*;
import cl.casol.backend.identidad.domain.*;
import cl.casol.backend.identidad.domain.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AutenticarUsuarioServiceTest {
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime ahora = LocalDateTime.now(reloj);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CodigoAccesoRepository codigos = mock(CodigoAccesoRepository.class);
    private final EnvioCodigoAccesoPort envio = mock(EnvioCodigoAccesoPort.class);
    private final PasswordEncoderPort encoder = mock(PasswordEncoderPort.class);
    private final AutenticarUsuarioService service = new AutenticarUsuarioService(usuarios, codigos, envio, encoder, reloj, 5, 60);
    private Usuario usuario(String email, String rol, boolean activo) {
        return new Usuario(1, new Rol(1, rol, null, true, null, null), "QA", email, "hash-existente", activo, null, null);
    }
    @BeforeEach void preparar() {
        when(encoder.codificar(anyString())).thenReturn("hash-seguro");
        when(codigos.guardar(any())).thenAnswer(i -> {
            CodigoAcceso c = i.getArgument(0);
            return new CodigoAcceso(c.id() == null ? 1L : c.id(), c.usuarioId(), c.codigoHash(), c.creadoEn(), c.expiraEn(), c.intentos(), c.usado());
        });
    }
    @ParameterizedTest @ValueSource(strings = {"qa@dimarsa.cl", "qa@gmail.com", "qa@outlook.com"})
    void permiteCualquierDominioRegistrado(String email) {
        when(usuarios.buscarPorEmail(email)).thenReturn(Optional.of(usuario(email,"ADMINISTRADOR",true)));
        var respuesta = service.solicitarCodigo(email);
        assertEquals("CODIGO", respuesta.modo()); assertEquals(300, respuesta.segundosVigencia());
        var codigo = ArgumentCaptor.forClass(String.class);
        verify(envio).encolar(any(), eq(email), eq("QA"), codigo.capture(), eq(5));
        assertTrue(codigo.getValue().matches("\\d{6}"));
        verify(encoder).codificar(codigo.getValue());
        var registro = ArgumentCaptor.forClass(CodigoAcceso.class);
        verify(codigos).guardar(registro.capture());
        assertEquals("hash-seguro",registro.getValue().codigoHash());
        assertEquals(ahora.plusMinutes(5),registro.getValue().expiraEn());
        var orden = inOrder(codigos,envio);
        orden.verify(codigos).bloquearUsuario(1);
        orden.verify(codigos).invalidarPendientes(1);
        orden.verify(codigos).guardar(any());
        orden.verify(envio).encolar(any(),anyString(),anyString(),anyString(),anyInt());
    }
    @Test void correoQueNoExisteNoRecibeCodigo() {
        assertThrows(CorreoNoRegistradoException.class,()->service.solicitarCodigo("nadie@example.com"));
        verifyNoInteractions(envio); verify(codigos,never()).guardar(any());
    }
    @Test void correoGmailSinCuentaNoPuedeRegistrarseSolo() {
        assertThrows(CorreoNoRegistradoException.class,()->service.solicitarCodigo("persona@gmail.com"));
        verifyNoInteractions(envio); verify(codigos,never()).guardar(any());
    }
    @Test void correoDeLaEmpresaSinCuentaNoEsRechazado() {
        assertDoesNotThrow(()->service.solicitarCodigo("persona@dimarsa.cl"));
    }
    @Test void inactivoYExistenteTienenRespuestaIgual() {
        when(usuarios.buscarPorEmail("inactivo@example.com")).thenReturn(Optional.of(usuario("inactivo@example.com","USUARIO",false)));
        when(usuarios.buscarPorEmail("activo@example.com")).thenReturn(Optional.of(usuario("activo@example.com","USUARIO",true)));
        assertEquals(service.solicitarCodigo("inactivo@example.com"),service.solicitarCodigo("activo@example.com"));
        verify(envio,times(1)).encolar(any(),anyString(),anyString(),anyString(),anyInt());
    }
    @Test void reenvioAnticipadoNoRevelaCuentaNiInvalidaCodigo() {
        when(usuarios.buscarPorEmail("qa@example.com")).thenReturn(Optional.of(usuario("qa@example.com","USUARIO",true)));
        when(codigos.buscarUltimo(1)).thenReturn(Optional.of(codigo(0,false,ahora.plusMinutes(5),ahora.minusSeconds(20))));
        assertEquals(new SolicitudCodigo(300,60),service.solicitarCodigo("qa@example.com"));
        verify(codigos,never()).invalidarPendientes(any()); verifyNoInteractions(envio);
    }
    @Test void limitaEmisionesAcumuladas() {
        when(usuarios.buscarPorEmail("qa@example.com")).thenReturn(Optional.of(usuario("qa@example.com","USUARIO",true)));
        when(codigos.contarDesde(1,ahora.minusMinutes(15))).thenReturn(5L);
        service.solicitarCodigo("qa@example.com"); verifyNoInteractions(envio);
    }
    private CodigoAcceso codigo(int intentos, boolean usado, LocalDateTime expira, LocalDateTime creado) {
        return new CodigoAcceso(1L,1,"hash-seguro",creado,expira,intentos,usado);
    }
    private void pendiente(CodigoAcceso c) {
        when(usuarios.buscarPorEmail("qa@example.com")).thenReturn(Optional.of(usuario("qa@example.com","ADMINISTRADOR",true)));
        when(codigos.buscarPendiente(1)).thenReturn(Optional.of(c));
    }
    @Test void correctoSeConsumeAntesDeRegistrarIngreso() {
        pendiente(codigo(0,false,ahora.plusMinutes(1),ahora.minusMinutes(1)));
        when(encoder.coincide("123456","hash-seguro")).thenReturn(true);
        assertEquals("ADMINISTRADOR",service.verificarCodigo("qa@example.com","123456").getRol().getNombre());
        var orden=inOrder(codigos,usuarios); orden.verify(codigos).bloquearUsuario(1);
        orden.verify(codigos).buscarPendiente(1); orden.verify(codigos).guardar(argThat(CodigoAcceso::usado));
        orden.verify(usuarios).registrarIngreso(eq(1),eq(ahora));
    }
    @Test void incorrectoIncrementaIntentos() {
        pendiente(codigo(2,false,ahora.plusMinutes(1),ahora.minusMinutes(1)));
        assertThrows(CodigoAccesoInvalidoException.class,()->service.verificarCodigo("qa@example.com","000000"));
        verify(codigos).guardar(argThat(c->c.intentos()==3));
        verify(usuarios,never()).registrarIngreso(anyInt(),any());
    }
    @Test void rechazaVencidoReutilizadoYExcesoDeIntentos() {
        for(var c:List.of(codigo(0,false,ahora,ahora.minusMinutes(5)),codigo(0,true,ahora.plusMinutes(1),ahora),codigo(5,false,ahora.plusMinutes(1),ahora))) {
            pendiente(c); assertThrows(CodigoAccesoInvalidoException.class,()->service.verificarCodigo("qa@example.com","123456"));
        }
        verifyNoInteractions(encoder); verify(usuarios,never()).registrarIngreso(anyInt(),any());
    }
    @Test void alTercerCodigoIncorrectoSeBloqueaInclusoConElCodigoCorrecto() {
        pendiente(codigo(0,false,ahora.plusMinutes(1),ahora.minusMinutes(1)));
        assertThrows(CodigoAccesoInvalidoException.class,()->service.verificarCodigo("qa@example.com","000000"));
        assertThrows(CodigoAccesoInvalidoException.class,()->service.verificarCodigo("qa@example.com","000000"));
        var bloqueo = assertThrows(IngresoBloqueadoException.class,()->service.verificarCodigo("qa@example.com","000000"));
        assertEquals(30, bloqueo.getSegundosRestantes());
        // Mientras dure el bloqueo, ni siquiera el código correcto deja entrar.
        when(encoder.coincide("123456","hash-seguro")).thenReturn(true);
        assertThrows(IngresoBloqueadoException.class,()->service.verificarCodigo("qa@example.com","123456"));
        verify(usuarios,never()).registrarIngreso(anyInt(),any());
    }
    @Test void cuentaInactivaNoVerifica() {
        when(usuarios.buscarPorEmail("qa@example.com")).thenReturn(Optional.of(usuario("qa@example.com","USUARIO",false)));
        assertThrows(CodigoAccesoInvalidoException.class,()->service.verificarCodigo("qa@example.com","123456"));
    }
    @Test void passwordNoAutenticaNiRecupera() {
        assertThrows(CredencialesInvalidasException.class,()->service.autenticar("qa@example.com","password"));
        assertThrows(CredencialesInvalidasException.class,()->service.completarRecuperacion("qa@example.com","123456","password"));
        verifyNoInteractions(usuarios,codigos,envio);
    }
}
