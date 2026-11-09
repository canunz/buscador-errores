package cl.casol.backend;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=classpath:application-smtp.properties","spring.datasource.url=jdbc:h2:mem:correo_sin_sucursal;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","jwt.secret=clave-sintetica-correo-solo-test-1234567890123456789","jwt.expiration=3600000","dimabug.automatizacion.modo=SMTP","dimabug.correo.remitente=qa-remitente@dimarsa.cl","dimabug.automatizacion.intervalo-ms=3600000","dimabug.correo.maximo-intentos-hora=2"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorreoSinSucursalHttpTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    cl.casol.backend.identidad.application.port.out.EnvioCodigoAccesoPort envioCodigo;
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender smtp;
    String token;
    String json(Object v){return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(v);}
    String texto(ResultActions r)throws Exception{return r.andReturn().getResponse().getContentAsString();}
    int id(String r){return ((Number)JsonPath.read(r,"$.id")).intValue();}
    ResultActions postJson(String ruta,Object body)throws Exception{return mvc.perform(post(ruta).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(body)));}
    @BeforeAll void datos()throws Exception{
        try(var c=jdbc.getDataSource().getConnection()){assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:correo_sin_sucursal"));}
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'Correo QA','qa-correo@example.invalid',?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",encoder.encode("SoloPruebas123!"));
        token=LoginCodigoTestSupport.autenticar(mvc,envioCodigo,"qa-correo@example.invalid",jdbc);
    }
    @BeforeEach void smtpAislado(){
        reset(smtp);jdbc.update("delete from se_evento_ejecucion where tipo in ('SMTP_INICIADO','RECORDATORIO_SMTP_INICIADO')");
        when(smtp.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"SMTP no debe retener una transacción SQL");return null;}).when(smtp).send(any(MimeMessage.class));
    }
    String preparar(String modo)throws Exception{
        int p=id(texto(postJson("/api/procedimientos",Map.of("nombre","Correo QA "+UUID.randomUUID())).andExpect(status().isCreated())));
        postJson("/api/procedimientos/"+p+"/pasos",Map.of("orden",1,"instruccion","Enviar comunicación","esCritico",false,"modoEjecucion",modo)).andExpect(status().isCreated());
        mvc.perform(patch("/api/procedimientos/"+p+"/estado").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}")).andExpect(status().isOk());
        int e=id(texto(postJson("/api/procedimientos/"+p+"/ejecuciones",null).andExpect(status().isCreated())));
        String pasos=texto(mvc.perform(get("/api/ejecuciones/"+e+"/pasos").header("Authorization","Bearer "+token)).andExpect(status().isOk()));
        return "/api/ejecuciones/"+e+"/pasos/"+((Number)JsonPath.read(pasos,"$[0].ejecucionPasoId")).intValue()+"/correo";
    }
    Map<String,Object> datosCorreo(){return Map.of("destinatarios",List.of(Map.of("correo","qa-para@dimarsa.cl","tipo","PRINCIPAL"),Map.of("correo","qa-segundo@dimarsa.cl","tipo","PRINCIPAL"),Map.of("correo","qa-copia@dimarsa.cl","tipo","COPIA")),"asunto","Prueba sin sucursal","mensaje","Mensaje personalizado QA");}
    @Test void enviaVariosParaYCcSinSucursalYPersisteUnaSolaComunicacion()throws Exception{
        String ruta=preparar("ENVIAR_CORREO");String enviada=texto(postJson(ruta,datosCorreo()).andExpect(status().isOk()).andExpect(jsonPath("$.contexto.sucursalId").isEmpty()).andExpect(jsonPath("$.estado").value("ENVIADA")).andExpect(jsonPath("$.fechaEnvio").isNotEmpty()));
        String repetida=texto(postJson(ruta,datosCorreo()).andExpect(status().isOk()));assertEquals((String)JsonPath.read(enviada,"$.id"),(String)JsonPath.read(repetida,"$.id"));
        var captor=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(smtp,times(1)).send(captor.capture());
        var mime=captor.getValue();assertEquals(2,mime.getRecipients(Message.RecipientType.TO).length);assertEquals(1,mime.getRecipients(Message.RecipientType.CC).length);
        assertEquals("Prueba sin sucursal",mime.getSubject());assertEquals("Mensaje personalizado QA",mime.getContent());
        assertNull(jdbc.queryForObject("select sucursal_id from se_solicitud_evidencia where id=?",Integer.class,(String)JsonPath.read(enviada,"$.id")));
    }
    @ParameterizedTest @ValueSource(strings={"no-es-correo","qa@otro.cl","a..b@dimarsa.cl","qa@dimarsa.cl\r\nBcc:evil@dimarsa.cl"})
    void rechazaCorreosInvalidosSinEnviar(String correo)throws Exception{
        var body=new HashMap<>(datosCorreo());body.put("destinatarios",List.of(Map.of("correo",correo,"tipo","PRINCIPAL")));
        postJson(preparar("ENVIAR_CORREO"),body).andExpect(status().isBadRequest());verify(smtp,never()).send(any(MimeMessage.class));
    }
    @Test void errorSmtpNoCompletaYReintentoNoDuplica()throws Exception{
        doThrow(new org.springframework.mail.MailAuthenticationException("Detalle sensible sintético que no debe salir")).when(smtp).send(any(MimeMessage.class));
        String ruta=preparar("ENVIAR_CORREO");String resultado=texto(postJson(ruta,datosCorreo()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ERROR")).andExpect(jsonPath("$.fechaEnvio").isEmpty()));
        assertFalse(resultado.contains("sensible"));postJson(ruta,datosCorreo()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ERROR"));verify(smtp,times(1)).send(any(MimeMessage.class));
        String ejecucion=ruta.substring(0,ruta.indexOf("/pasos"));mvc.perform(get(ejecucion+"/pasos").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].cumplido").value(false));
    }
    @Test void evidenciasEnviaSinSucursalYEsperaLasFotografias()throws Exception{
        postJson(preparar("SOLICITAR_EVIDENCIA"),datosCorreo()).andExpect(status().isOk()).andExpect(jsonPath("$.contexto.sucursalId").isEmpty()).andExpect(jsonPath("$.estado").value("ESPERANDO_EVIDENCIA")).andExpect(jsonPath("$.politica.configuracion.evidenciasRequeridas.length()").value(3));verify(smtp,times(1)).send(any(MimeMessage.class));
    }
    @Test void limitaIntentosPorUsuario()throws Exception{
        for(int i=0;i<2;i++)postJson(preparar("ENVIAR_CORREO"),datosCorreo()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADA"));
        postJson(preparar("ENVIAR_CORREO"),datosCorreo()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ERROR"));verify(smtp,times(2)).send(any(MimeMessage.class));
    }
    @Test void sinAutenticacionNoEnvia()throws Exception{mvc.perform(post("/api/ejecuciones/1/pasos/1/correo").contentType(MediaType.APPLICATION_JSON).content(json(datosCorreo()))).andExpect(status().isUnauthorized());verifyNoInteractions(smtp);}
    @Test void sinParaYSobreLimiteDeDestinatariosNoEnvia()throws Exception{
        var body=new HashMap<>(datosCorreo());body.put("destinatarios",List.of(Map.of("correo","qa-cc@dimarsa.cl","tipo","COPIA")));
        postJson(preparar("ENVIAR_CORREO"),body).andExpect(status().isBadRequest());
        var muchos=new ArrayList<Map<String,String>>();for(int i=0;i<21;i++)muchos.add(Map.of("correo","qa"+i+"@dimarsa.cl","tipo","PRINCIPAL"));
        body.put("destinatarios",muchos);postJson(preparar("ENVIAR_CORREO"),body).andExpect(status().isBadRequest());verify(smtp,never()).send(any(MimeMessage.class));
    }
    @Test void asuntoNoPermiteInyectarCabeceras()throws Exception{
        var body=new HashMap<>(datosCorreo());body.put("asunto","Prueba\r\nBcc:qa@dimarsa.cl");
        postJson(preparar("ENVIAR_CORREO"),body).andExpect(status().isBadRequest());verify(smtp,never()).send(any(MimeMessage.class));
    }
    @Test void usuarioFinalNoPuedeEnviar()throws Exception{
        mvc.perform(post("/api/ejecuciones/1/pasos/1/correo").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("qa").roles("USUARIO")).contentType(MediaType.APPLICATION_JSON).content(json(datosCorreo()))).andExpect(status().isForbidden());verifyNoInteractions(smtp);
    }
    @Test void perfilSmtpExigeStarttlsYTimeoutsFinitos(@Autowired org.springframework.core.env.Environment env){
        assertEquals("true",env.getProperty("spring.mail.properties[mail.smtp.starttls.required]"));
        assertEquals("5000",env.getProperty("spring.mail.properties[mail.smtp.connectiontimeout]"));
        assertEquals("false",env.getProperty("spring.mail.properties[mail.debug]"));
    }
}
