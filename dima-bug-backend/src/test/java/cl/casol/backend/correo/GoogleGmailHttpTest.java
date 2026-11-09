package cl.casol.backend.correo;
import cl.casol.backend.correo.infrastructure.gmail.*;
import cl.casol.backend.correo.application.service.GmailException;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import java.time.*;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class GoogleGmailHttpTest {
    GoogleGmailHttp google;MockRestServiceServer oauth,api;
    @BeforeEach void preparar(){var a=RestClient.builder();var o=RestClient.builder();api=MockRestServiceServer.bindTo(a).build();oauth=MockRestServiceServer.bindTo(o).build();google=new GoogleGmailHttp(CifradoGmailTest.config(CifradoGmailTest.clave()),Clock.systemUTC(),a,o);}
    @AfterEach void verificar(){api.verify();oauth.verify();}
    @Test void solicitudSoloSendOfflineYProofKey(){String url=google.autorizacion("estado-qa","verificador-sintetico");assertTrue(url.startsWith(GoogleGmailHttp.AUTH));var q=org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().getQueryParams();assertEquals(GmailConfig.SCOPE,java.net.URLDecoder.decode(q.getFirst("scope"),java.nio.charset.StandardCharsets.UTF_8));assertEquals("offline",q.getFirst("access_type"));assertEquals("false",q.getFirst("include_granted_scopes"));assertEquals("S256",q.getFirst("code_challenge_method"));assertNotNull(q.getFirst("code_challenge"));assertFalse(url.contains("verificador-sintetico"));assertFalse(url.contains("secreto-qa"));assertFalse(url.contains("openid"));}
    @Test void intercambiaCodigoIncluyendoPkceSinUserinfo(){oauth.expect(requestTo(GoogleGmailHttp.TOKEN)).andExpect(method(HttpMethod.POST)).andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=verificador-qa"))).andRespond(withSuccess("{\"access_token\":\"acceso-qa\",\"refresh_token\":\"refresco-qa\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\""+GmailConfig.SCOPE+"\"}",MediaType.APPLICATION_JSON));var t=google.intercambiar("codigo-qa","estado-qa","verificador-qa");assertEquals("refresco-qa",t.refresco);assertEquals(Set.of(GmailConfig.SCOPE),t.scopes);assertFalse(t.toString().contains("acceso-qa"));}
    @Test void renuevaSinPedirOtrosPermisos(){oauth.expect(requestTo(GoogleGmailHttp.TOKEN)).andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token"))).andRespond(withSuccess("{\"access_token\":\"nuevo-qa\",\"token_type\":\"Bearer\",\"expires_in\":3600}",MediaType.APPLICATION_JSON));assertEquals("nuevo-qa",google.renovar("refresh-qa").acceso);}
    @Test void errorOauthNoFiltraRespuesta(){oauth.expect(requestTo(GoogleGmailHttp.TOKEN)).andRespond(withBadRequest().body("{\"error\":\"invalid_grant\",\"error_description\":\"SECRET_NO_SALIR\"}").contentType(MediaType.APPLICATION_JSON));var e=assertThrows(GmailException.class,()->google.renovar("refresh-qa"));assertEquals(GmailException.Tipo.AUTORIZACION,e.tipo);assertFalse(e.getMessage().contains("SECRET"));assertNull(e.getCause());}
    @Test void envioHaceUnSoloPostYDevuelveReferencia(){api.expect(requestTo(GoogleGmailHttp.SEND)).andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION,"Bearer acceso-qa")).andExpect(content().json("{\"raw\":\"YWJj\"}")).andRespond(withSuccess("{\"id\":\"abc123\"}",MediaType.APPLICATION_JSON));assertEquals("GMAIL-abc123",google.enviar("acceso-qa","YWJj"));}
    @Test void errorDeEnvioNoReintentaNiFiltraRespuesta(){api.expect(requestTo(GoogleGmailHttp.SEND)).andRespond(withServerError().body("TOKEN_NO_SALIR"));var e=assertThrows(GmailException.class,()->google.enviar("acceso-qa","YWJj"));assertEquals(GmailException.Tipo.ENVIO,e.tipo);assertFalse(e.getMessage().contains("TOKEN"));}
    @Test void revocaSinIncluirTokenEnLaUrl(){api.expect(requestTo(GoogleGmailHttp.REVOKE)).andExpect(method(HttpMethod.POST)).andExpect(content().string("token=refresh-qa")).andRespond(withSuccess());google.revocar("refresh-qa");}
    @Test void falloTemporalNoSeClasificaComoGrantRevocado(){oauth.expect(requestTo(GoogleGmailHttp.TOKEN)).andRespond(withBadRequest().body("{\"error\":\"temporarily_unavailable\"}").contentType(MediaType.APPLICATION_JSON));assertEquals(GmailException.Tipo.CONEXION,assertThrows(GmailException.class,()->google.renovar("refresh-qa")).tipo);}
    @Test void rechazoExplicitoSeDistingueDeUnResultadoIncierto(){api.expect(requestTo(GoogleGmailHttp.SEND)).andRespond(withStatus(HttpStatus.FORBIDDEN).body("RESPUESTA_NO_MOSTRAR"));var error=assertThrows(GmailException.class,()->google.enviar("acceso-qa","YWJj"));assertEquals(GmailException.Tipo.RECHAZO,error.tipo);assertFalse(error.getMessage().contains("RESPUESTA"));}
}
