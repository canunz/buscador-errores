package cl.casol.backend;

import cl.casol.backend.correo.application.port.out.*;
import cl.casol.backend.correo.application.service.*;
import cl.casol.backend.correo.infrastructure.gmail.GmailConfig;
import cl.casol.backend.ejecucion.application.service.AutomatizarEvidenciaService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:gmail_oauth;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","jwt.secret=clave-sintetica-gmail-oauth-solo-test-1234567890123456789","jwt.expiration=3600000","dimabug.automatizacion.modo=GMAIL_API","dimabug.automatizacion.intervalo-ms=3600000","dimabug.gmail.oauth-habilitado=true","dimabug.gmail.envio-real-habilitado=false","dimabug.gmail.client-id=id-sintetico","dimabug.gmail.client-secret=secret-sintetico","dimabug.gmail.remitente=qa-gmail@dimarsa.cl","dimabug.gmail.clave-cifrado=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GmailOAuthHttpTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;@Autowired Clock reloj;
    @Autowired GmailTokenStore store;@Autowired GmailOAuthService servicio;@Autowired AutomatizarEvidenciaService automatizar;
    @MockitoBean GoogleGmailPort google;@MockitoSpyBean GmailConfig config;
    String token;
    String json(Object o){return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(o);}
    String texto(ResultActions r)throws Exception{return r.andReturn().getResponse().getContentAsString();}
    ResultActions postJson(String ruta,Object body)throws Exception{return mvc.perform(post(ruta).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json(body)));}
    int id(String s){return ((Number)JsonPath.read(s,"$.id")).intValue();}
    @BeforeAll void datos()throws Exception{
        try(var c=jdbc.getDataSource().getConnection()){assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:gmail_oauth"));}
        jdbc.update("insert into u_rol(rol_id,rol_nombre,rol_estado,rol_fecha_creacion,rol_fecha_actualizacion) values(1,'ADMINISTRADOR',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into u_usuario(usuario_id,rol_id,usuario_nombre,usuario_email,usuario_password_hash,usuario_estado,usuario_fecha_creacion,usuario_fecha_actualizacion) values(1,1,'Gmail QA','qa-gmail@example.invalid',?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",encoder.encode("SoloPruebas123!"));
        token=JsonPath.read(texto(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email","qa-gmail@example.invalid","password","SoloPruebas123!")))).andExpect(status().isOk())),"$.token");
    }
    @BeforeEach void aislado(){
        reset(google);for(String tabla:List.of("gm_prueba_evento","gm_prueba_correo","gm_estado_oauth","gm_auditoria_oauth","gm_cuenta_oauth"))jdbc.update("delete from "+tabla);
        when(google.autorizacion(anyString(),anyString())).thenAnswer(i->"https://accounts.google.com/o/oauth2/v2/auth?state="+i.getArgument(0));
        when(google.intercambiar(anyString(),anyString(),anyString())).thenAnswer(i->new GoogleGmailPort.Tokens("access-token-sintetico","refresh-token-sintetico",reloj.instant().plusSeconds(3600),Set.of(GmailOAuthService.SCOPE)));
        when(google.renovar(anyString())).thenAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return new GoogleGmailPort.Tokens("access-renovado",null,reloj.instant().plusSeconds(3600),Set.of(GmailOAuthService.SCOPE));});
        when(google.enviar(anyString(),anyString())).thenAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return "GMAIL-qa123";});
    }
    record Inicio(String state,Cookie cookie){}
    Inicio iniciar()throws Exception{
        var r=postJson("/api/admin/gmail/autorizacion",Map.of()).andExpect(status().isOk()).andReturn().getResponse();
        String url=JsonPath.read(r.getContentAsString(),"$.authorizationUrl");String s=url.substring(url.indexOf("state=")+6);
        String cookie=r.getHeader("Set-Cookie");assertTrue(cookie.contains("HttpOnly"));assertTrue(cookie.contains("SameSite=Lax"));assertFalse(cookie.contains("JSESSIONID"));
        return new Inicio(s,new Cookie("dimabug_gmail_oauth",cookie.substring(cookie.indexOf('=')+1,cookie.indexOf(';'))));
    }
    ResultActions callback(Inicio i)throws Exception{return mvc.perform(get("/login/oauth2/code/google").param("state",i.state()).param("code","codigo-sintetico").cookie(i.cookie()));}
    void conectar()throws Exception{callback(iniciar()).andExpect(status().isSeeOther()).andExpect(header().string("Location","http://localhost:4200/admin/gmail"));}
    @Test void conservaJwtYNoAutorizaRolesNoAdministrativos()throws Exception{
        mvc.perform(post("/api/admin/gmail/autorizacion")).andExpect(status().isUnauthorized());
        for(String rol:List.of("TECNICO","SOPORTE","USUARIO"))mvc.perform(post("/api/admin/gmail/autorizacion").with(user("qa").roles(rol))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/gmail").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.conectado").value(false));verifyNoInteractions(google);
    }
    @Test void conectaCifradoSinDevolverTokensNiActivarEnvios()throws Exception{
        conectar();String s=texto(mvc.perform(get("/api/admin/gmail").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.conectado").value(true)).andExpect(jsonPath("$.envioRealHabilitado").value(false)));
        assertFalse(s.contains("access-token"));assertFalse(s.contains("refresh-token"));assertFalse(servicio.disponible());
        String acceso=jdbc.queryForObject("select access_token_cifrado from gm_cuenta_oauth",String.class),refresh=jdbc.queryForObject("select refresh_token_cifrado from gm_cuenta_oauth",String.class);assertTrue(acceso.startsWith("v1:"));assertFalse(acceso.contains("access-token"));assertFalse(refresh.contains("refresh-token"));verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void rechazaEstadoAjenoCookieAusenteYReplay()throws Exception{
        var i=iniciar();mvc.perform(get("/login/oauth2/code/google").param("state",i.state()).param("code","qa")).andExpect(status().isBadRequest());
        callback(new Inicio(i.state(),new Cookie("dimabug_gmail_oauth","B".repeat(43)))).andExpect(status().isBadRequest());
        callback(i).andExpect(status().isSeeOther());callback(i).andExpect(status().isBadRequest());verify(google,times(1)).intercambiar(anyString(),anyString(),anyString());
    }
    @Test void rechazaEstadoVencido()throws Exception{var i=iniciar();jdbc.update("update gm_estado_oauth set expira_en=?",java.sql.Timestamp.from(reloj.instant().minusSeconds(1)));callback(i).andExpect(status().isBadRequest());verify(google,never()).intercambiar(anyString(),anyString(),anyString());}
    @Test void administradorDesactivadoNoPuedeCompletar()throws Exception{var i=iniciar();jdbc.update("update u_usuario set usuario_estado=false where usuario_id=1");try{callback(i).andExpect(status().isForbidden());verify(google,never()).intercambiar(anyString(),anyString(),anyString());}finally{jdbc.update("update u_usuario set usuario_estado=true where usuario_id=1");}}
    @Test void rechazaPermisosAdicionalesYFaltaDeRefreshToken()throws Exception{
        when(google.intercambiar(anyString(),anyString(),anyString())).thenReturn(new GoogleGmailPort.Tokens("qa","refresh",reloj.instant().plusSeconds(3600),Set.of(GmailOAuthService.SCOPE,"https://mail.google.com/")));
        callback(iniciar()).andExpect(status().isBadRequest());assertNotEquals("ACTIVA",store.cuenta().orElseThrow().estado());
        when(google.intercambiar(anyString(),anyString(),anyString())).thenReturn(new GoogleGmailPort.Tokens("qa",null,reloj.instant().plusSeconds(3600),Set.of(GmailOAuthService.SCOPE)));callback(iniciar()).andExpect(status().isBadRequest());
    }
    @Test void revocaEnGoogleYNoResucitaUnaRenovacionAntigua()throws Exception{
        conectar();var anterior=store.cuenta().orElseThrow();postJson("/api/admin/gmail/revocar",Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.conectado").value(false));verify(google).revocar("refresh-token-sintetico");
        var c=store.cuenta().orElseThrow();assertNull(c.accesoCifrado());assertNull(c.refrescoCifrado());assertNull(c.revocacionCifrada());assertThrows(GmailException.class,()->store.renovar(anterior.version(),"acceso","refresco",reloj.instant().plusSeconds(3600)));
    }
    @Test void falloDeRevocacionRemotaDeshabilitaLocalYPermiteReintentar()throws Exception{
        conectar();doThrow(new GmailException(GmailException.Tipo.CONEXION)).when(google).revocar(anyString());postJson("/api/admin/gmail/revocar",Map.of()).andExpect(status().isBadRequest());assertEquals("REVOCADA",store.cuenta().orElseThrow().estado());assertNotNull(store.cuenta().orElseThrow().revocacionCifrada());assertFalse(servicio.disponible());
        doNothing().when(google).revocar(anyString());postJson("/api/admin/gmail/revocar",Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.revocacionRemotaPendiente").value(false));
    }
    String prepararPaso(String modo)throws Exception{
        int p=id(texto(postJson("/api/procedimientos",Map.of("nombre","Gmail QA "+UUID.randomUUID())).andExpect(status().isCreated())));
        postJson("/api/procedimientos/"+p+"/pasos",Map.of("orden",1,"instruccion","Correo de prueba aislado","esCritico",false,"modoEjecucion",modo)).andExpect(status().isCreated());
        mvc.perform(patch("/api/procedimientos/"+p+"/estado").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\"}")).andExpect(status().isOk());
        int e=id(texto(postJson("/api/procedimientos/"+p+"/ejecuciones",null).andExpect(status().isCreated())));String pasos=texto(mvc.perform(get("/api/ejecuciones/"+e+"/pasos").header("Authorization","Bearer "+token)).andExpect(status().isOk()));
        return "/api/ejecuciones/"+e+"/pasos/"+((Number)JsonPath.read(pasos,"$[0].ejecucionPasoId")).intValue()+"/correo";
    }
    Map<String,Object> mensaje(){return Map.of("destinatarios",List.of(Map.of("correo","qa-para@dimarsa.cl","tipo","PRINCIPAL"),Map.of("correo","qa-cc@dimarsa.cl","tipo","COPIA")),"asunto","Solicitud Gmail QA","mensaje","Mensaje solo para mock");}
    @Test void noEnviaAunqueLaCuentaEsteAutorizadaSinHabilitacion()throws Exception{conectar();postJson(prepararPaso("ENVIAR_CORREO"),mensaje()).andExpect(status().isBadRequest());verify(google,never()).enviar(anyString(),anyString());}
    @Test void envioUsaGmailMimeRegistraUnaVezYNoRetieneTransaccion()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();String ruta=prepararPaso("ENVIAR_CORREO");
        postJson(ruta,mensaje()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADA")).andExpect(jsonPath("$.mensajeEnviadoId").value("GMAIL-qa123")).andExpect(jsonPath("$.fechaEnvio").isNotEmpty());postJson(ruta,mensaje()).andExpect(status().isOk());
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);verify(google,times(1)).enviar(eq("access-token-sintetico"),captor.capture());String mime=new String(Base64.getUrlDecoder().decode(captor.getValue()),java.nio.charset.StandardCharsets.UTF_8);assertTrue(mime.contains("To: qa-para@dimarsa.cl"));assertTrue(mime.contains("Cc: qa-cc@dimarsa.cl"));assertEquals(1,jdbc.queryForObject("select count(*) from se_evento_ejecucion where tipo='GMAIL_ACEPTADO' and ejecucion_id=?",Integer.class,Integer.parseInt(ruta.split("/")[3])));
    }
    @Test void renuevaAntesDeEnviarYConservaRefreshCuandoGoogleNoLoRota()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();String previo=store.cuenta().orElseThrow().refrescoCifrado();jdbc.update("update gm_cuenta_oauth set expira_en=?",java.sql.Timestamp.from(reloj.instant().minusSeconds(1)));
        postJson(prepararPaso("ENVIAR_CORREO"),mensaje()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADA"));verify(google).renovar("refresh-token-sintetico");verify(google).enviar(eq("access-renovado"),anyString());assertEquals(previo,store.cuenta().orElseThrow().refrescoCifrado());
    }
    @Test void invalidGrantExigeReautorizacionSinEnviar()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();jdbc.update("update gm_cuenta_oauth set expira_en=?",java.sql.Timestamp.from(reloj.instant().minusSeconds(1)));when(google.renovar(anyString())).thenThrow(new GmailException(GmailException.Tipo.AUTORIZACION));
        postJson(prepararPaso("ENVIAR_CORREO"),mensaje()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ERROR"));assertEquals("REAUTORIZAR",store.cuenta().orElseThrow().estado());verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void recordatorioGmailSaleUnaVezYNoCompletaEvidencias()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();String ruta=prepararPaso("SOLICITAR_EVIDENCIA");
        String enviada=texto(postJson(ruta,mensaje()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ESPERANDO_EVIDENCIA")));String sid=JsonPath.read(enviada,"$.id");
        jdbc.update("update se_solicitud_evidencia set fecha_envio=?,proximo_seguimiento=? where id=?",java.sql.Timestamp.from(reloj.instant().minusSeconds(25*3600)),java.sql.Timestamp.from(reloj.instant().minusSeconds(3600)),sid);
        automatizar.procesarSeguimiento(sid);automatizar.procesarSeguimiento(sid);
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);verify(google,times(2)).enviar(anyString(),captor.capture());String mime=new String(Base64.getUrlDecoder().decode(captor.getAllValues().get(1)),java.nio.charset.StandardCharsets.UTF_8);assertTrue(mime.contains("Recordatorio:"));
        assertEquals(1,jdbc.queryForObject("select count(*) from se_evento_ejecucion where tipo='RECORDATORIO_GMAIL_ACEPTADO' and solicitud_id=?",Integer.class,sid));
        assertEquals("ESPERANDO_EVIDENCIA",jdbc.queryForObject("select estado from se_solicitud_evidencia where id=?",String.class,sid));
    }
    @Test void falloGmailNoDuplicaUnEnvioIncierto()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();when(google.enviar(anyString(),anyString())).thenThrow(new GmailException(GmailException.Tipo.ENVIO));String ruta=prepararPaso("ENVIAR_CORREO");
        postJson(ruta,mensaje()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ERROR"));postJson(ruta,mensaje()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ERROR"));verify(google,times(1)).enviar(anyString(),anyString());
    }
    @Test void revocarInvalidaUnCallbackAnteriorSinIntercambiarCodigo()throws Exception{
        var i=iniciar();postJson("/api/admin/gmail/revocar",Map.of()).andExpect(status().isOk());callback(i).andExpect(status().isBadRequest());verify(google,never()).intercambiar(anyString(),anyString(),anyString());
    }
    @Test void noReemplazaUnaCuentaActivaSinRevocarla()throws Exception{conectar();postJson("/api/admin/gmail/autorizacion",Map.of()).andExpect(status().isBadRequest());}
    @Test void falloTemporalDeRenovacionConservaLasCredencialesSinEnviar()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();String refresh=store.cuenta().orElseThrow().refrescoCifrado();jdbc.update("update gm_cuenta_oauth set expira_en=?",java.sql.Timestamp.from(reloj.instant().minusSeconds(1)));when(google.renovar(anyString())).thenThrow(new GmailException(GmailException.Tipo.CONEXION));
        postJson(prepararPaso("ENVIAR_CORREO"),mensaje()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ERROR"));assertEquals("ACTIVA",store.cuenta().orElseThrow().estado());assertEquals(refresh,store.cuenta().orElseThrow().refrescoCifrado());verify(google,never()).enviar(anyString(),anyString());
    }
    String simulacionHistoricaVencida()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();when(google.enviar(anyString(),anyString())).thenReturn("SIMULADO-historico-qa");
        String s=texto(postJson(prepararPaso("SOLICITAR_EVIDENCIA"),mensaje()).andExpect(status().isOk()));String sid=JsonPath.read(s,"$.id");
        jdbc.update("update se_solicitud_evidencia set fecha_envio=?,proximo_seguimiento=? where id=?",java.sql.Timestamp.from(reloj.instant().minusSeconds(25*3600)),java.sql.Timestamp.from(reloj.instant().minusSeconds(3600)),sid);
        clearInvocations(google);when(google.enviar(anyString(),anyString())).thenReturn("GMAIL-futuro-qa");return sid;
    }
    @Test void colaRealExcluyeRecordatoriosDeSimulacionesHistoricas()throws Exception{
        String sid=simulacionHistoricaVencida();assertFalse(automatizar.vencidas().contains(sid));
        assertEquals(1,jdbc.queryForObject("select count(*) from se_solicitud_evidencia where id=? and proximo_seguimiento<=?",Integer.class,sid,java.sql.Timestamp.from(reloj.instant())));verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void seguimientoRealNoReenviaNiModificaUnaSimulacionHistorica()throws Exception{
        String sid=simulacionHistoricaVencida();var antes=jdbc.queryForMap("select * from se_solicitud_evidencia where id=?",sid);int eventos=jdbc.queryForObject("select count(*) from se_evento_ejecucion where solicitud_id=?",Integer.class,sid);
        automatizar.procesarSeguimiento(sid);
        assertEquals(antes,jdbc.queryForMap("select * from se_solicitud_evidencia where id=?",sid));assertEquals(eventos,jdbc.queryForObject("select count(*) from se_evento_ejecucion where solicitud_id=?",Integer.class,sid));verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void reservaRealRechazaSimulacionAunqueUnEventoEstuvieraEncolado()throws Exception{
        String sid=simulacionHistoricaVencida();jdbc.update("update se_solicitud_evidencia set regla_aplicada=24 where id=?",sid);var antes=jdbc.queryForMap("select * from se_solicitud_evidencia where id=?",sid);
        assertTrue(automatizar.reservarEnvioSmtp(sid,24).isEmpty());assertEquals(antes,jdbc.queryForMap("select * from se_solicitud_evidencia where id=?",sid));verify(google,never()).enviar(anyString(),anyString());
    }
    Map<String,Object> prueba(String clave){return Map.of("clave",clave,"confirmado",true,"destinatario","qa-gmail@dimarsa.cl");}
    @Test void pruebaGmailSoloAdministradores()throws Exception{
        mvc.perform(get("/api/admin/gmail/prueba")).andExpect(status().isUnauthorized());
        for(String rol:List.of("TECNICO","SOPORTE","USUARIO")){
            mvc.perform(get("/api/admin/gmail/prueba").with(user("qa").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/gmail/prueba").with(user("qa").roles(rol)).contentType(MediaType.APPLICATION_JSON).content(json(prueba(UUID.randomUUID().toString())))).andExpect(status().isForbidden());
        }verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void pruebaDeshabilitadaNoEnviaNiCreaRegistros()throws Exception{
        conectar();postJson("/api/admin/gmail/prueba",prueba(UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/gmail/prueba").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.habilitado").value(false)).andExpect(jsonPath("$.transporte").value("GMAIL_API"));
        assertEquals(0,jdbc.queryForObject("select count(*) from gm_prueba_correo",Integer.class));verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void pruebaDesconectadaNoEnvia()throws Exception{
        doReturn(true).when(config).envioHabilitado();postJson("/api/admin/gmail/prueba",prueba(UUID.randomUUID().toString())).andExpect(status().isBadRequest());verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void pruebaExigeConfirmacionYDestinoUnicoAutorizado()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();var b=new HashMap<>(prueba(UUID.randomUUID().toString()));b.put("confirmado",false);postJson("/api/admin/gmail/prueba",b).andExpect(status().isBadRequest());b.put("confirmado",true);
        for(String d:List.of("incorrecto","qa@otro.cl","otro@dimarsa.cl","qa@dimarsa.cl,otro@dimarsa.cl")){b.put("destinatario",d);postJson("/api/admin/gmail/prueba",b).andExpect(status().isBadRequest());}verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void pruebaAceptadaRegistraMimeAuditoriaYNoModificaProcedimientos()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();int procedimientos=jdbc.queryForObject("select count(*) from pr_procedimiento",Integer.class),ejecuciones=jdbc.queryForObject("select count(*) from se_ejecucion",Integer.class);
        String clave=UUID.randomUUID().toString();postJson("/api/admin/gmail/prueba",prueba(clave)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO")).andExpect(jsonPath("$.mensajeId").value("GMAIL-qa123")).andExpect(jsonPath("$.finalizada").isNotEmpty());
        postJson("/api/admin/gmail/prueba",prueba(clave)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(clave));postJson("/api/admin/gmail/prueba",prueba(UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(clave));
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);verify(google,times(1)).enviar(anyString(),captor.capture());var mime=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(Base64.getUrlDecoder().decode(captor.getValue())));assertEquals(1,mime.getRecipients(jakarta.mail.Message.RecipientType.TO).length);assertNull(mime.getRecipients(jakarta.mail.Message.RecipientType.CC));
        assertEquals(3,jdbc.queryForObject("select count(*) from gm_prueba_evento where prueba_id=?",Integer.class,clave));assertEquals(procedimientos,jdbc.queryForObject("select count(*) from pr_procedimiento",Integer.class));assertEquals(ejecuciones,jdbc.queryForObject("select count(*) from se_ejecucion",Integer.class));
    }
    @Test void pruebaInciertaNoReintentaConClaveNueva()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();when(google.enviar(anyString(),anyString())).thenThrow(new GmailException(GmailException.Tipo.ENVIO));String clave=UUID.randomUUID().toString();
        postJson("/api/admin/gmail/prueba",prueba(clave)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("INCIERTO")).andExpect(jsonPath("$.mensajeId").isEmpty());postJson("/api/admin/gmail/prueba",prueba(UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(clave));verify(google,times(1)).enviar(anyString(),anyString());
    }
    @Test void pruebaRechazadaRequiereNuevaConfirmacionConAntecedente()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();when(google.enviar(anyString(),anyString())).thenThrow(new GmailException(GmailException.Tipo.RECHAZO));String clave=UUID.randomUUID().toString();
        postJson("/api/admin/gmail/prueba",prueba(clave)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FALLIDO"));postJson("/api/admin/gmail/prueba",prueba(UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(clave));verify(google,times(1)).enviar(anyString(),anyString());
        doReturn("GMAIL-reintento-qa").when(google).enviar(anyString(),anyString());var nuevo=new HashMap<>(prueba(UUID.randomUUID().toString()));nuevo.put("anterior",clave);postJson("/api/admin/gmail/prueba",nuevo).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO"));verify(google,times(2)).enviar(anyString(),anyString());
    }
    @Test void pruebaEnviandoVencidaSeMuestraInciertaSinAlterarRegistro()throws Exception{
        conectar();
        String id=UUID.randomUUID().toString();jdbc.update("insert into gm_prueba_correo(id,version,actor_id,remitente,destinatario,estado,creada,intento) values(?,0,1,'qa-gmail@dimarsa.cl','qa-gmail@dimarsa.cl','ENVIANDO',?,?)",id,java.sql.Timestamp.from(reloj.instant().minusSeconds(120)),java.sql.Timestamp.from(reloj.instant().minusSeconds(120)));
        mvc.perform(get("/api/admin/gmail/prueba").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.ultima.estado").value("INCIERTO"));assertEquals("ENVIANDO",jdbc.queryForObject("select estado from gm_prueba_correo where id=?",String.class,id));verify(google,never()).enviar(anyString(),anyString());
    }
    @Test void pruebaConcurrenteSoloUnEnvio()throws Exception{
        conectar();doReturn(true).when(config).envioHabilitado();var entro=new java.util.concurrent.CountDownLatch(1);var salir=new java.util.concurrent.CountDownLatch(1);
        when(google.enviar(anyString(),anyString())).thenAnswer(i->{entro.countDown();assertTrue(salir.await(8,java.util.concurrent.TimeUnit.SECONDS));return "GMAIL-concurrente-qa";});
        String clave=UUID.randomUUID().toString();var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{var primero=pool.submit(()->{try{return texto(postJson("/api/admin/gmail/prueba",prueba(clave)).andExpect(status().isOk()));}catch(Exception ex){throw new RuntimeException(ex);}});assertTrue(entro.await(8,java.util.concurrent.TimeUnit.SECONDS));postJson("/api/admin/gmail/prueba",prueba(clave)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIANDO"));postJson("/api/admin/gmail/prueba",prueba(UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIANDO"));salir.countDown();assertTrue(primero.get(8,java.util.concurrent.TimeUnit.SECONDS).contains("ENVIADO"));verify(google,times(1)).enviar(anyString(),anyString());}finally{salir.countDown();pool.shutdownNow();}
    }
}
