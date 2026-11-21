package cl.casol.backend;

import cl.casol.backend.correo.application.service.*;
import cl.casol.backend.identidad.application.service.*;
import cl.casol.backend.identidad.infrastructure.security.UsuarioAutenticadoCache;
import cl.casol.backend.identidad.domain.exception.CodigoAccesoInvalidoException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:correo_codigo_qa;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "jwt.secret=secreto-sintetico-exclusivo-test-12345678901234567890", "jwt.expiration=3600000",
    "dimabug.automatizacion.modo=SIMULADO", "dimabug.auth.codigo.segundos-reenvio=60",
    "dimabug.auth.correo.intervalo-ms=3600000", "dimabug.auth.limite-ip-minuto=1000",
    "dimabug.auth.correo.maximo-minuto=1000",
    "dimabug.gmail.remitente=correo-qa@example.invalid",
    "dimabug.gmail.clave-cifrado=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class CorreoCodigoIntegracionTest {
    @Autowired AutenticarUsuarioService identidad;
    @Autowired DespacharCodigosService despachar;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager tm;
    @Autowired UsuarioAutenticadoCache cache;
    @MockitoBean GmailOAuthService gmail; // Ninguna llamada a Google, incluso si cambia la configuración.
    private final AtomicReference<String> codigo = new AtomicReference<>();
    @BeforeEach void datos() throws Exception {
        try (var c = jdbc.getDataSource().getConnection()) {
            assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:correo_codigo_qa"));
        }
        jdbc.execute("ALTER TABLE u_usuario ADD COLUMN IF NOT EXISTS usuario_ultimo_ingreso TIMESTAMP NULL");
        jdbc.update("delete from u_correo_codigo"); jdbc.update("delete from u_codigo_acceso");
        jdbc.update("delete from u_usuario"); jdbc.update("delete from u_rol");
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (var email : List.of("qa@gmail.com", "qa@dimarsa.cl", "inactivo@outlook.com")) {
            cache.invalidar(email);
            jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(?,1,'QA',?,'hash-legado-no-utilizable',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    email.startsWith("inactivo") ? 3 : email.endsWith("gmail.com") ? 1 : 2, email, !email.startsWith("inactivo"));
        }
        when(gmail.enviarAutenticacion(anyString())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Sin transacción durante la red");
            var mime = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(Base64.getUrlDecoder().decode((String)i.getArgument(0))));
            var m = java.util.regex.Pattern.compile("\\d{6}").matcher(mime.getSubject());
            assertTrue(m.find()); codigo.set(m.group()); return "GMAIL-qa";
        });
    }
    private String solicitar(String email) throws Exception {
        return mvc.perform(post("/api/auth/codigo").contentType("application/json").content("{\"email\":\""+email+"\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
    }
    private void procesarPendientes() {
        // El worker real espera al siguiente intervalo; la prueba hace explícita la elegibilidad temporal.
        jdbc.update("update u_correo_codigo set proximo_intento=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) where estado='PENDIENTE'");
        despachar.procesar();
    }
    private String estado() { return jdbc.queryForObject("select estado from u_correo_codigo",String.class); }
    private String id() { return jdbc.queryForObject("select id from u_correo_codigo",String.class); }

    @Test void respuestaUniformeIncluyeAdministradorPersonalInstitucionalInactivoYDesconocido() throws Exception {
        String r = solicitar("nadie@example.invalid");
        assertEquals(r, solicitar("inactivo@outlook.com"));
        assertEquals(r, solicitar("qa@gmail.com"));
        assertEquals(r, solicitar("qa@dimarsa.cl"));
        assertEquals(2,jdbc.queryForObject("select count(*) from u_codigo_acceso",Integer.class));
        verifyNoInteractions(gmail);
    }
    @Test void colaCifradaDespachaUnaVezYJwtSoloDespuesDeVerificar() throws Exception {
        solicitar("qa@gmail.com"); assertEquals("PENDIENTE",estado());
        String cifrado = jdbc.queryForObject("select contenido_cifrado from u_correo_codigo",String.class);
        assertTrue(cifrado.startsWith("v1:")); assertFalse(cifrado.contains("qa@gmail.com"));
        procesarPendientes(); procesarPendientes(); assertEquals("ENVIADO",estado());
        verify(gmail,times(1)).enviarAutenticacion(anyString()); verify(gmail,never()).enviar(anyString());
        assertNull(jdbc.queryForObject("select contenido_cifrado from u_correo_codigo",String.class));
        mvc.perform(post("/api/auth/codigo/verificar").contentType("application/json")
                .content("{\"email\":\"qa@gmail.com\",\"codigo\":\""+codigo.get()+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString()).andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
        assertThrows(CodigoAccesoInvalidoException.class,()->identidad.verificarCodigo("qa@gmail.com",codigo.get()));
    }
    @Test void rollbackNoDejaCodigoNiCorreoDespachable() {
        new TransactionTemplate(tm).executeWithoutResult(tx -> { identidad.solicitarCodigo("qa@gmail.com"); tx.setRollbackOnly(); });
        assertEquals(0,jdbc.queryForObject("select count(*) from u_codigo_acceso",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from u_correo_codigo",Integer.class));
        procesarPendientes(); verifyNoInteractions(gmail);
    }
    @Test void verificacionesConcurrentesSoloConsumenUnaVez() throws Exception {
        solicitar("qa@gmail.com"); procesarPendientes();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> validar = () -> { inicio.await(); try { identidad.verificarCodigo("qa@gmail.com",codigo.get()); return true; }
                catch(CodigoAccesoInvalidoException ex) { return false; } };
            var a=pool.submit(validar); var b=pool.submit(validar); inicio.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
    }
    @Test void errorInciertoNoReintentaNiUsaElEnvioDeEvidencias() throws Exception {
        solicitar("qa@gmail.com"); doThrow(new GmailException(GmailException.Tipo.ENVIO)).when(gmail).enviarAutenticacion(anyString());
        procesarPendientes(); procesarPendientes(); assertEquals("INCIERTO",estado());
        verify(gmail,times(1)).enviarAutenticacion(anyString()); verify(gmail,never()).enviar(anyString());
    }
    @Test void errorTemporalPrevioAlEnvioTieneReintentoAcotado() throws Exception {
        solicitar("qa@gmail.com"); doThrow(new GmailException(GmailException.Tipo.CONEXION)).when(gmail).enviarAutenticacion(anyString());
        for(int n=0;n<3;n++) {
            jdbc.update("update u_correo_codigo set proximo_intento=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)");
            despachar.procesar(id());
            assertEquals(n<2?"ERROR_TEMPORAL":"ERROR_PERMANENTE",estado());
        }
        procesarPendientes(); verify(gmail,times(3)).enviarAutenticacion(anyString());
    }
    @Test void falloConfiguracionYRevocacionQuedanDistinguidos() throws Exception {
        solicitar("qa@gmail.com"); doThrow(new GmailException(GmailException.Tipo.CONFIGURACION)).when(gmail).enviarAutenticacion(anyString());
        procesarPendientes(); assertEquals("NO_CONFIGURADO",estado());
    }
    @Test void autorizacionRevocadaQuedaRegistradaSinAlternativaSmtp() throws Exception {
        solicitar("qa@gmail.com");
        doThrow(new GmailException(GmailException.Tipo.AUTORIZACION)).when(gmail).enviarAutenticacion(anyString());
        procesarPendientes(); assertEquals("REAUTORIZAR",estado());
        verify(gmail,never()).enviar(anyString());
    }
    @Test void solicitudesConcurrentesSoloEmitenUnCodigo() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            Callable<SolicitudCodigo> emitir=()->{inicio.await();return identidad.solicitarCodigo("qa@gmail.com");};
            var a=pool.submit(emitir);var b=pool.submit(emitir);inicio.countDown();
            assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from u_codigo_acceso",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from u_correo_codigo",Integer.class));
    }
    @Test void intentosFallidosSeConfirmanAunqueLaValidacionRechace() throws Exception {
        solicitar("qa@gmail.com");
        for(int n=0;n<5;n++) assertThrows(CodigoAccesoInvalidoException.class,()->identidad.verificarCodigo("qa@gmail.com","no-valido"));
        assertEquals(3,jdbc.queryForObject("select intentos from u_codigo_acceso",Integer.class)); // Al tercer error se bloquea y no se siguen contando.
        procesarPendientes();assertEquals("EXPIRADO",estado()); verifyNoInteractions(gmail);
    }
    @Test void envioAbandonadoSeMarcaInciertoSinReenviar() throws Exception {
        solicitar("qa@gmail.com");
        jdbc.update("update u_correo_codigo set estado='ENVIANDO',proximo_intento=DATEADD('SECOND',-61,CURRENT_TIMESTAMP)");
        procesarPendientes(); assertEquals("INCIERTO",estado()); verifyNoInteractions(gmail);
    }
    @Test void codigoInvalidadoNoSaleDeLaCola() throws Exception {
        solicitar("qa@gmail.com"); jdbc.update("update u_codigo_acceso set usado=true");
        procesarPendientes(); assertEquals("EXPIRADO",estado()); verifyNoInteractions(gmail);
    }
}
